package com.flightreservation.ticket;

/** Pure business logic of subsystem B: decides whether a piece of baggage is allowed on a ticket. */
public class BaggageRulesEngine {
    public static final double FREE_ALLOWANCE_KG = 23.0;

    public void check(double baggageKg) {
        if (Double.isNaN(baggageKg) || baggageKg < 0) {
            throw new IllegalArgumentException("Baggage weight must be zero or positive");
        }
        if (baggageKg > FREE_ALLOWANCE_KG) {
            throw new BaggageLimitExceededException(
                    "Baggage " + baggageKg + " kg exceeds the " + FREE_ALLOWANCE_KG + " kg allowance");
        }
    }
}
