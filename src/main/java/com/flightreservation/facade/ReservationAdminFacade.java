package com.flightreservation.facade;

import com.flightreservation.loyalty.LoyaltyProfile;
import com.flightreservation.loyalty.LoyaltyService;
import com.flightreservation.reservation.Reservation;
import com.flightreservation.reservation.SeatReservationService;
import com.flightreservation.ticket.ETicket;
import com.flightreservation.ticket.TicketService;
import com.flightreservation.workflow.WorkflowGateway;
import com.flightreservation.workflow.WorkflowNotFoundException;
import com.flightreservation.workflow.WorkflowProgress;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Narrow facade for operators: look at what is persisted, compensate a stuck chain by hand,
 * or wipe the store for a fresh demo run.
 */
public class ReservationAdminFacade {
    private final SeatReservationService seats;
    private final TicketService tickets;
    private final LoyaltyService loyalty;
    private final WorkflowGateway workflows;
    private final WorkflowCompensator compensator;

    public ReservationAdminFacade(Path dataDirectory) {
        this.seats = new SeatReservationService(dataDirectory);
        this.tickets = new TicketService(dataDirectory);
        this.loyalty = new LoyaltyService(dataDirectory);
        this.workflows = new WorkflowGateway(dataDirectory);
        this.compensator = new WorkflowCompensator(seats, tickets, workflows);
    }

    public List<WorkflowProgress> inspectWorkflows() {
        return workflows.loadAll();
    }

    public Optional<WorkflowProgress> latestWorkflow() {
        return workflows.loadLatest();
    }

    public List<Reservation> inspectReservations() {
        return seats.getReservations();
    }

    public List<ETicket> inspectTickets() {
        return tickets.getTickets();
    }

    public List<LoyaltyProfile> inspectProfiles() {
        return loyalty.getProfiles();
    }

    /** Chains that are neither COMPLETED nor COMPENSATED. */
    public List<WorkflowProgress> findStuckWorkflows() {
        return workflows.findUnfinished();
    }

    /**
     * Manually rolls back one stuck chain (B first, then A, based on what is persisted).
     *
     * @throws WorkflowNotFoundException if no chain has this id
     * @throws IllegalStateException     if the chain is already COMPLETED or COMPENSATED
     */
    public WorkflowProgress compensateWorkflow(String workflowId) {
        WorkflowProgress workflow = workflows.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
        if (workflow.getStatus().isTerminal()) {
            throw new IllegalStateException("Workflow " + workflowId + " is already " + workflow.getStatus()
                    + " and cannot be compensated");
        }
        return compensator.compensate(workflow);
    }

    /** Rolls back every stuck chain; returns the ones that were handled. */
    public List<WorkflowProgress> compensateAllStuck() {
        List<WorkflowProgress> handled = new ArrayList<>();
        for (WorkflowProgress stuck : workflows.findUnfinished()) {
            handled.add(compensator.compensate(stuck));
        }
        return handled;
    }

    /** Empties all four stores. Used for a clean demo run. */
    public void resetStore() {
        seats.clearAll();
        tickets.clearAll();
        loyalty.clearAll();
        workflows.deleteAll();
    }
}
