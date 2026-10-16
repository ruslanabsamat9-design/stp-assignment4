package com.flightreservation.reservation;

/** The flight number or seat does not exist on the seat map (invalid input at the very first step). */
public class InvalidSeatSelectionException extends RuntimeException {
    public InvalidSeatSelectionException(String message) {
        super(message);
    }
}
