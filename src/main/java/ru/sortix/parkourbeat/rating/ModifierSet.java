package ru.sortix.parkourbeat.rating;

import lombok.NonNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The set of modifiers a player has switched on. Held per-player and read when a run
 * starts so that toggling mid-run is not possible (the run captures a snapshot).
 */
public class ModifierSet {
    private final EnumSet<Modifier> active = EnumSet.noneOf(Modifier.class);

    public boolean isActive(@NonNull Modifier modifier) {
        return this.active.contains(modifier);
    }

    /**
     * @return true if the modifier ended up enabled after the toggle
     */
    public boolean toggle(@NonNull Modifier modifier) {
        if (this.active.contains(modifier)) {
            this.active.remove(modifier);
            return false;
        }
        this.active.add(modifier);
        return true;
    }

    public void set(@NonNull Modifier modifier, boolean enabled) {
        if (enabled) this.active.add(modifier);
        else this.active.remove(modifier);
    }

    public void clear() {
        this.active.clear();
    }

    public boolean isEmpty() {
        return this.active.isEmpty();
    }

    @NonNull
    public Set<Modifier> getActive() {
        return Collections.unmodifiableSet(this.active);
    }

    /**
     * @return product of all active multipliers; 1.0 when nothing is on. PRACTICE
     * carries a 0.0 multiplier, which correctly zeroes the score for a practice run.
     */
    public double getTotalMultiplier() {
        double multiplier = 1.0D;
        for (Modifier modifier : this.active) {
            multiplier *= modifier.getScoreMultiplier();
        }
        return multiplier;
    }

    @NonNull
    public ModifierSet copy() {
        ModifierSet copy = new ModifierSet();
        copy.active.addAll(this.active);
        return copy;
    }
}
