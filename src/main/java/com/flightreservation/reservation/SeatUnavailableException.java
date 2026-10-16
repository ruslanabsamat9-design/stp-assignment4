package com.flightreservation.reservation;

/** The seat exists, but somebody else already holds it. */
public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String message) {
        super(message);
    }
}
