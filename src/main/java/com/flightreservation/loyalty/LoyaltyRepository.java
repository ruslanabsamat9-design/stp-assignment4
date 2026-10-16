package com.flightreservation.loyalty;

import com.flightreservation.storage.JsonFileStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Persistence gateway of subsystem C: the only class that talks to loyalty.json. */
public class LoyaltyRepository {
    private final JsonFileStore<LoyaltyProfile> store;

    public LoyaltyRepository(Path dataDirectory) {
        this.store = new JsonFileStore<>(dataDirectory.resolve("loyalty.json"), LoyaltyProfile.class);
    }

    public List<LoyaltyProfile> findAll() {
        return store.loadAll();
    }

    public Optional<LoyaltyProfile> findByPassengerName(String passengerName) {
        return store.loadAll().stream().filter(p -> p.getPassengerName().equalsIgnoreCase(passengerName)).findFirst();
    }

    /** Insert or replace by passenger name (case-insensitive). */
    public LoyaltyProfile save(LoyaltyProfile profile) {
        List<LoyaltyProfile> all = store.loadAll();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getPassengerName().equalsIgnoreCase(profile.getPassengerName())) {
                all.set(i, profile);
                store.saveAll(all);
                return profile;
            }
        }
        all.add(profile);
        store.saveAll(all);
        return profile;
    }

    public void deleteAll() {
        store.clear();
    }
}
