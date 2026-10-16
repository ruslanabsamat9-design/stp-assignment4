package com.flightreservation;

import com.flightreservation.loyalty.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Subsystem C used completely on its own: no facade, no other subsystem. */
class SubsystemCTest {

    @TempDir
    Path tempDir;

    @Test
    void savingAPreferenceCreatesAProfileAndAwardsPoints() {
        LoyaltyService service = new LoyaltyService(tempDir);

        LoyaltyProfile profile = service.savePreference("Amina", "vegan");

        assertEquals("Vegan", profile.getMealPreference());
        assertEquals(LoyaltyProfile.POINTS_PER_BOOKING, profile.getPoints());
        assertEquals(LoyaltyStatus.UPDATED, profile.getStatus());
        assertEquals(10, new LoyaltyService(tempDir).findByPassengerName("AMINA").getPoints());   // read back from disk
    }

    @Test
    void pointsAccumulateOnTheSameProfile() {
        LoyaltyService service = new LoyaltyService(tempDir);
        service.savePreference("Amina", "Vegan");

        LoyaltyProfile second = service.savePreference("amina", "Halal");

        assertEquals(1, service.getProfiles().size());
        assertEquals(20, second.getPoints());
        assertEquals("Halal", second.getMealPreference());
    }

    @Test
    void aMealThatIsNotOfferedIsRejectedAndNothingIsSaved() {
        LoyaltyService service = new LoyaltyService(tempDir);

        MealUnavailableException error = assertThrows(MealUnavailableException.class,
                () -> service.savePreference("Selma", "Pizza"));

        assertTrue(error.getMessage().contains("Pizza"));
        assertTrue(service.getProfiles().isEmpty());
        assertThrows(MealUnavailableException.class, () -> new MealPreferenceCatalog().require(null));
    }

    @Test
    void aSuspendedProfileCannotBeUpdatedUntilItIsReinstated() {
        LoyaltyService service = new LoyaltyService(tempDir);
        service.savePreference("Sam", "Standard");

        assertEquals(LoyaltyStatus.SUSPENDED, service.suspend("Sam").getStatus());
        assertThrows(IllegalStateException.class, () -> service.savePreference("Sam", "Vegan"));

        assertEquals(LoyaltyStatus.ACTIVE, service.reinstate("Sam").getStatus());
        assertEquals("Vegan", service.savePreference("Sam", "Vegan").getMealPreference());
    }

    @Test
    void unknownPassengersAreReportedAndClearAllEmptiesTheStore() {
        LoyaltyService service = new LoyaltyService(tempDir);
        service.savePreference("Sam", "Standard");

        assertThrows(LoyaltyProfileNotFoundException.class, () -> service.findByPassengerName("nobody"));
        assertThrows(LoyaltyProfileNotFoundException.class, () -> service.suspend("nobody"));
        service.clearAll();
        assertTrue(service.getProfiles().isEmpty());
    }

    @Test
    void profileEnforcesItsOwnLegalTransitions() {
        LoyaltyProfile profile = new LoyaltyProfile("Sam", "Standard", 5, LoyaltyStatus.ACTIVE);

        assertThrows(IllegalStateException.class, profile::reinstate);   // only SUSPENDED can be reinstated
        profile.suspend();
        assertThrows(IllegalStateException.class, profile::suspend);
        assertThrows(IllegalStateException.class, () -> profile.updatePreference("Vegan"));
        assertEquals(5, profile.getPoints());
    }

    @Test
    void profileBeanPropertiesRoundTrip() {
        LoyaltyProfile profile = new LoyaltyProfile();
        profile.setPassengerName("Sam");
        profile.setMealPreference("Halal");
        profile.setPoints(7);
        profile.setStatus(LoyaltyStatus.ACTIVE);

        assertEquals("Sam", profile.getPassengerName());
        assertEquals("Halal", profile.getMealPreference());
        assertEquals(7, profile.getPoints());
        assertEquals(LoyaltyStatus.ACTIVE, profile.getStatus());
    }
}
