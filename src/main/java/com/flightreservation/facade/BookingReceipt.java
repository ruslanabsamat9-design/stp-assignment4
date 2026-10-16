package com.flightreservation.facade;

import com.flightreservation.workflow.WorkflowStatus;

/** What the client gets back after a successful booking. */
public record BookingReceipt(String workflowId, String reservationId, String ticketId, String passengerName,
                             String flightNumber, String seatNumber, WorkflowStatus status) {
}
