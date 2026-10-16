package com.flightreservation.facade;

/**
 * A step of the chain failed.
 * stage = "A", "B" or "C"; compensated tells whether earlier committed steps were rolled back
 * (false for stage A, where nothing had been committed yet).
 */
public class BookingFailedException extends RuntimeException {
    private final String stage;
    private final String workflowId;
    private final boolean compensated;

    public BookingFailedException(String stage, String workflowId, boolean compensated, Throwable cause) {
        super("Booking failed at step " + stage + ": " + cause.getMessage(), cause);
        this.stage = stage;
        this.workflowId = workflowId;
        this.compensated = compensated;
    }

    public String getStage() { return stage; }
    public String getWorkflowId() { return workflowId; }
    public boolean isCompensated() { return compensated; }
}
