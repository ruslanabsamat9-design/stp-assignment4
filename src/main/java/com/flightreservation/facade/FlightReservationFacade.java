package com.flightreservation.facade;

import com.flightreservation.loyalty.LoyaltyService;
import com.flightreservation.reservation.Reservation;
import com.flightreservation.reservation.SeatReservationService;
import com.flightreservation.ticket.ETicket;
import com.flightreservation.ticket.TicketService;
import com.flightreservation.workflow.WorkflowGateway;
import com.flightreservation.workflow.WorkflowProgress;
import com.flightreservation.workflow.WorkflowStatus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Client-facing facade: one call, "book", drives A (seat) -> B (e-ticket) -> C (loyalty / meal).
 *
 * The status of the chain is written to disk after every step. If a later step fails, the
 * facade compensates exactly what was already committed:
 *   B fails -> undo A only;   C fails -> undo B, then A.
 */
public class FlightReservationFacade {
    private final SeatReservationService seats;
    private final TicketService tickets;
    private final LoyaltyService loyalty;
    private final WorkflowGateway workflows;
    private final WorkflowCompensator compensator;
    private final ChainObserver observer;

    public FlightReservationFacade(Path dataDirectory) {
        this(dataDirectory, ChainObserver.NONE);
    }

    public FlightReservationFacade(Path dataDirectory, ChainObserver observer) {
        this.seats = new SeatReservationService(dataDirectory);
        this.tickets = new TicketService(dataDirectory);
        this.loyalty = new LoyaltyService(dataDirectory);
        this.workflows = new WorkflowGateway(dataDirectory);
        this.compensator = new WorkflowCompensator(seats, tickets, workflows);
        this.observer = observer;
    }

    /**
     * @throws InvalidBookingRequestException malformed request, nothing committed
     * @throws BookingFailedException         a step failed; getStage() says which one and
     *                                        isCompensated() whether anything had to be rolled back
     */
    public BookingReceipt book(BookingRequest request) {
        request.validate();

        // Step A: reserve the seat. If this fails nothing has been committed, so there is nothing to undo.
        Reservation reservation;
        try {
            reservation = seats.reserveSeat(request.passengerName(), request.flightNumber(), request.seatNumber());
        } catch (RuntimeException e) {
            throw new BookingFailedException("A", null, false, e);
        }
        WorkflowProgress workflow = new WorkflowProgress(UUID.randomUUID().toString(), request.passengerName(),
                reservation.getId(), null, WorkflowStatus.PENDING_B);
        checkpoint(workflow);

        // Step B: issue the e-ticket. If this fails, A is already committed -> undo A.
        ETicket ticket;
        try {
            ticket = tickets.issueTicket(reservation.getId(), request.passengerName(), request.baggageKg());
        } catch (RuntimeException e) {
            throw failAndCompensate("B", workflow, e);
        }
        workflow.setTicketId(ticket.getId());
        workflow.setStatus(WorkflowStatus.PENDING_C);
        checkpoint(workflow);

        // Step C: store the meal preference and award loyalty points. If this fails, A and B are
        // committed -> undo B, then A.
        try {
            loyalty.savePreference(request.passengerName(), request.mealPreference());
        } catch (RuntimeException e) {
            throw failAndCompensate("C", workflow, e);
        }
        workflow.setStatus(WorkflowStatus.COMPLETED);
        checkpoint(workflow);

        return new BookingReceipt(workflow.getWorkflowId(), reservation.getId(), ticket.getId(),
                request.passengerName(), reservation.getFlightNumber(), reservation.getSeatNumber(),
                workflow.getStatus());
    }

    /**
     * Call on application start. Looks at what is really on disk and rolls back every chain that
     * was interrupted (PENDING_B, PENDING_C or a half-finished COMPENSATING).
     *
     * @return the chains that were recovered; empty if everything was already consistent
     */
    public List<WorkflowProgress> recoverInterruptedWorkflows() {
        List<WorkflowProgress> recovered = new ArrayList<>();
        for (WorkflowProgress unfinished : workflows.findUnfinished()) {
            recovered.add(compensator.compensate(unfinished));
        }
        return recovered;
    }

    public Optional<WorkflowProgress> latestWorkflow() {
        return workflows.loadLatest();
    }

    public Optional<WorkflowProgress> findWorkflow(String workflowId) {
        return workflows.findById(workflowId);
    }

    private void checkpoint(WorkflowProgress workflow) {
        workflows.save(workflow);
        observer.afterCheckpoint(workflow);
    }

    private BookingFailedException failAndCompensate(String stage, WorkflowProgress workflow, RuntimeException cause) {
        workflow.setFailureReason("Step " + stage + ": " + cause.getMessage());
        compensator.compensate(workflow);
        return new BookingFailedException(stage, workflow.getWorkflowId(), true, cause);
    }
}
