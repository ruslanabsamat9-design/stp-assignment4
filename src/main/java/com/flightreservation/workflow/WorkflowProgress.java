package com.flightreservation.workflow;

import java.util.ArrayList;
import java.util.List;

/** Durable record of one A -> B -> C chain. This is what the facade reads back after a restart. */
public class WorkflowProgress {
    private String workflowId;
    private String passengerName;
    private String reservationId;
    private String ticketId;
    private WorkflowStatus status;
    private String failureReason;
    private List<String> history = new ArrayList<>();

    public WorkflowProgress() {
    }

    public WorkflowProgress(String workflowId, String passengerName, String reservationId, String ticketId, WorkflowStatus status) {
        this.workflowId = workflowId;
        this.passengerName = passengerName;
        this.reservationId = reservationId;
        this.ticketId = ticketId;
        this.status = status;
    }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }
    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }
    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public List<String> getHistory() { return history; }
    public void setHistory(List<String> history) { this.history = history == null ? new ArrayList<>() : history; }

    /** Appends a line to the persisted audit trail (e.g. the order in which compensation steps ran). */
    public void log(String entry) {
        history.add(entry);
    }
}
