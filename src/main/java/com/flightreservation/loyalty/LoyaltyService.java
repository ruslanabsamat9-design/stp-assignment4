package com.flightreservation.loyalty;

import java.nio.file.Path;
import java.util.List;

/** Subsystem C entry point (usable on its own, without any facade). */
public class LoyaltyService {
    private final LoyaltyRepository repository;
    private final MealPreferenceCatalog mealCatalog;

    public LoyaltyService(Path dataDirectory) {
        this(new LoyaltyRepository(dataDirectory), new MealPreferenceCatalog());
    }

    public LoyaltyService(LoyaltyRepository repository, MealPreferenceCatalog mealCatalog) {
        this.repository = repository;
        this.mealCatalog = mealCatalog;
    }

    public LoyaltyProfile savePreference(String passengerName, String mealPreference) {
        String meal = mealCatalog.require(mealPreference);

        LoyaltyProfile profile = repository.findByPassengerName(passengerName)
                .orElseGet(() -> new LoyaltyProfile(passengerName, null, 0, LoyaltyStatus.ACTIVE));
        profile.updatePreference(meal);
        return repository.save(profile);
    }

    public LoyaltyProfile suspend(String passengerName) {
        LoyaltyProfile profile = findByPassengerName(passengerName);
        profile.suspend();
        return repository.save(profile);
    }

    public LoyaltyProfile reinstate(String passengerName) {
        LoyaltyProfile profile = findByPassengerName(passengerName);
        profile.reinstate();
        return repository.save(profile);
    }

    public LoyaltyProfile findByPassengerName(String passengerName) {
        return repository.findByPassengerName(passengerName)
                .orElseThrow(() -> new LoyaltyProfileNotFoundException(passengerName));
    }

    public List<LoyaltyProfile> getProfiles() {
        return repository.findAll();
    }

    public void clearAll() {
        repository.deleteAll();
    }
}
