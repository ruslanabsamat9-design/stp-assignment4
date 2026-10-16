package com.flightreservation.ticket;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Subsystem B entry point (usable on its own, without any facade). */
public class TicketService {
    private final TicketRepository repository;
    private final BaggageRulesEngine baggageRules;

    public TicketService(Path dataDirectory) {
        this(new TicketRepository(dataDirectory), new BaggageRulesEngine());
    }

    public TicketService(TicketRepository repository, BaggageRulesEngine baggageRules) {
        this.repository = repository;
        this.baggageRules = baggageRules;
    }

    public ETicket issueTicket(String reservationId, String passengerName, double baggageKg) {
        baggageRules.check(baggageKg);

        ETicket ticket = new ETicket(UUID.randomUUID().toString(), reservationId, passengerName, baggageKg,
                ETicketStatus.PENDING);
        ticket.issue();
        return repository.save(ticket);
    }

    public ETicket cancelTicket(String ticketId) {
        ETicket ticket = findById(ticketId);
        ticket.cancel();
        return repository.save(ticket);
    }

    public ETicket findById(String ticketId) {
        return repository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    public Optional<ETicket> findByReservationId(String reservationId) {
        return repository.findByReservationId(reservationId);
    }

    public List<ETicket> getTickets() {
        return repository.findAll();
    }

    public void clearAll() {
        repository.deleteAll();
    }
}
