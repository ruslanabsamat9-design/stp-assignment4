package com.flightreservation.facade;

import com.flightreservation.reservation.Reservation;
import com.flightreservation.reservation.ReservationStatus;
import com.flightreservation.reservation.SeatReservationService;
import com.flightreservation.ticket.ETicket;
import com.flightreservation.ticket.ETicketStatus;
import com.flightreservation.ticket.TicketService;
import com.flightreservation.workflow.WorkflowGateway;
import com.flightreservation.workflow.WorkflowProgress;
import com.flightreservation.workflow.WorkflowStatus;

import java.util.Optional;

/**
 * The one place that knows how to undo a chain. Shared by both facades.
 *
 * It decides what to undo by looking at what is really on disk (the ticket and reservation
 * records), not at in-memory flags, and every step is idempotent. So it works the same
 * for a failure we just caught and for a chain found half-done after a restart.
 * Order is always B first, then A.
 */
class WorkflowCompensator {
    private final SeatReservationService seats;
    private final TicketService tickets;
    private final WorkflowGateway workflows;

    WorkflowCompensator(SeatReservationService seats, TicketService tickets, WorkflowGateway workflows) {
        this.seats = seats;
        this.tickets = tickets;
        this.workflows = workflows;
    }

    WorkflowProgress compensate(WorkflowProgress workflow) {
        if (workflow.getStatus().isTerminal()) {
            return workflow;
        }

        workflow.setStatus(WorkflowStatus.COMPENSATING);
        workflows.save(workflow);

        // Step 1: undo B. The ticket may or may not exist (B may be the step that failed),
        // and the workflow may not even know its id if we crashed right after issuing it.
        // So look it up by reservation id in B's own storage.
        Optional<ETicket> ticket = tickets.findByReservationId(workflow.getReservationId());
        if (ticket.isPresent() && ticket.get().getStatus() != ETicketStatus.CANCELLED) {
            tickets.cancelTicket(ticket.get().getId());
            workflow.log("TICKET_CANCELLED:" + ticket.get().getId());
            workflows.save(workflow);
        }

        // Step 2: undo A.
        Reservation reservation = seats.findById(workflow.getReservationId());
        if (reservation.getStatus() != ReservationStatus.CANCELLED) {
            seats.cancelReservation(reservation.getId());
            workflow.log("SEAT_CANCELLED:" + reservation.getId());
        }

        workflow.setStatus(WorkflowStatus.COMPENSATED);
        workflows.save(workflow);
        return workflow;
    }
}
