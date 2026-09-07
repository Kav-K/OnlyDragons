package com.kaveenk.onlydragons.domain.projectile;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CritOutcome;
import com.kaveenk.onlydragons.domain.enchant.OverloadCapture;
import com.kaveenk.onlydragons.domain.enchant.EnchantEffects;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.item.WeaponIdentity;
import com.kaveenk.onlydragons.domain.stats.StatSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Captured at acceptance; swapping gear cannot rewrite an already accepted physical arrow.
 * <p>
 * Contains no live equipment/player/entity reference. The adapter validates trusted item identity
 * and reserves admission; constructing this record alone proves neither firing nor a collision.
 * @param encounterId nonnull admitted generation
 * @param shotId nonnull trigger/group identity shared by primary and Duplex
 * @param projectileId nonnull real arrow identity
 * @param ordinal nonnegative projectile index within the group, distinct from impact/commit ordinal
 * @param parentProjectileId nonnull optional real primary UUID, never self
 * @param ownerId nonnull captured shooter UUID independent of current session
 * @param weapon nonnull persistent weapon identity
 * @param stats nonnull immutable effective/raw launch stats, excluding impact-time Tempo
 * @param enchantments copied ID-sorted captured selections with one ultimate at most
 * @param mechanic nonnull retained combat policy identity
 * @param launchTick nonnegative arrow emission game tick
 * @param launchPosition nonnull finite world-block origin used by Snipe and aimed Tracer
 * @param initialVelocity nonnull finite blocks/tick launch vector, retained across owner movement
 * @param crit nonnull captured ordinary roll
 * @param drawScale finite nonnegative factor; this DTO does not cap it at one
 * @param projectileScale finite nonnegative factor; Duplex receives its own scaled basis
 * @param overload nonnull immutable mega capture consistent with enchant level, raw CC and ordinary crit
 */
public record ShotContext(UUID encounterId, UUID shotId, UUID projectileId, int ordinal,
                          Optional<UUID> parentProjectileId, UUID ownerId, WeaponIdentity weapon,
                          StatSnapshot stats, List<WeaponDefinition.Enchantment> enchantments,
                          MechanicRevision mechanic, long launchTick, Vector3 launchPosition,
                          Vector3 initialVelocity, CritOutcome crit, double drawScale, double projectileScale,
                          OverloadCapture overload) {
    /** Legacy callers cannot silently construct an equipped Overload shot without a decision. */
    public ShotContext(UUID encounterId, UUID shotId, UUID projectileId, int ordinal,
                       Optional<UUID> parentProjectileId, UUID ownerId, WeaponIdentity weapon,
                       StatSnapshot stats, List<WeaponDefinition.Enchantment> enchantments,
                       MechanicRevision mechanic, long launchTick, Vector3 launchPosition,
                       Vector3 initialVelocity, CritOutcome crit, double drawScale, double projectileScale) {
        this(encounterId, shotId, projectileId, ordinal, parentProjectileId, ownerId, weapon, stats,
                enchantments, mechanic, launchTick, launchPosition, initialVelocity, crit, drawScale,
                projectileScale, OverloadCapture.absent());
    }

    /**
     * Validates structural ownership, nonnegative inputs and captured Overload consistency, then
     * freezes enchant order. Does not consult item catalogs, a random source or live target state.
     */
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
        Objects.requireNonNull(overload, "overload");
        if (overload.level() != EnchantEffects.level(enchantments, "overload", 5)
                || (overload.level() > 0 && overload.rawCritChance() != stats.raw(com.kaveenk.onlydragons.domain.stats.StatKey.CRIT_CHANCE))
                || (overload.megaCritical() && crit != CritOutcome.CRITICAL))
            throw new IllegalArgumentException("Overload capture disagrees with shot inputs");
        DomainChecks.nonNegative(drawScale, "drawScale");
        DomainChecks.nonNegative(projectileScale, "projectileScale");
    }
}
