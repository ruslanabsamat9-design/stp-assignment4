package com.flightreservation.loyalty;

public class LoyaltyProfileNotFoundException extends RuntimeException {
    public LoyaltyProfileNotFoundException(String passengerName) {
        super("No loyalty profile for passenger: " + passengerName);
    }
}
