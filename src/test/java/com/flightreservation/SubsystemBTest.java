package com.flightreservation;

import com.flightreservation.ticket.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Subsystem B used completely on its own: no facade, no other subsystem. */
class SubsystemBTest {

    @TempDir
    Path tempDir;

    @Test
    void issuingATicketPersistsItAsIssued() {
        TicketService service = new TicketService(tempDir);

        ETicket ticket = service.issueTicket("res-1", "Amina", 20.0);

        assertEquals(ETicketStatus.ISSUED, ticket.getStatus());
        ETicket reloaded = new TicketService(tempDir).findById(ticket.getId());   // read back from disk
        assertEquals("res-1", reloaded.getReservationId());
        assertEquals(20.0, reloaded.getBaggageKg());
    }

    @Test
    void baggageOverTheAllowanceIsRejectedAndNothingIsSaved() {
        TicketService service = new TicketService(tempDir);

        assertThrows(BaggageLimitExceededException.class, () -> service.issueTicket("res-1", "Bora", 23.1));
        assertEquals(ETicketStatus.ISSUED, service.issueTicket("res-1", "Bora", 23.0).getStatus());   // limit is inclusive
        assertEquals(1, service.getTickets().size());
    }

    @Test
    void baggageRulesRejectNegativeAndNaNWeights() {
        BaggageRulesEngine rules = new BaggageRulesEngine();

        assertDoesNotThrow(() -> rules.check(0));
        assertThrows(IllegalArgumentException.class, () -> rules.check(-1));
        assertThrows(IllegalArgumentException.class, () -> rules.check(Double.NaN));
        assertThrows(BaggageLimitExceededException.class, () -> rules.check(BaggageRulesEngine.FREE_ALLOWANCE_KG + 0.5));
    }

    @Test
    void ticketsCanBeFoundByReservationAndCancelled() {
        TicketService service = new TicketService(tempDir);
        ETicket ticket = service.issueTicket("res-7", "Sam", 5);

        assertEquals(ticket.getId(), service.findByReservationId("res-7").orElseThrow().getId());
        assertTrue(service.findByReservationId("other").isEmpty());

        service.cancelTicket(ticket.getId());

        assertEquals(ETicketStatus.CANCELLED, service.findById(ticket.getId()).getStatus());
    }

    @Test
    void unknownTicketsAreReportedAndClearAllEmptiesTheStore() {
        TicketService service = new TicketService(tempDir);
        service.issueTicket("res-1", "Sam", 5);

        assertThrows(TicketNotFoundException.class, () -> service.findById("missing"));
        assertThrows(TicketNotFoundException.class, () -> service.cancelTicket("missing"));
        service.clearAll();
        assertTrue(service.getTickets().isEmpty());
    }

    @Test
    void ticketEnforcesItsOwnLegalTransitions() {
        ETicket ticket = new ETicket("t1", "r1", "Sam", 2.0, ETicketStatus.PENDING);

        ticket.issue();
        assertEquals(ETicketStatus.ISSUED, ticket.getStatus());
        assertThrows(IllegalStateException.class, ticket::issue);

        ticket.cancel();
        assertEquals(ETicketStatus.CANCELLED, ticket.getStatus());
        assertThrows(IllegalStateException.class, ticket::cancel);
        assertThrows(IllegalStateException.class, ticket::issue);   // cancelled can never be issued again
    }

    @Test
    void ticketBeanPropertiesRoundTrip() {
        ETicket ticket = new ETicket();
        ticket.setId("t2");
        ticket.setReservationId("r2");
        ticket.setPassengerName("Sam");
        ticket.setBaggageKg(3.5);
        ticket.setStatus(ETicketStatus.PENDING);

        assertEquals("t2", ticket.getId());
        assertEquals("r2", ticket.getReservationId());
        assertEquals("Sam", ticket.getPassengerName());
        assertEquals(3.5, ticket.getBaggageKg());
        ticket.cancel();   // PENDING -> CANCELLED is legal
        assertEquals(ETicketStatus.CANCELLED, ticket.getStatus());
    }
}
