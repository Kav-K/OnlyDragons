package com.kaveenk.onlydragons.domain;

import static org.junit.jupiter.api.Assertions.*;
import com.kaveenk.onlydragons.domain.combat.CritOutcome;
import com.kaveenk.onlydragons.domain.combat.DamageResult;
import com.kaveenk.onlydragons.domain.combat.PhysicalImpact;
import com.kaveenk.onlydragons.domain.combat.ProcCommand;
import com.kaveenk.onlydragons.domain.encounter.EncounterResult;
import com.kaveenk.onlydragons.domain.encounter.TargetState;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.item.WeaponIdentity;
import com.kaveenk.onlydragons.domain.projectile.ShotContext;
import com.kaveenk.onlydragons.domain.projectile.Vector3;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.domain.stats.StatSnapshot;
import com.kaveenk.onlydragons.domain.stats.StatValue;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Structural DTO oracles for immutable ownership, one ultimate, physical-key identity and separate
 * HP/credit amounts. Uses fixed UUIDs and directly constructed results; the 210/52.5/20 example
 * proves representability and validation, not execution by the combat calculator or Paper collisions.
 * @see com.kaveenk.onlydragons.domain.combat.CombatServicesTest
 */
class DomainContractsTest {
    private static final UUID ENCOUNTER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID SHOT = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ARROW = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID HIT = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID CHILD = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final MechanicRevision PROFILE = new MechanicRevision("calibration", "fixture-v1");
    private static final PhysicalImpact.Key ORIGIN = new PhysicalImpact.Key(ENCOUNTER, ARROW, TARGET, 0);

    @Test
    void oneUltimatePerWeaponIsEnforcedAndCapturedItemsDoNotFollowMutation() {
        var enchants = new ArrayList<>(List.of(
                new WeaponDefinition.Enchantment("duplex", 5, WeaponDefinition.EnchantmentKind.ULTIMATE)));
        var definition = weapon(enchants);
        var shot = shot(enchants);
        enchants.clear();
        assertEquals("duplex", definition.enchantments().getFirst().id());
        assertEquals(definition.enchantments(), shot.enchantments());
        assertThrows(UnsupportedOperationException.class, () -> shot.enchantments().clear());
        var illegal = List.of(
                new WeaponDefinition.Enchantment("duplex", 5, WeaponDefinition.EnchantmentKind.ULTIMATE),
                new WeaponDefinition.Enchantment("fatal_tempo", 1, WeaponDefinition.EnchantmentKind.ULTIMATE));
        assertThrows(IllegalArgumentException.class, () -> weapon(illegal));
        assertThrows(IllegalArgumentException.class, () -> shot(illegal));
        assertThrows(IllegalArgumentException.class, () -> weapon(List.of(
                new WeaponDefinition.Enchantment("power", 1, WeaponDefinition.EnchantmentKind.ORDINARY),
                new WeaponDefinition.Enchantment("power", 2, WeaponDefinition.EnchantmentKind.ORDINARY))));
    }

    @Test
    void impactIdentityDistinguishesSimultaneousArrowsAndEncounterGenerations() {
        var volley = new PhysicalImpact.Key(ENCOUNTER, CHILD, TARGET, 0);
        var nextEncounter = new PhysicalImpact.Key(CHILD, ARROW, TARGET, 0);
        assertNotEquals(ORIGIN, volley);
        assertNotEquals(ORIGIN, nextEncounter);
        var claims = new java.util.HashSet<PhysicalImpact.Key>();
        assertTrue(claims.add(ORIGIN));
        assertFalse(claims.add(new PhysicalImpact.Key(ENCOUNTER, ARROW, TARGET, 0)));
        assertTrue(claims.add(volley));
    }

