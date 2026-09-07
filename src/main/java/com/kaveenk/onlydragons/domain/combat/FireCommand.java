package com.kaveenk.onlydragons.domain.combat;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.enchant.QuiverFlameProfile;
import java.util.Objects;
import java.util.UUID;

/** One due managed fire strike. The encounter revalidates the exact accepted physical source. */
public record FireCommand(UUID id, DamageResult source, int level, double vulnerability, long dueTick) {
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
