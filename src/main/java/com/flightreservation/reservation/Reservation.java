package com.flightreservation.reservation;

/** State holder of subsystem A. Enforces its own legal status transitions. */
public class Reservation {
    private String id;
    private String passengerName;
    private String flightNumber;
    private String seatNumber;
    private ReservationStatus status;

    public Reservation() {
    }

    public Reservation(String id, String passengerName, String flightNumber, String seatNumber, ReservationStatus status) {
        this.id = id;
        this.passengerName = passengerName;
        this.flightNumber = flightNumber;
        this.seatNumber = seatNumber;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }

    /** PENDING -> CONFIRMED only. */
    public void confirm() {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Only a PENDING reservation can be confirmed, but it is " + status);
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    /** PENDING or CONFIRMED -> CANCELLED. A cancelled reservation can never come back. */
    public void cancel() {
        if (status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Reservation is already cancelled");
        }
        this.status = ReservationStatus.CANCELLED;
    }
}
