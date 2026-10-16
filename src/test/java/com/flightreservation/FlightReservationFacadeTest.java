package com.flightreservation;

import com.flightreservation.facade.BookingFailedException;
import com.flightreservation.facade.BookingReceipt;
import com.flightreservation.facade.BookingRequest;
import com.flightreservation.facade.InvalidBookingRequestException;
import com.flightreservation.loyalty.LoyaltyProfile;
import com.flightreservation.loyalty.LoyaltyStatus;
import com.flightreservation.reservation.ReservationStatus;
import com.flightreservation.ticket.ETicketStatus;
import com.flightreservation.workflow.WorkflowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FlightReservationFacadeTest {

    @TempDir
    Path tempDir;
    FlightDesk desk;

    @BeforeEach
    void openDesk() {
        desk = FlightDesk.openIn(tempDir);
    }

    // ---- happy path -------------------------------------------------------------------------

    @Test
    void aValidBookingConfirmsSeatIssuesTicketAndAwardsPoints() {
        BookingReceipt receipt = desk.passenger("Amina").onFlight("FZ-101").inSeat("12A")
                .carrying(20).wantingMeal("Vegetarian").books();

        assertEquals(WorkflowStatus.COMPLETED, receipt.status());
        desk.assertSeat("FZ-101", "12A").is(ReservationStatus.CONFIRMED);
        desk.assertTicketsAre(ETicketStatus.ISSUED);
        desk.assertLastChainIs(WorkflowStatus.COMPLETED);

        LoyaltyProfile profile = desk.admin().inspectProfiles().get(0);
        assertEquals("Vegetarian", profile.getMealPreference());
        assertEquals(10, profile.getPoints());
        assertEquals(LoyaltyStatus.UPDATED, profile.getStatus());
        assertEquals(receipt.reservationId(), desk.admin().inspectTickets().get(0).getReservationId());
    }

    @Test
    void mealNamesAreMatchedIgnoringCaseAndStoredInCanonicalSpelling() {
        desk.passenger("Dana").wantingMeal("  vegan ").books();

        assertEquals("Vegan", desk.admin().inspectProfiles().get(0).getMealPreference());
    }

    @Test
    void theSamePassengerBookingTwiceKeepsOneProfileAndCollectsPoints() {
        desk.passenger("Aigerim").onFlight("FZ-101").inSeat("1A").books();
        desk.passenger("Aigerim").onFlight("FZ-102").inSeat("1A").books();

        assertEquals(1, desk.admin().inspectProfiles().size());
        assertEquals(20, desk.admin().inspectProfiles().get(0).getPoints());
    }

    @Test
    void theClientCanLookUpAChainByItsId() {
        BookingReceipt receipt = desk.passenger("Erlan").books();

        assertEquals(WorkflowStatus.COMPLETED, desk.facade().findWorkflow(receipt.workflowId()).orElseThrow().getStatus());
        assertTrue(desk.facade().findWorkflow("nope").isEmpty());
        assertEquals(receipt.workflowId(), desk.facade().latestWorkflow().orElseThrow().getWorkflowId());
    }

    // ---- compensation depth 1: B fails after A committed ----------------------------------------

    @Test
    void overweightBaggageFailsAtB_andOnlyTheSeatIsCompensated() {
        desk.passenger("Bora").onFlight("FZ-103").inSeat("22B").carrying(35).tryToBook();

        desk.assertBookingFailedAtStage("B");
        assertTrue(desk.failure().isCompensated());
        desk.assertSeat("FZ-103", "22B").is(ReservationStatus.CANCELLED);
        desk.assertNoTicketsWereIssued();
        desk.assertNoLoyaltyProfileExists();
        desk.assertLastChainIs(WorkflowStatus.COMPENSATED);
        desk.assertCompensationRanInOrder("SEAT_CANCELLED");
        assertTrue(desk.admin().latestWorkflow().orElseThrow().getFailureReason().contains("exceeds"));
    }

    // ---- compensation depth 2: C fails after A and B committed ------------------------------------

    @Test
    void unavailableMealFailsAtC_andTicketThenSeatAreCompensatedInThatOrder() {
        desk.passenger("Selma").onFlight("FZ-104").inSeat("30A").carrying(15).wantingMeal("Pizza").tryToBook();

        desk.assertBookingFailedAtStage("C");
        assertTrue(desk.failure().isCompensated());
        desk.assertSeat("FZ-104", "30A").is(ReservationStatus.CANCELLED);
        desk.assertTicketsAre(ETicketStatus.CANCELLED);
        desk.assertNoLoyaltyProfileExists();
        desk.assertLastChainIs(WorkflowStatus.COMPENSATED);
        desk.assertCompensationRanInOrder("TICKET_CANCELLED", "SEAT_CANCELLED");
    }

    @Test
    void aSeatFreedByCompensationCanBeBookedByNextPassenger() {
        desk.passenger("First").onFlight("FZ-105").inSeat("7C").carrying(40).tryToBook();
        desk.assertSeat("FZ-105", "7C").is(ReservationStatus.CANCELLED);

        desk.passenger("Second").onFlight("FZ-105").inSeat("7C").carrying(10).books();

        desk.assertSeat("FZ-105", "7C").is(ReservationStatus.CONFIRMED);
    }

    // ---- other realistic failures: nothing committed yet -------------------------------------------

    @Test
    void aSeatThatIsAlreadyTakenFailsAtA_andNothingNewIsWritten() {
        desk.passenger("Holder").onFlight("FZ-110").inSeat("9D").books();
        int reservationsBefore = desk.admin().inspectReservations().size();
        int chainsBefore = desk.admin().inspectWorkflows().size();

        desk.passenger("Latecomer").onFlight("FZ-110").inSeat("9D").tryToBook();

        desk.assertBookingFailedAtStage("A");
        assertFalse(desk.failure().isCompensated());
        assertNull(desk.failure().getWorkflowId());
        assertEquals(reservationsBefore, desk.admin().inspectReservations().size());
        assertEquals(chainsBefore, desk.admin().inspectWorkflows().size());
        assertEquals(1, desk.admin().inspectTickets().size());
    }

    @Test
    void aSeatThatDoesNotExistFailsAtA() {
        desk.passenger("Lost").onFlight("FZ-111").inSeat("99Z").tryToBook();

        desk.assertBookingFailedAtStage("A");
        desk.assertSeat("FZ-111", "99Z").neverExisted();
        desk.assertNoChainWasStarted();
    }

    @Test
    void aMalformedFlightNumberFailsAtA() {
        desk.passenger("Lost").onFlight("banana").tryToBook();

        desk.assertBookingFailedAtStage("A");
        desk.assertNoChainWasStarted();
    }

    @Test
    void invalidRequestsAreRejectedBeforeAnySubsystemIsTouched() {
        assertThrows(InvalidBookingRequestException.class,
                () -> desk.facade().book(new BookingRequest(" ", "FZ-1", "1A", 10, "Standard")));
        assertThrows(InvalidBookingRequestException.class,
                () -> desk.facade().book(new BookingRequest("Ann", "", "1A", 10, "Standard")));
        assertThrows(InvalidBookingRequestException.class,
                () -> desk.facade().book(new BookingRequest("Ann", "FZ-1", null, 10, "Standard")));
        assertThrows(InvalidBookingRequestException.class,
                () -> desk.facade().book(new BookingRequest("Ann", "FZ-1", "1A", -5, "Standard")));
        assertThrows(InvalidBookingRequestException.class,
                () -> desk.facade().book(new BookingRequest("Ann", "FZ-1", "1A", Double.NaN, "Standard")));
        assertThrows(InvalidBookingRequestException.class,
                () -> desk.facade().book(new BookingRequest("Ann", "FZ-1", "1A", 10, " ")));

        assertTrue(desk.admin().inspectReservations().isEmpty());
        desk.assertNoChainWasStarted();
    }

    @Test
    void theFailureExceptionKeepsTheOriginalCauseForTheClient() {
        desk.passenger("Cause").carrying(50).tryToBook();

        BookingFailedException failure = desk.failure();
        assertNotNull(failure.getCause());
        assertNotNull(failure.getWorkflowId());
        assertTrue(failure.getMessage().contains("step B"));
    }
}
