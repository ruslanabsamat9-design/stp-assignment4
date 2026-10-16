package com.flightreservation.workflow;

/** Progress of one booking chain, persisted after every step. */
public enum WorkflowStatus {
    /** A (seat) is committed, B (ticket) still has to happen. */
    PENDING_B,
    /** A and B are committed, C (loyalty / meal) still has to happen. */
    PENDING_C,
    COMPLETED,
    /** Rollback started but not finished yet (e.g. the application died in the middle). */
    COMPENSATING,
    COMPENSATED;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPENSATED;
    }
}
