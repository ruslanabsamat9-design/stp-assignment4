package com.flightreservation.workflow;

import com.flightreservation.storage.JsonFileStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Persistence gateway for the chain log (workflow.json). Owned by the facades, not by A, B or C. */
public class WorkflowGateway {
    private final JsonFileStore<WorkflowProgress> store;

    public WorkflowGateway(Path dataDirectory) {
        this.store = new JsonFileStore<>(dataDirectory.resolve("workflow.json"), WorkflowProgress.class);
    }

    /** Insert, or replace the existing record with the same id in place (order is preserved). */
    public WorkflowProgress save(WorkflowProgress progress) {
        List<WorkflowProgress> all = store.loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getWorkflowId().equals(progress.getWorkflowId())) {
                all.set(i, progress);
                store.saveAll(all);
                return progress;
            }
        }
        all.add(progress);
        store.saveAll(all);
        return progress;
    }

    public List<WorkflowProgress> loadAll() {
        return store.loadAll();
    }

    public Optional<WorkflowProgress> loadLatest() {
        List<WorkflowProgress> all = store.loadAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
    }

    public Optional<WorkflowProgress> findById(String workflowId) {
        return store.loadAll().stream().filter(w -> w.getWorkflowId().equals(workflowId)).findFirst();
    }

    /** Every chain that is neither COMPLETED nor COMPENSATED, i.e. everything that needs attention after a restart. */
    public List<WorkflowProgress> findUnfinished() {
        return store.loadAll().stream().filter(w -> !w.getStatus().isTerminal()).toList();
    }

    public void deleteAll() {
        store.clear();
    }
}
