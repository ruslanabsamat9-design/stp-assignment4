package com.flightreservation;

import com.flightreservation.facade.BookingFailedException;
import com.flightreservation.facade.BookingReceipt;
import com.flightreservation.facade.BookingRequest;
import com.flightreservation.facade.FlightReservationFacade;
import com.flightreservation.facade.ReservationAdminFacade;
import com.flightreservation.facade.SimulatedCrash;
import com.flightreservation.reservation.Reservation;
import com.flightreservation.reservation.ReservationStatus;
import com.flightreservation.ticket.ETicket;
import com.flightreservation.ticket.ETicketStatus;
import com.flightreservation.workflow.WorkflowProgress;
import com.flightreservation.workflow.WorkflowStatus;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Domain-specific testing language: a "check-in desk" where passengers try to book flights.
 * Tests read like sentences:
 *
 *   desk.passenger("Bora").onFlight("FZ-103").inSeat("22B").carrying(35).tryToBook();
 *   desk.assertBookingFailedAtStage("B");
 *   desk.assertSeat("FZ-103", "22B").is(ReservationStatus.CANCELLED);
 *
 * Every desk works on its own temp folder, so tests never share state.
 */
final class FlightDesk {
    private final Path dataDir;
    private FlightReservationFacade facade;
    private ReservationAdminFacade admin;
    private BookingReceipt lastReceipt;
    private BookingFailedException lastFailure;

    private FlightDesk(Path dataDir) {
        this.dataDir = dataDir;
        reopen();
    }

    static FlightDesk openIn(Path dataDir) {
        return new FlightDesk(dataDir);
    }

    /** Simulates stopping and starting the application: brand-new objects over the same files. */
    FlightDesk restart() {
        reopen();
        return this;
    }

    private void reopen() {
        this.facade = new FlightReservationFacade(dataDir);
        this.admin = new ReservationAdminFacade(dataDir);
    }

    Passenger passenger(String name) {
        return new Passenger(name);
    }

    FlightReservationFacade facade() { return facade; }
    ReservationAdminFacade admin() { return admin; }
    BookingReceipt receipt() { return lastReceipt; }
    BookingFailedException failure() { return lastFailure; }

    /** One passenger building a booking, step by step. */
    final class Passenger {
        private final String name;
        private String flight = "FZ-101";
        private String seat = "12A";
        private double baggageKg = 15;
        private String meal = "Standard";

        private Passenger(String name) { this.name = name; }

        Passenger onFlight(String flight) { this.flight = flight; return this; }
        Passenger inSeat(String seat) { this.seat = seat; return this; }
        Passenger carrying(double kg) { this.baggageKg = kg; return this; }
        Passenger wantingMeal(String meal) { this.meal = meal; return this; }

        private BookingRequest request() {
            return new BookingRequest(name, flight, seat, baggageKg, meal);
        }

        /** Books and expects success. */
        BookingReceipt books() {
            lastFailure = null;
            lastReceipt = facade.book(request());
            return lastReceipt;
        }

        /** Books and remembers the failure instead of throwing, so the test can assert on the end state. */
        void tryToBook() {
            lastReceipt = null;
            try {
                lastReceipt = facade.book(request());
                lastFailure = null;
            } catch (BookingFailedException e) {
                lastFailure = e;
            }
        }

        /** Books, but the process "dies" as soon as the chain reaches the given status. */
        void booksUntilTheProcessDiesAt(WorkflowStatus crashStatus) {
            FlightReservationFacade doomed = new FlightReservationFacade(dataDir, progress -> {
                if (progress.getStatus() == crashStatus) {
                    throw new SimulatedCrash("killed at " + crashStatus);
                }
            });
            try {
                doomed.book(request());
                throw new AssertionError("expected the process to die at " + crashStatus);
            } catch (SimulatedCrash expected) {
                // the "process" is gone; only the files on disk are left
            }
        }
    }

    // ---- assertions in domain words -------------------------------------------------------

    SeatAssertion assertSeat(String flight, String seat) {
        return new SeatAssertion(flight, seat);
    }

    final class SeatAssertion {
        private final String flight;
        private final String seat;

        private SeatAssertion(String flight, String seat) {
            this.flight = flight;
            this.seat = seat;
        }

        void is(ReservationStatus expected) {
            List<Reservation> matches = admin.inspectReservations().stream()
                    .filter(r -> r.getFlightNumber().equals(flight) && r.getSeatNumber().equals(seat))
                    .toList();
            assertFalse(matches.isEmpty(), "no reservation for " + flight + " " + seat);
            assertEquals(expected, matches.get(matches.size() - 1).getStatus());
        }

        void neverExisted() {
            assertTrue(admin.inspectReservations().stream()
                    .noneMatch(r -> r.getFlightNumber().equals(flight) && r.getSeatNumber().equals(seat)));
        }
    }

    void assertNoTicketsWereIssued() {
        assertTrue(admin.inspectTickets().isEmpty(), "tickets should be empty but were " + admin.inspectTickets().size());
    }

    void assertTicketsAre(ETicketStatus... expected) {
        List<ETicketStatus> actual = admin.inspectTickets().stream().map(ETicket::getStatus).toList();
        assertEquals(Arrays.asList(expected), actual);
    }

    void assertNoLoyaltyProfileExists() {
        assertTrue(admin.inspectProfiles().isEmpty());
    }

    void assertLastChainIs(WorkflowStatus expected) {
        WorkflowProgress latest = admin.latestWorkflow().orElseThrow(() -> new AssertionError("no chain on disk"));
        assertEquals(expected, latest.getStatus());
    }

    void assertNoChainWasStarted() {
        assertTrue(admin.inspectWorkflows().isEmpty());
        assertNull(lastReceipt);
    }

    void assertBookingFailedAtStage(String stage) {
        assertNotNull(lastFailure, "booking was expected to fail");
        assertEquals(stage, lastFailure.getStage());
    }

    /** The compensation steps must have run in exactly this order, e.g. "TICKET_CANCELLED", "SEAT_CANCELLED". */
    void assertCompensationRanInOrder(String... stepPrefixes) {
        List<String> history = admin.latestWorkflow().orElseThrow().getHistory();
        assertEquals(stepPrefixes.length, history.size(), "history was " + history);
        for (int i = 0; i < stepPrefixes.length; i++) {
            assertTrue(history.get(i).startsWith(stepPrefixes[i]), "step " + i + " was " + history.get(i));
        }
    }
}
