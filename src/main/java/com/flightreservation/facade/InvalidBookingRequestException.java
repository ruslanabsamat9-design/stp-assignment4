package com.flightreservation.facade;

/** The request is malformed. Nothing has been committed anywhere. */
public class InvalidBookingRequestException extends RuntimeException {
    public InvalidBookingRequestException(String message) {
        super(message);
    }
}
