package com.flightreservation.facade;

import com.flightreservation.workflow.WorkflowProgress;

/**
 * Called right after each checkpoint of the chain has been written to disk.
 * The default does nothing; the demo app and the tests use it to "kill the process"
 * at an exact point of the chain and then prove that recovery works from disk alone.
 */
@FunctionalInterface
public interface ChainObserver {
    ChainObserver NONE = progress -> { };

    void afterCheckpoint(WorkflowProgress progress);
}
