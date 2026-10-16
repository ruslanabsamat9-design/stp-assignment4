package com.flightreservation.loyalty;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Pure business logic of subsystem C: which special meals the airline can actually serve. */
public class MealPreferenceCatalog {
    private final Map<String, String> offered = new LinkedHashMap<>();

    public MealPreferenceCatalog() {
        for (String meal : new String[]{"Standard", "Vegetarian", "Vegan", "Kosher", "Halal", "Gluten-Free"}) {
            offered.put(meal.toLowerCase(Locale.ROOT), meal);
        }
    }

    /** Returns the canonical spelling of the meal or throws if it is not offered. */
    public String require(String meal) {
        String key = meal == null ? "" : meal.trim().toLowerCase(Locale.ROOT);
        String canonical = offered.get(key);
        if (canonical == null) {
            throw new MealUnavailableException("Meal not available: '" + meal + "' (offered: " + String.join(", ", offered.values()) + ")");
        }
        return canonical;
    }
}
