package com.flightreservation;

import com.flightreservation.storage.JsonFileStore;
import com.flightreservation.storage.StorageException;
import com.flightreservation.workflow.WorkflowGateway;
import com.flightreservation.workflow.WorkflowProgress;
import com.flightreservation.workflow.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageAndWorkflowLogTest {

    @TempDir
    Path tempDir;

    @Test
    void aMissingOrEmptyFileReadsAsEmptyAndWritesCreateTheFolder() throws IOException {
        Path file = tempDir.resolve("nested").resolve("workflow.json");
        JsonFileStore<WorkflowProgress> store = new JsonFileStore<>(file, WorkflowProgress.class);

        assertTrue(store.loadAll().isEmpty());

        store.saveAll(List.of(new WorkflowProgress("w1", "Sam", "r1", null, WorkflowStatus.PENDING_B)));
        assertTrue(Files.exists(file));
        assertEquals(1, store.loadAll().size());

        Files.writeString(file, "");
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    void corruptJsonIsReportedAsAStorageException() throws IOException {
        Path file = tempDir.resolve("broken.json");
        Files.writeString(file, "{ not json");
        JsonFileStore<WorkflowProgress> store = new JsonFileStore<>(file, WorkflowProgress.class);

        assertThrows(StorageException.class, store::loadAll);
    }

    @Test
    void clearLeavesAValidEmptyArrayOnDisk() throws IOException {
        Path file = tempDir.resolve("workflow.json");
        JsonFileStore<WorkflowProgress> store = new JsonFileStore<>(file, WorkflowProgress.class);
        store.saveAll(List.of(new WorkflowProgress("w1", "Sam", "r1", null, WorkflowStatus.PENDING_B)));

        store.clear();

        assertTrue(store.loadAll().isEmpty());
        assertEquals("[ ]", Files.readString(file).trim());
    }

    @Test
    void savingAnExistingChainReplacesItInPlaceAndKeepsTheOrder() {
        WorkflowGateway gateway = new WorkflowGateway(tempDir);
        gateway.save(new WorkflowProgress("w1", "A", "r1", null, WorkflowStatus.PENDING_B));
        gateway.save(new WorkflowProgress("w2", "B", "r2", null, WorkflowStatus.PENDING_B));

        WorkflowProgress updated = new WorkflowProgress("w1", "A", "r1", "t1", WorkflowStatus.COMPLETED);
        updated.log("note");
        gateway.save(updated);

        assertEquals(2, gateway.loadAll().size());
        assertEquals("w2", gateway.loadLatest().orElseThrow().getWorkflowId());   // w1 did not jump to the end
        WorkflowProgress reloaded = new WorkflowGateway(tempDir).findById("w1").orElseThrow();
        assertEquals("t1", reloaded.getTicketId());
        assertEquals(List.of("note"), reloaded.getHistory());
    }

    @Test
    void unfinishedChainsAreExactlyTheOnesThatAreNotCompletedOrCompensated() {
        WorkflowGateway gateway = new WorkflowGateway(tempDir);
        assertTrue(gateway.loadLatest().isEmpty());
        gateway.save(new WorkflowProgress("w1", "A", "r1", null, WorkflowStatus.PENDING_B));
        gateway.save(new WorkflowProgress("w2", "B", "r2", "t2", WorkflowStatus.PENDING_C));
        gateway.save(new WorkflowProgress("w3", "C", "r3", "t3", WorkflowStatus.COMPENSATING));
        gateway.save(new WorkflowProgress("w4", "D", "r4", "t4", WorkflowStatus.COMPLETED));
        gateway.save(new WorkflowProgress("w5", "E", "r5", null, WorkflowStatus.COMPENSATED));

        List<String> unfinished = gateway.findUnfinished().stream().map(WorkflowProgress::getWorkflowId).toList();

        assertEquals(List.of("w1", "w2", "w3"), unfinished);
        assertTrue(gateway.findById("nope").isEmpty());
    }

    @Test
    void deleteAllEmptiesTheChainLog() {
        WorkflowGateway gateway = new WorkflowGateway(tempDir);
        gateway.save(new WorkflowProgress("w1", "A", "r1", null, WorkflowStatus.PENDING_B));

        gateway.deleteAll();

        assertTrue(gateway.loadAll().isEmpty());
    }

    @Test
    void workflowBeanPropertiesRoundTrip() {
        WorkflowProgress progress = new WorkflowProgress();
        progress.setWorkflowId("w");
        progress.setPassengerName("Sam");
        progress.setReservationId("r");
        progress.setTicketId("t");
        progress.setStatus(WorkflowStatus.PENDING_C);
        progress.setFailureReason("boom");
        progress.setHistory(null);

        assertEquals("w", progress.getWorkflowId());
        assertEquals("Sam", progress.getPassengerName());
        assertEquals("r", progress.getReservationId());
        assertEquals("t", progress.getTicketId());
        assertEquals(WorkflowStatus.PENDING_C, progress.getStatus());
        assertEquals("boom", progress.getFailureReason());
        assertTrue(progress.getHistory().isEmpty());
    }
}
