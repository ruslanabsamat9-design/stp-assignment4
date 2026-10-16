package com.flightreservation.reservation;

import com.flightreservation.storage.JsonFileStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Persistence gateway of subsystem A: the only class that talks to reservations.json. */
public class ReservationRepository {
    private final JsonFileStore<Reservation> store;

    public ReservationRepository(Path dataDirectory) {
        this.store = new JsonFileStore<>(dataDirectory.resolve("reservations.json"), Reservation.class);
    }

    public List<Reservation> findAll() {
        return store.loadAll();
    }

    public Optional<Reservation> findById(String id) {
        return store.loadAll().stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    /** Insert or replace by id. */
    public Reservation save(Reservation reservation) {
        List<Reservation> all = store.loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(reservation.getId())) {
                all.set(i, reservation);
                store.saveAll(all);
                return reservation;
            }
        }
        all.add(reservation);
        store.saveAll(all);
        return reservation;
    }

    public void deleteAll() {
        store.clear();
    }
}
