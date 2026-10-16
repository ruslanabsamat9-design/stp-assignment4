package com.flightreservation;

import com.flightreservation.reservation.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Subsystem A used completely on its own: no facade, no other subsystem. */
class SubsystemATest {

    @TempDir
    Path tempDir;

    @Test
    void reservingASeatPersistsItAsConfirmed() {
        SeatReservationService service = new SeatReservationService(tempDir);

        Reservation reservation = service.reserveSeat("Amina", "fz-101", " 12a ");

        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertEquals("FZ-101", reservation.getFlightNumber());
        assertEquals("12A", reservation.getSeatNumber());
        assertEquals(1, new SeatReservationService(tempDir).getReservations().size());   // read back from disk
    }

    @Test
    void aTakenSeatCannotBeReservedAgainUntilItIsCancelled() {
        SeatReservationService service = new SeatReservationService(tempDir);
        Reservation first = service.reserveSeat("First", "FZ-110", "9D");

        assertFalse(service.isSeatAvailable("FZ-110", "9D"));
        assertThrows(SeatUnavailableException.class, () -> service.reserveSeat("Second", "FZ-110", "9D"));

        service.cancelReservation(first.getId());

        assertTrue(service.isSeatAvailable("FZ-110", "9D"));
        assertEquals(ReservationStatus.CONFIRMED, service.reserveSeat("Second", "FZ-110", "9D").getStatus());
    }

    @Test
    void theSameSeatOnAnotherFlightIsIndependent() {
        SeatReservationService service = new SeatReservationService(tempDir);
        service.reserveSeat("A", "FZ-110", "9D");

        assertTrue(service.isSeatAvailable("FZ-111", "9D"));
    }

    @Test
    void lookupsAndCancellationOfUnknownReservationsFail() {
        SeatReservationService service = new SeatReservationService(tempDir);

        assertThrows(ReservationNotFoundException.class, () -> service.findById("missing"));
        assertThrows(ReservationNotFoundException.class, () -> service.cancelReservation("missing"));
    }

    @Test
    void clearAllEmptiesTheStore() {
        SeatReservationService service = new SeatReservationService(tempDir);
        service.reserveSeat("A", "FZ-110", "1A");

        service.clearAll();

        assertTrue(service.getReservations().isEmpty());
    }

    @Test
    void seatMapAcceptsOnlyRealSeatsAndFlights() {
        SeatMap map = new SeatMap();

        assertEquals("1A", map.validSeat("1a"));
        assertEquals("45F", map.validSeat("45F"));
        for (String bad : new String[]{"0A", "46A", "12G", "A12", "", " ", "12", null}) {
            assertThrows(InvalidSeatSelectionException.class, () -> map.validSeat(bad), "seat: " + bad);
        }
        assertEquals("FZ-1", map.validFlight("fz-1"));
        for (String bad : new String[]{"FZ101", "F-101", "FZ-", "", null, "FZ-12345"}) {
            assertThrows(InvalidSeatSelectionException.class, () -> map.validFlight(bad), "flight: " + bad);
        }
    }

    @Test
    void reservationEnforcesItsOwnLegalTransitions() {
        Reservation reservation = new Reservation("r1", "Sam", "FZ-1", "1A", ReservationStatus.PENDING);

        reservation.confirm();
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertThrows(IllegalStateException.class, reservation::confirm);   // CONFIRMED -> CONFIRMED is illegal

        reservation.cancel();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertThrows(IllegalStateException.class, reservation::cancel);
        assertThrows(IllegalStateException.class, reservation::confirm);   // cancelled can never come back
    }

    @Test
    void aPendingReservationCanBeCancelledDirectly() {
        Reservation reservation = new Reservation();
        reservation.setId("r2");
        reservation.setPassengerName("Sam");
        reservation.setFlightNumber("FZ-1");
        reservation.setSeatNumber("2B");
        reservation.setStatus(ReservationStatus.PENDING);

        reservation.cancel();

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals("r2", reservation.getId());
        assertEquals("Sam", reservation.getPassengerName());
    }
}
