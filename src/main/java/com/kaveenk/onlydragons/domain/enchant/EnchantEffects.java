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

/**
 * Attack effects consume trusted captured items. ItemRegistry remains the item validation authority.
 * <p>
 * Pure attack-modifier assembly from {@link ShotContext}; it does not grant item identity,
 * register listeners, schedule effects or add Vicious again. Supplied extension policies must
 * remain stable for the shots using them; arbitrary OverloadPolicy implementations are not copied.
 */
public final class EnchantEffects {
    /**
     * Identity of the preserved six-enchant calibration; expanded effects use {@link OverloadCapture#REVISION}.
     */
    public static final String REVISION = "enchant-calibration-v1";
    /**
     * Index by validated Power level 0–7; entries are additive fractions, not percentage points.
     */
    private static final double[] POWER = {0, .08, .16, .24, .32, .40, .50, .65};

    /**
     * No implicit Gravity balance or Dragon Hunter alias. Empty table means deferred.
     * <p>
     * @param revision nonblank author-maintained table identity
     * @param airborneFractions copied map of levels 1–6 to finite nonnegative additive fractions
     * @param legacyAlias whether dragon_hunter may supply a level when Gravity is absent
     */
    public record GravityProfile(String revision, Map<Integer, Double> airborneFractions, boolean legacyAlias) {
        /**
         * Validates every supplied entry; a partial table is allowed but a selected missing level fails in modifiers.
         */
        public GravityProfile {
            DomainChecks.text(revision, "gravity revision");
            airborneFractions = Map.copyOf(airborneFractions);
            airborneFractions.forEach((level, fraction) -> {
                requireLevel(level, 6);
                if (level == 0) throw new IllegalArgumentException("Gravity table levels start at 1");
                DomainChecks.nonNegative(fraction, "gravity fraction");
            });
        }
        /**
         * Returns an empty immutable table with alias disabled; selecting Gravity then fails explicitly.
         */
        public static GravityProfile deferred() { return new GravityProfile("gravity-deferred", Map.of(), false); }
    }

    /** Implement only with an explicit named probability/damage interpretation; shot retains raw crit. */
    public interface OverloadPolicy {
        /**
         * Returns a stable nonblank policy label used to namespace diagnostic modifier IDs.
         */
        String revision();
        /**
         * Returns nonnull immutable extra modifiers for the captured shot and validated equipped level.
         * Implementations must not reroll impact-time Overload or mutate the shot; checkpointTwo
         * verifies the existing capture and returns no extra factor because DamageCalculator applies it.
         */
        DamageModifiers modifiers(ShotContext shot, int level);
    }

    private final GravityProfile gravity;
    private final OverloadPolicy overload;

    /**
     * Retains nonnull Gravity and Overload policies and validates the extension revision.
     * The caller owns extension lifetime and any thread-safety needed by a custom implementation.
     */
    public EnchantEffects(GravityProfile gravity, OverloadPolicy overload) {
        this.gravity = Objects.requireNonNull(gravity);
        this.overload = Objects.requireNonNull(overload);
        DomainChecks.text(overload.revision(), "overload revision");
    }

    /**
     * Returns the legacy profile: Power/Snipe active, Gravity and Overload explicitly deferred.
     * Selecting a deferred effect throws IllegalStateException instead of silently ignoring it.
     */
    public static EnchantEffects calibration() {
        return new EnchantEffects(GravityProfile.deferred(), new OverloadPolicy() {
            /** Identifies the deliberately unavailable legacy policy. */
            public String revision() { return "overload-deferred"; }
            /** Rejects every requested Overload evaluation until a concrete policy is selected. */
            public DamageModifiers modifiers(ShotContext shot, int level) {
                throw new IllegalStateException("Overload probability/damage profile has not been chosen");
            }
        });
    }

    /**
     * Expanded production attack profile. Overload is applied after ordinary crit by DamageCalculator.
     * <p>
     * Returns the expanded table: Gravity I–VI adds 5/10/15/20/30/40% for AIRBORNE
     * targets; the supplied Overload level must match the shot's immutable capture.
     */
    public static EnchantEffects checkpointTwo() {
        return new EnchantEffects(new GravityProfile(OverloadCapture.REVISION,
                Map.of(1, .05, 2, .10, 3, .15, 4, .20, 5, .30, 6, .40), false), new OverloadPolicy() {
            /** Identifies the policy whose outcome is already captured in the shot. */
            public String revision() { return OverloadCapture.REVISION; }
            /** Checks captured level consistency and returns no extra factor, avoiding double application. */
            public DamageModifiers modifiers(ShotContext shot, int level) {
                if (shot.overload().level() != level) throw new IllegalArgumentException("Missing Overload capture");
                return new DamageModifiers(Map.of(), Map.of());
            }
        });
    }

    /**
     * Builds one physical attack's named modifiers. Virtual children inherit the resolved basis.
     * @param shot trusted nonnull launch snapshot; enchant level extraction does not validate a catalog
     * @param impactPosition finite collision position in blocks, used by Snipe
     * @param airborneTarget explicit backend classification, not a height/phase inference
     * @return immutable additive fractions and separate factors
     * @throws IllegalArgumentException for unsupported levels or conflicting/disabled aliases
     * @throws IllegalStateException for a selected deferred Gravity/Overload policy
     */
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

    /**
     * Continuous launch-to-impact displacement, never path length or the owner's new position.
     * <p>
     * Level I at 10 blocks adds 0.01 (+1%); 9.99 blocks adds 0.00999 without rounding.
     * @param level 0–4, where zero disables the bonus
     * @param launch nonnull finite world-block launch position
     * @param impact nonnull finite world-block collision position
     * @return finite nonnegative level × 0.001 × Euclidean displacement
     * @throws IllegalArgumentException for invalid level or overflowing displacement arithmetic
     */
    public static double snipeFraction(int level, Vector3 launch, Vector3 impact) {
        requireLevel(level, 4);
        double distance = Math.hypot(Math.hypot(impact.x() - launch.x(), impact.y() - launch.y()), impact.z() - launch.z());
        return DomainChecks.nonNegative(distance * (level * .001), "snipe fraction");
    }

    /**
     * Reads the first matching enchant ID; trusted item/shot validation owns uniqueness.
     * @param enchants nonnull captured list
     * @param id exact case-sensitive ID to select
     * @param maximum inclusive supported maximum
     * @return 0 if absent, otherwise the first matching level
     * @throws IllegalArgumentException when the selected level lies outside 0..maximum
     */
    public static int level(List<WeaponDefinition.Enchantment> enchants, String id, int maximum) {
        int level = enchants.stream().filter(e -> e.id().equals(id)).mapToInt(WeaponDefinition.Enchantment::level).findFirst().orElse(0);
        requireLevel(level, maximum);
        return level;
    }

    /**
     * Rejects levels outside the effect consumer's inclusive 0..maximum range; zero means absent.
     */
    private static void requireLevel(int level, int maximum) {
        if (level < 0 || level > maximum) throw new IllegalArgumentException("Unsupported enchant level: " + level);
    }
}
