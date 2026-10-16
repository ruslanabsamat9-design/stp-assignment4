package com.flightreservation;

import com.flightreservation.reservation.ReservationStatus;
import com.flightreservation.reservation.SeatReservationService;
import com.flightreservation.ticket.ETicket;
import com.flightreservation.ticket.ETicketStatus;
import com.flightreservation.ticket.TicketService;
import com.flightreservation.workflow.WorkflowGateway;
import com.flightreservation.workflow.WorkflowProgress;
import com.flightreservation.workflow.WorkflowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceRecoveryTest {

    @TempDir
    Path tempDir;
    FlightDesk desk;

    @BeforeEach
    void openDesk() {
        desk = FlightDesk.openIn(tempDir);
    }

    @Test
    void completedBookingsSurviveARestart() {
        var receipt = desk.passenger("Nursultan").onFlight("FZ-202").inSeat("14C").wantingMeal("Kosher").books();

        desk.restart();

        desk.assertSeat("FZ-202", "14C").is(ReservationStatus.CONFIRMED);
        desk.assertTicketsAre(ETicketStatus.ISSUED);
        desk.assertLastChainIs(WorkflowStatus.COMPLETED);
        assertEquals("Kosher", desk.admin().inspectProfiles().get(0).getMealPreference());
        assertEquals(receipt.workflowId(), desk.facade().latestWorkflow().orElseThrow().getWorkflowId());
        assertTrue(desk.facade().recoverInterruptedWorkflows().isEmpty(), "a finished chain needs no recovery");
    }

    @Test
    void chainInterruptedAfterA_isFoundOnDiskAndOnlyTheSeatIsUndone() {
        desk.passenger("Mid").onFlight("FZ-203").inSeat("3A").booksUntilTheProcessDiesAt(WorkflowStatus.PENDING_B);

        desk.restart();
        desk.assertSeat("FZ-203", "3A").is(ReservationStatus.CONFIRMED);   // the half-done state is really on disk
        desk.assertLastChainIs(WorkflowStatus.PENDING_B);

        List<WorkflowProgress> recovered = desk.facade().recoverInterruptedWorkflows();

        assertEquals(1, recovered.size());
        desk.assertSeat("FZ-203", "3A").is(ReservationStatus.CANCELLED);
        desk.assertNoTicketsWereIssued();
        desk.assertLastChainIs(WorkflowStatus.COMPENSATED);
        desk.assertCompensationRanInOrder("SEAT_CANCELLED");
    }

    @Test
    void chainInterruptedAfterB_isFoundOnDiskAndTicketThenSeatAreUndone() {
        desk.passenger("Mid").onFlight("FZ-204").inSeat("4B").booksUntilTheProcessDiesAt(WorkflowStatus.PENDING_C);

        desk.restart();
        desk.assertLastChainIs(WorkflowStatus.PENDING_C);
        desk.assertTicketsAre(ETicketStatus.ISSUED);
        desk.assertSeat("FZ-204", "4B").is(ReservationStatus.CONFIRMED);

        desk.facade().recoverInterruptedWorkflows();

        desk.assertTicketsAre(ETicketStatus.CANCELLED);
        desk.assertSeat("FZ-204", "4B").is(ReservationStatus.CANCELLED);
        desk.assertLastChainIs(WorkflowStatus.COMPENSATED);
        desk.assertCompensationRanInOrder("TICKET_CANCELLED", "SEAT_CANCELLED");
        desk.assertNoLoyaltyProfileExists();
    }

    @Test
    void recoveryRunsOnlyOnceAndIsIdempotent() {
        desk.passenger("Mid").onFlight("FZ-205").inSeat("5C").booksUntilTheProcessDiesAt(WorkflowStatus.PENDING_C);
        desk.restart();

        assertEquals(1, desk.facade().recoverInterruptedWorkflows().size());
        assertTrue(desk.facade().recoverInterruptedWorkflows().isEmpty());
        desk.assertCompensationRanInOrder("TICKET_CANCELLED", "SEAT_CANCELLED");
    }

    @Test
    void recoveryHandlesSeveralInterruptedChainsAndLeavesFinishedOnesAlone() {
        desk.passenger("Done").onFlight("FZ-206").inSeat("1A").books();
        desk.passenger("Crash1").onFlight("FZ-206").inSeat("2A").booksUntilTheProcessDiesAt(WorkflowStatus.PENDING_B);
        desk.passenger("Crash2").onFlight("FZ-206").inSeat("3A").booksUntilTheProcessDiesAt(WorkflowStatus.PENDING_C);
        desk.restart();

        assertEquals(2, desk.facade().recoverInterruptedWorkflows().size());

        desk.assertSeat("FZ-206", "1A").is(ReservationStatus.CONFIRMED);
        desk.assertSeat("FZ-206", "2A").is(ReservationStatus.CANCELLED);
        desk.assertSeat("FZ-206", "3A").is(ReservationStatus.CANCELLED);
        assertTrue(desk.admin().findStuckWorkflows().isEmpty());
    }

    @Test
    void aRollbackThatWasItselfInterruptedIsFinishedAfterRestart() {
        // Disk state: the ticket is already cancelled, the seat is not, the chain says COMPENSATING.
        SeatReservationService seats = new SeatReservationService(tempDir);
        TicketService tickets = new TicketService(tempDir);
        var reservation = seats.reserveSeat("Half", "FZ-207", "6D");
        ETicket ticket = tickets.issueTicket(reservation.getId(), "Half", 10);
        tickets.cancelTicket(ticket.getId());
        new WorkflowGateway(tempDir).save(new WorkflowProgress("wf-half", "Half", reservation.getId(),
                ticket.getId(), WorkflowStatus.COMPENSATING));

        desk.restart().facade().recoverInterruptedWorkflows();

        desk.assertSeat("FZ-207", "6D").is(ReservationStatus.CANCELLED);
        desk.assertTicketsAre(ETicketStatus.CANCELLED);
        desk.assertLastChainIs(WorkflowStatus.COMPENSATED);
        desk.assertCompensationRanInOrder("SEAT_CANCELLED");   // the ticket step was not repeated
    }

    @Test
    void aTicketIssuedJustBeforeTheCrashIsFoundByReservationEvenIfTheChainNeverRecordedItsId() {
        // Disk state: seat + ticket exist, but the chain still says PENDING_B with no ticket id.
        SeatReservationService seats = new SeatReservationService(tempDir);
        TicketService tickets = new TicketService(tempDir);
        var reservation = seats.reserveSeat("Orphan", "FZ-208", "8F");
        tickets.issueTicket(reservation.getId(), "Orphan", 5);
        new WorkflowGateway(tempDir).save(new WorkflowProgress("wf-orphan", "Orphan", reservation.getId(),
                null, WorkflowStatus.PENDING_B));

        desk.restart().facade().recoverInterruptedWorkflows();

        desk.assertTicketsAre(ETicketStatus.CANCELLED);
        desk.assertSeat("FZ-208", "8F").is(ReservationStatus.CANCELLED);
        desk.assertCompensationRanInOrder("TICKET_CANCELLED", "SEAT_CANCELLED");
    }
}
