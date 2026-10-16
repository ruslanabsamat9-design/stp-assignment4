package com.flightreservation.facade;

/**
 * Stands in for the JVM being killed. It extends Error on purpose: the facade only catches
 * RuntimeException when it compensates, so a "crash" is NOT compensated on the spot,
 * exactly like a real power cut would not be.
 */
public class SimulatedCrash extends Error {
    public SimulatedCrash(String message) {
        super(message);
    }
}
