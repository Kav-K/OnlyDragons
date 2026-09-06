package com.kaveenk.onlydragons.paper.encounter;

import com.kaveenk.onlydragons.domain.encounter.*;
import org.bukkit.entity.LivingEntity;

/** Classic server-thread native projection. Terminal scoring precedes defeated(); close is resource cleanup.
 * A dragon backend may retain a bounded death animation after defeated(), then report released(). */
public interface TargetBackend extends AutoCloseable {
    LivingEntity entity();
    boolean airborne();
    void synchronize(TargetState state);
    void defeated(EncounterResult result);
    boolean released();
    @Override void close();
}
