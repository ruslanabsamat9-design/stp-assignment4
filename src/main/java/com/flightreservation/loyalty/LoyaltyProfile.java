package com.flightreservation.loyalty;

/** State holder of subsystem C. Enforces its own legal status transitions. */
public class LoyaltyProfile {
    public static final int POINTS_PER_BOOKING = 10;

    private String passengerName;
    private String mealPreference;
    private int points;
    private LoyaltyStatus status;

    public LoyaltyProfile() {
    }

    public LoyaltyProfile(String passengerName, String mealPreference, int points, LoyaltyStatus status) {
        this.passengerName = passengerName;
        this.mealPreference = mealPreference;
        this.points = points;
        this.status = status;
    }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }
    public String getMealPreference() { return mealPreference; }
    public void setMealPreference(String mealPreference) { this.mealPreference = mealPreference; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public LoyaltyStatus getStatus() { return status; }
    public void setStatus(LoyaltyStatus status) { this.status = status; }

    /** ACTIVE or UPDATED -> UPDATED (and earns points). A SUSPENDED profile cannot be changed. */
    public void updatePreference(String mealPreference) {
        if (status == LoyaltyStatus.SUSPENDED) {
            throw new IllegalStateException("A suspended loyalty profile cannot be updated");
        }
        this.mealPreference = mealPreference;
        this.points += POINTS_PER_BOOKING;
        this.status = LoyaltyStatus.UPDATED;
    }

    /** ACTIVE or UPDATED -> SUSPENDED. */
    public void suspend() {
        if (status == LoyaltyStatus.SUSPENDED) {
            throw new IllegalStateException("Loyalty profile is already suspended");
        }
        this.status = LoyaltyStatus.SUSPENDED;
    }

    /** SUSPENDED -> ACTIVE. */
    public void reinstate() {
        if (status != LoyaltyStatus.SUSPENDED) {
            throw new IllegalStateException("Only a suspended loyalty profile can be reinstated");
        }
        this.status = LoyaltyStatus.ACTIVE;
    }
}
