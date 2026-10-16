package com.flightreservation.facade;

/** Everything a client has to hand over to book a flight. */
public record BookingRequest(String passengerName, String flightNumber, String seatNumber,
                             double baggageKg, String mealPreference) {

    /** Cheap input checks that need no subsystem; runs before anything is committed. */
    void validate() {
        if (passengerName == null || passengerName.isBlank()) {
            throw new InvalidBookingRequestException("Passenger name must not be empty");
        }
        if (flightNumber == null || flightNumber.isBlank()) {
            throw new InvalidBookingRequestException("Flight number must not be empty");
        }
        if (seatNumber == null || seatNumber.isBlank()) {
            throw new InvalidBookingRequestException("Seat number must not be empty");
        }
        if (Double.isNaN(baggageKg) || baggageKg < 0) {
            throw new InvalidBookingRequestException("Baggage weight must be zero or positive");
        }
        if (mealPreference == null || mealPreference.isBlank()) {
            throw new InvalidBookingRequestException("Meal preference must not be empty");
        }
    }
}
