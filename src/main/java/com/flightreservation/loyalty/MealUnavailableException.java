package com.flightreservation.loyalty;

/** The requested meal is not offered (realistic step-C failure, after seat and ticket are committed). */
public class MealUnavailableException extends RuntimeException {
    public MealUnavailableException(String message) {
        super(message);
    }
}
