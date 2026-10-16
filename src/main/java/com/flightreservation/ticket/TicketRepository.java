package com.flightreservation.ticket;

import com.flightreservation.storage.JsonFileStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Persistence gateway of subsystem B: the only class that talks to tickets.json. */
public class TicketRepository {
    private final JsonFileStore<ETicket> store;

    public TicketRepository(Path dataDirectory) {
        this.store = new JsonFileStore<>(dataDirectory.resolve("tickets.json"), ETicket.class);
    }

    public List<ETicket> findAll() {
        return store.loadAll();
    }

    public Optional<ETicket> findById(String id) {
        return store.loadAll().stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    public Optional<ETicket> findByReservationId(String reservationId) {
        return store.loadAll().stream().filter(t -> reservationId.equals(t.getReservationId())).findFirst();
    }

    /** Insert or replace by id. */
    public ETicket save(ETicket ticket) {
        List<ETicket> all = store.loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(ticket.getId())) {
                all.set(i, ticket);
                store.saveAll(all);
                return ticket;
            }
        }
        all.add(ticket);
        store.saveAll(all);
        return ticket;
    }

    public void deleteAll() {
        store.clear();
    }
}
