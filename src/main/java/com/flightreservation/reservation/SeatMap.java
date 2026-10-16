package com.flightreservation.reservation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure business logic of subsystem A: which flights and seats are valid.
 * Aircraft layout: rows 1-45, seats A-F. Flight numbers look like "FZ-101".
 */
public class SeatMap {
    public static final int LAST_ROW = 45;
    private static final Pattern FLIGHT = Pattern.compile("[A-Z]{2}-\\d{1,4}");
    private static final Pattern SEAT = Pattern.compile("(\\d{1,2})([A-F])");

    public String validFlight(String flightNumber) {
        String normalized = normalize(flightNumber);
        if (!FLIGHT.matcher(normalized).matches()) {
            throw new InvalidSeatSelectionException("Unknown flight number format: '" + flightNumber + "' (expected e.g. FZ-101)");
        }
        return normalized;
    }

    public String validSeat(String seatNumber) {
        String normalized = normalize(seatNumber);
        Matcher matcher = SEAT.matcher(normalized);
        if (!matcher.matches() || Integer.parseInt(matcher.group(1)) < 1 || Integer.parseInt(matcher.group(1)) > LAST_ROW) {
            throw new InvalidSeatSelectionException("Seat does not exist: '" + seatNumber + "' (rows 1-" + LAST_ROW + ", seats A-F)");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
