package com.flightreservation.reservation;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Subsystem A entry point (usable on its own, without any facade). */
public class SeatReservationService {
    private final ReservationRepository repository;
    private final SeatMap seatMap;

    public SeatReservationService(Path dataDirectory) {
        this(new ReservationRepository(dataDirectory), new SeatMap());
    }

    public SeatReservationService(ReservationRepository repository, SeatMap seatMap) {
        this.repository = repository;
        this.seatMap = seatMap;
    }

    public Reservation reserveSeat(String passengerName, String flightNumber, String seatNumber) {
        String flight = seatMap.validFlight(flightNumber);
        String seat = seatMap.validSeat(seatNumber);

        if (!isSeatAvailable(flight, seat)) {
            throw new SeatUnavailableException("Seat is already reserved: " + flight + " " + seat);
        }

        Reservation reservation = new Reservation(UUID.randomUUID().toString(), passengerName, flight, seat,
                ReservationStatus.PENDING);
        reservation.confirm();
        return repository.save(reservation);
    }

    public boolean isSeatAvailable(String flightNumber, String seatNumber) {
        String flight = seatMap.validFlight(flightNumber);
        String seat = seatMap.validSeat(seatNumber);
        return repository.findAll().stream().noneMatch(r ->
                r.getFlightNumber().equals(flight)
                        && r.getSeatNumber().equals(seat)
                        && r.getStatus() != ReservationStatus.CANCELLED);
    }

    public Reservation cancelReservation(String reservationId) {
        Reservation reservation = findById(reservationId);
        reservation.cancel();
        return repository.save(reservation);
    }

    public Reservation findById(String reservationId) {
        return repository.findById(reservationId).orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    public List<Reservation> getReservations() {
        return repository.findAll();
    }

    public void clearAll() {
        repository.deleteAll();
    }
}
