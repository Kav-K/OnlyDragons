package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CritOutcome;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.item.WeaponIdentity;
import com.kaveenk.onlydragons.domain.stats.StatSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Captured at acceptance; swapping gear cannot rewrite an already accepted physical arrow. */
public record ShotContext(UUID encounterId, UUID shotId, UUID projectileId, int ordinal,
                          Optional<UUID> parentProjectileId, UUID ownerId, WeaponIdentity weapon,
                          StatSnapshot stats, List<WeaponDefinition.Enchantment> enchantments,
                          MechanicRevision mechanic, long launchTick, Vector3 launchPosition,
                          Vector3 initialVelocity, CritOutcome crit, double drawScale, double projectileScale) {
    public ShotContext {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(shotId, "shotId");
        Objects.requireNonNull(projectileId, "projectileId");
        if (ordinal < 0) throw new IllegalArgumentException("ordinal must be nonnegative");
        Objects.requireNonNull(parentProjectileId, "parentProjectileId");
        if (parentProjectileId.filter(projectileId::equals).isPresent()) {
            throw new IllegalArgumentException("A projectile cannot parent itself");
        }
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(stats, "stats");
        enchantments = WeaponDefinition.validatedEnchantments(enchantments);
        Objects.requireNonNull(mechanic, "mechanic");
        DomainChecks.nonNegative(launchTick, "launchTick");
        Objects.requireNonNull(launchPosition, "launchPosition");
        Objects.requireNonNull(initialVelocity, "initialVelocity");
        Objects.requireNonNull(crit, "crit");
        DomainChecks.nonNegative(drawScale, "drawScale");
        DomainChecks.nonNegative(projectileScale, "projectileScale");
    }
}