    @Test
    void fixtureCanRepresentOrdinaryCritThenSeparateFerocityHealthAndScore() {
        // Contract fixture only: this task supplies no production combat calculator.
        double ordinaryCrit = 100 * 1.4 * 1.5;
        assertEquals(210, ordinaryCrit, 1e-10);
        var ordinary = result(HIT, Optional.empty(), DamageResult.Kind.PHYSICAL,
                new DamageResult.Amounts(210, 210, 210, 210, 210, 210), Optional.empty());
        var child = result(CHILD, Optional.of(HIT), DamageResult.Kind.FEROCITY,
                new DamageResult.Amounts(210, 210, 210, 52.5, 20, 210), Optional.empty());
        assertTrue(ordinary.accepted());
        assertEquals(20, child.amounts().actualHealthDamage());
        assertEquals(210, child.amounts().contributionDamage());
        assertEquals(Optional.of(HIT), child.parentImpactId());
        var target = new TargetState(ENCOUNTER, TARGET, 1000, 0, 0);
        assertFalse(target.alive());
        var pending = new ProcCommand(CHILD, HIT, ORIGIN, OWNER, SHOT, 22, 210,
                ordinary.crit(), PROFILE, 0);
        assertEquals(ordinary.crit(), pending.crit());
        assertEquals(PROFILE, pending.mechanic());
    }

    @Test
    void rejectedHitsCannotAwardScoreOrHpAndActualLossCannotExceedRequested() {
        assertThrows(IllegalArgumentException.class, () -> result(HIT, Optional.empty(),
                DamageResult.Kind.PHYSICAL, new DamageResult.Amounts(210, 210, 210, 210, 0, 210),
                Optional.of(DamageResult.RejectionReason.TARGET_DEAD)));
        assertThrows(IllegalArgumentException.class, () -> new DamageResult.Amounts(1, 1, 1, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new DamageResult.Amounts(Double.NaN, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TargetState(ENCOUNTER, TARGET, 100, 101, 0));
        assertThrows(IllegalArgumentException.class, () -> new TargetState(ENCOUNTER, TARGET, 0, 0, 0));
        var rejected = result(CHILD, Optional.of(HIT), DamageResult.Kind.FEROCITY,
                new DamageResult.Amounts(210, 210, 210, 52.5, 0, 0),
                Optional.of(DamageResult.RejectionReason.TARGET_DEAD));
        assertFalse(rejected.accepted());
    }

    @Test
    void frozenEncounterResultCannotBeChangedByLaterContributorMutations() {
        var contributions = new HashMap<UUID, EncounterResult.Contribution>();
        contributions.put(OWNER, new EncounterResult.Contribution(230, 420, 0, true));
        var result = new EncounterResult(HIT, ENCOUNTER, "practice", PROFILE, 22, contributions);
        contributions.clear();
        assertEquals(230, result.participants().get(OWNER).actualHealthDamage());
        assertEquals(420, result.participants().get(OWNER).contributionDamage());
        assertThrows(UnsupportedOperationException.class, () -> result.participants().clear());
    }

    /**
     * Constructs a direct DTO with fixed origin/tick and supplied amounts; no ledger calculation occurs.
     */
    private static DamageResult result(UUID id, Optional<UUID> parent, DamageResult.Kind kind,
                                       DamageResult.Amounts amounts, Optional<DamageResult.RejectionReason> rejection) {
        return new DamageResult(id, parent, ORIGIN, OWNER, SHOT, kind, 20, PROFILE, amounts,
                CritOutcome.CRITICAL, 25, Map.of("power", 40.0), rejection);
    }

    /**
     * Builds the minimal 100-damage drawn definition to isolate enchant-list copying and validation.
     */
    private static WeaponDefinition weapon(List<WeaponDefinition.Enchantment> enchantments) {
        return new WeaponDefinition("calibration_bow", 1, "v1", WeaponDefinition.FiringMode.DRAWN_BOW,
                100, List.of(), enchantments);
    }

    /**
     * Builds a complete zero-default stat map with explicit weapon damage and a captured critical flag;
     * this is a DTO fixture, not a CritResolver probability sample.
     */
    private static ShotContext shot(List<WeaponDefinition.Enchantment> enchantments) {
        var stats = new EnumMap<StatKey, StatValue>(StatKey.class);
        for (StatKey key : StatKey.values()) stats.put(key, new StatValue(0, 0));
        stats.put(StatKey.WEAPON_DAMAGE, new StatValue(100, 100));
        return new ShotContext(ENCOUNTER, SHOT, ARROW, 0, Optional.empty(), OWNER,
                new WeaponIdentity(SHOT, "calibration_bow", 1, "v1"),
                new StatSnapshot("equipment-v1", stats, List.of()), enchantments, PROFILE, 10,
                new Vector3(0, 100, 0), new Vector3(0, 1, 0), CritOutcome.CRITICAL, 1, 1);
    }
}
