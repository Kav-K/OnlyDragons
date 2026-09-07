package com.kaveenk.onlydragons.domain.enchant;

import com.kaveenk.onlydragons.domain.DomainChecks;
import com.kaveenk.onlydragons.domain.combat.DamageModifiers;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import com.kaveenk.onlydragons.domain.projectile.Vector3;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Attack effects consume trusted captured items. ItemRegistry remains the item validation authority. */
public final class EnchantEffects {
    public static final String REVISION = "enchant-calibration-v1";
    private static final double[] POWER = {0, .08, .16, .24, .32, .40, .50, .65};

    /** No implicit Gravity balance or Dragon Hunter alias. Empty table means deferred. */
    public record GravityProfile(String revision, Map<Integer, Double> airborneFractions, boolean legacyAlias) {
        public GravityProfile {
            DomainChecks.text(revision, "gravity revision");
            airborneFractions = Map.copyOf(airborneFractions);
            airborneFractions.forEach((level, fraction) -> {
                requireLevel(level, 6);
                if (level == 0) throw new IllegalArgumentException("Gravity table levels start at 1");
                DomainChecks.nonNegative(fraction, "gravity fraction");
            });
        }
        public static GravityProfile deferred() { return new GravityProfile("gravity-deferred", Map.of(), false); }
    }

    /** Implement only with an explicit named probability/damage interpretation; shot retains raw crit. */
    public interface OverloadPolicy {
        String revision();
        DamageModifiers modifiers(ShotContext shot, int level);
    }

    private final GravityProfile gravity;
    private final OverloadPolicy overload;

    public EnchantEffects(GravityProfile gravity, OverloadPolicy overload) {
        this.gravity = Objects.requireNonNull(gravity);
        this.overload = Objects.requireNonNull(overload);
        DomainChecks.text(overload.revision(), "overload revision");
    }

    public static EnchantEffects calibration() {
        return new EnchantEffects(GravityProfile.deferred(), new OverloadPolicy() {
            public String revision() { return "overload-deferred"; }
            public DamageModifiers modifiers(ShotContext shot, int level) {
                throw new IllegalStateException("Overload probability/damage profile has not been chosen");
            }
        });
    }

    /** Expanded production attack profile. Overload is applied after ordinary crit by DamageCalculator. */
    public static EnchantEffects checkpointTwo() {
        return new EnchantEffects(new GravityProfile(OverloadCapture.REVISION,
                Map.of(1, .05, 2, .10, 3, .15, 4, .20, 5, .30, 6, .40), false), new OverloadPolicy() {
            public String revision() { return OverloadCapture.REVISION; }
            public DamageModifiers modifiers(ShotContext shot, int level) {
                if (shot.overload().level() != level) throw new IllegalArgumentException("Missing Overload capture");
                return new DamageModifiers(Map.of(), Map.of());
            }
        });
    }

    public DamageModifiers modifiers(ShotContext shot, Vector3 impactPosition, boolean airborneTarget) {
        var bonuses = new TreeMap<String, Double>();
        var multipliers = new TreeMap<String, Double>();
        int power = level(shot.enchantments(), "power", 7);
        int snipe = level(shot.enchantments(), "snipe", 4);
        if (power > 0) bonuses.put("enchant:power", POWER[power]);
        if (snipe > 0) bonuses.put("enchant:snipe", snipeFraction(snipe, shot.launchPosition(), impactPosition));
        int gravityLevel = level(shot.enchantments(), "gravity", 6);
        int legacy = level(shot.enchantments(), "dragon_hunter", 5);
        if (legacy > 0 && (!gravity.legacyAlias() || gravityLevel > 0)) {
            throw new IllegalArgumentException("Dragon Hunter alias disabled or conflicts with Gravity");
        }
        gravityLevel = Math.max(gravityLevel, legacy);
        if (gravityLevel > 0) {
            Double fraction = gravity.airborneFractions().get(gravityLevel);
            if (fraction == null) throw new IllegalStateException("Gravity level has no chosen profile value");
            if (airborneTarget) bonuses.put("enchant:gravity/" + gravity.revision(), fraction);
        }
        int overloadLevel = level(shot.enchantments(), "overload", 5);
        if (overloadLevel > 0) {
            var extra = Objects.requireNonNull(overload.modifiers(shot, overloadLevel));
            extra.additiveFractions().forEach((id, value) -> bonuses.put("enchant:overload/" + overload.revision() + "/" + id, value));
            extra.multipliers().forEach((id, value) -> multipliers.put("enchant:overload/" + overload.revision() + "/" + id, value));
        }
        // Vicious is already a flat modifier in the trusted captured stat snapshot.
        return new DamageModifiers(bonuses, multipliers);
    }

    /** Continuous launch-to-impact displacement, never path length or the owner's new position. */
    public static double snipeFraction(int level, Vector3 launch, Vector3 impact) {
        requireLevel(level, 4);
        double distance = Math.hypot(Math.hypot(impact.x() - launch.x(), impact.y() - launch.y()), impact.z() - launch.z());
        return DomainChecks.nonNegative(distance * (level * .001), "snipe fraction");
    }

    public static int level(List<WeaponDefinition.Enchantment> enchants, String id, int maximum) {
        int level = enchants.stream().filter(e -> e.id().equals(id)).mapToInt(WeaponDefinition.Enchantment::level).findFirst().orElse(0);
        requireLevel(level, maximum);
        return level;
    }

    private static void requireLevel(int level, int maximum) {
        if (level < 0 || level > maximum) throw new IllegalArgumentException("Unsupported enchant level: " + level);
    }
}
