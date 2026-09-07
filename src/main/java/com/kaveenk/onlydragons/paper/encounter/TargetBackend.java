package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.*;
import org.bukkit.entity.LivingEntity;

/**
 * Classic server-thread native projection of an already authoritative domain target.
 * Scoring completion must freeze before defeated is called. Implementations must not
 * call damage accounting again while mirroring health; close is resource cleanup.
 * A dragon may retain ownership/tickets through death animation before released.
 * @see ManagedCombatService
 * @see DragonBackend
 */
public interface TargetBackend extends AutoCloseable {
    /**
     * Returns the owned native parent, including its retained death-animation object.
     * @return native target entity; liveness alone does not decide resource ownership
     */
    LivingEntity entity();
    /**
     * Declares the semantic modifier category independently from current height or phase.
     * @return whether Gravity's explicit AIRBORNE classification applies
     */
    boolean airborne();
    /**
     * Mirrors authoritative health/presentation and advances owned native motion.
     * @param state current domain HP/maxHP; must belong to this backend's encounter
     */
    void synchronize(TargetState state);
    /**
     * Begins native terminal handling after domain completion is already immutable.
     * @param result frozen defeated result; must not be recomputed from Bukkit killer data
     */
    void defeated(EncounterResult result);
    /**
     * Reports whether native ownership resources can be retired after defeat.
     * @return true only when the backend no longer requires retained native ownership
     */
    boolean released();
    /**
     * Controls completion notification relative to the native death transition.
     * @return true for immediate backends; dragons wait for confirmed native death
     */
    default boolean announcesImmediately() { return true; }
    /**
     * Receives final native death cancellation state; default backends need no observation.
     * @param cancelled whether the native death was vetoed, not owned damage suppression
     */
    default void deathObserved(boolean cancelled) {}
    /**
     * Receives the immediate public native removal reason before later polling.
     * @param cause observed removal cause for the owned parent; not merely isValid=false
     */
    default void removed(org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {}
    /**
     * Releases only this backend entity/resources; callers invoke on the server thread.
     */
    @Override void close();
}
