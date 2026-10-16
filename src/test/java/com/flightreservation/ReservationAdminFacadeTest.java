package com.flightreservation;

import com.flightreservation.reservation.ReservationStatus;
import com.flightreservation.ticket.ETicketStatus;
import com.flightreservation.workflow.WorkflowNotFoundException;
import com.flightreservation.workflow.WorkflowProgress;
import com.flightreservation.workflow.WorkflowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationAdminFacadeTest {

    @TempDir
    Path tempDir;
    FlightDesk desk;

    @BeforeEach
    void openDesk() {
        desk = FlightDesk.openIn(tempDir);
    }

    @Test
    void emptyStoreInspectsAsEmpty() {
        assertTrue(desk.admin().inspectWorkflows().isEmpty());
        assertTrue(desk.admin().latestWorkflow().isEmpty());
        assertTrue(desk.admin().inspectReservations().isEmpty());
        assertTrue(desk.admin().inspectTickets().isEmpty());
        assertTrue(desk.admin().inspectProfiles().isEmpty());
        assertTrue(desk.admin().findStuckWorkflows().isEmpty());
        assertTrue(desk.admin().compensateAllStuck().isEmpty());
    }

    @Test
    void adminSeesWhatTheClientFacadeBooked() {
        desk.passenger("Reader").onFlight("FZ-220").inSeat("44B").books();

        assertEquals(1, desk.admin().inspectReservations().size());
        assertEquals(1, desk.admin().inspectTickets().size());
        assertEquals(1, desk.admin().inspectProfiles().size());
        assertEquals(WorkflowStatus.COMPLETED, desk.admin().inspectWorkflows().get(0).getStatus());
    }

    @Test
    void adminCanManuallyCompensateAChainThatWasLeftHalfDone() {
        desk.passenger("Stuck").onFlight("FZ-221").inSeat("2A").booksUntilTheProcessDiesAt(WorkflowStatus.PENDING_C);
        List<WorkflowProgress> stuck = desk.admin().findStuckWorkflows();
        assertEquals(1, stuck.size());

        WorkflowProgress result = desk.admin().compensateWorkflow(stuck.get(0).getWorkflowId());

        assertEquals(WorkflowStatus.COMPENSATED, result.getStatus());
        desk.assertTicketsAre(ETicketStatus.CANCELLED);
        desk.assertSeat("FZ-221", "2A").is(ReservationStatus.CANCELLED);
        desk.assertCompensationRanInOrder("TICKET_CANCELLED", "SEAT_CANCELLED");
    }

    @Test
    void compensateAllStuckHandlesEveryUnfinishedChain() {
        desk.passenger("S1").onFlight("FZ-222").inSeat("1A").booksUntilTheProcessDiesAt(WorkflowStatus.PENDING_B);
        desk.passenger("S2").onFlight("FZ-222").inSeat("2A").booksUntilTheProcessDiesAt(WorkflowStatus.PENDING_C);

        List<WorkflowProgress> handled = desk.admin().compensateAllStuck();

        assertEquals(2, handled.size());
        assertTrue(desk.admin().findStuckWorkflows().isEmpty());
    }

    @Test
    void completedOrAlreadyCompensatedChainsCannotBeCompensatedAgain() {
        var receipt = desk.passenger("Fine").books();
        IllegalStateException completed = assertThrows(IllegalStateException.class,
                () -> desk.admin().compensateWorkflow(receipt.workflowId()));
        assertTrue(completed.getMessage().contains("COMPLETED"));

        desk.passenger("Failed").onFlight("FZ-223").inSeat("3C").carrying(99).tryToBook();
        String failedId = desk.failure().getWorkflowId();
        assertThrows(IllegalStateException.class, () -> desk.admin().compensateWorkflow(failedId));
    }

    @Test
    void compensatingAnUnknownChainIsReported() {
        assertThrows(WorkflowNotFoundException.class, () -> desk.admin().compensateWorkflow("does-not-exist"));
    }

    @Test
    void resetStoreWipesEverythingAndTheAppKeepsWorking() {
        desk.passenger("Wipe").books();

        desk.admin().resetStore();

        assertTrue(desk.admin().inspectReservations().isEmpty());
        assertTrue(desk.admin().inspectTickets().isEmpty());
        assertTrue(desk.admin().inspectProfiles().isEmpty());
        assertTrue(desk.admin().inspectWorkflows().isEmpty());
        desk.passenger("Again").books();   // same seat as before is free again
        desk.assertSeat("FZ-101", "12A").is(ReservationStatus.CONFIRMED);
    }
}
