package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.enchant.QuiverFlameProfile;
import java.util.Objects;
import java.util.UUID;

/**
 * One due managed fire strike. The encounter revalidates the exact accepted physical source.
 * <p>
 * Created by {@link com.kaveenk.onlydragons.application.fire.OwnedFireCoordinator}; no scheduler is owned here.
 * @param id nonnull strike identity for idempotency
 * @param source nonnull accepted PHYSICAL/DUPLEX result, retained in full
 * @param level captured Flame I or II
 * @param vulnerability finite current multiplier in [1,1.5], sampled once at drain
 * @param dueTick game tick no earlier than source.tick(); late execution is allowed
 */
public record FireCommand(UUID id, DamageResult source, int level, double vulnerability, long dueTick) {
    /**
     * Rejects virtual/rejected sources, unsupported levels, invalid vulnerability or backdated due time.
     * The encounter separately verifies exact source and captured Flame level.
     */
    public FireCommand {
        Objects.requireNonNull(id); Objects.requireNonNull(source);
        if (!source.accepted() || source.kind() != DamageResult.Kind.PHYSICAL && source.kind() != DamageResult.Kind.DUPLEX)
            throw new IllegalArgumentException("Fire requires accepted physical source");
        if (level == 0) throw new IllegalArgumentException("Missing Flame");
        QuiverFlameProfile.fireFraction(level);
        DomainChecks.finite(vulnerability, "fire vulnerability");
        if (vulnerability < 1 || vulnerability > 1.5) throw new IllegalArgumentException("Invalid vulnerability");
        if (dueTick < source.tick())
            throw new IllegalArgumentException("Fire precedes captured source");
    }
}
