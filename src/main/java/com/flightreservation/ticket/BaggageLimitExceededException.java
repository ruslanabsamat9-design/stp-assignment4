package com.flightreservation.ticket;

/** The e-ticket cannot be issued because the baggage breaks the rules (realistic step-B failure). */
public class BaggageLimitExceededException extends RuntimeException {
    public BaggageLimitExceededException(String message) {
        super(message);
    }
}
