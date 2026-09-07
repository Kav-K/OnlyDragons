package com.kaveenk.onlydragons.gametests.item;

import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.item.ItemDefinition;
import com.kaveenk.onlydragons.domain.item.ItemInstance;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.ItemValidationException;
import com.kaveenk.onlydragons.domain.stats.ModifierOperation;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.domain.stats.StatModifier;
import com.kaveenk.onlydragons.domain.stats.ModifierSources;
import com.kaveenk.onlydragons.domain.stats.StatProfile;
import com.kaveenk.onlydragons.domain.stats.StatSnapshotFactory;
import com.kaveenk.onlydragons.gametests.Scenario;
import com.kaveenk.onlydragons.gametests.ScenarioContext;
import com.kaveenk.onlydragons.paper.item.codec.ItemReadResult;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import static com.kaveenk.onlydragons.domain.item.ItemValidationException.Code.*;

/**
 * Real Paper item-byte/PDC validation with a synthetic inventory and pinned legacy catalog.
 * An explicit complete ID map supplies independent per-loadout expectations; adding
 * a catalog entry requires a deliberate oracle update. Forged presentation, malformed
 * tags and unsupported identities must fail through the actual codec. No logged-in
 * player, equipment event or combat effect is claimed here.
 */
public final class ItemIdentityScenario implements Scenario {
    /**
     * Checks exact catalog coverage, serialized identity, trusted projections and typed rejection codes.
     * @param context server-thread report and cleanup owner
     */
    @Override public void start(ScenarioContext context) {
        context.mechanicRevision("item-codec-v4");
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        context.check("codec_loaded_from_production", true,
                WeaponItemCodec.class.getClassLoader() == context.production().getClass().getClassLoader());
        ItemRegistry registry = CalibrationLoadouts.registry();
        WeaponItemCodec codec = new WeaponItemCodec(registry);
        var snapshots = new StatSnapshotFactory(StatProfile.calibration());
        int roundtrips = 0;
        int calibratedSnapshots = 0;
        var expectedFerocity = Map.of("ordinary", 0.0, "crit", 0.0, "ferocity_25", 25.0,
                "ferocity_100", 100.0, "ferocity_500", 500.0, "tracer", 0.0, "duplex", 0.0, "fatal_tempo", 25.0, "shortbow_v1", 0.0, "tracer_return_v2", 0.0);
        var expectedIds = new java.util.TreeSet<>(expectedFerocity.keySet());
        var actualIds = new java.util.TreeSet<>(registry.definitions().keySet());
        context.check("all_loadout_ids", List.copyOf(expectedIds), List.copyOf(actualIds));
        if (!expectedIds.equals(actualIds)) {
            throw new IllegalStateException("Calibration loadout oracle mismatch: expected "
                    + expectedIds + ", actual " + actualIds);
        }
        for (String id : registry.definitions().keySet()) {
            ItemInstance instance = registry.create(id);
            var result = codec.decode(bytes(codec.encode(instance)));
            if (result instanceof ItemReadResult.Valid valid && valid.item().equals(registry.resolve(instance))) roundtrips++;
            if (result instanceof ItemReadResult.Valid valid) {
                var snapshot = snapshots.create("item-calibration-" + id, valid.item().resolvedWeapon(),
                        ModifierSources.empty()).snapshot();
                if (snapshot.raw(StatKey.WEAPON_DAMAGE) == 100 && snapshot.raw(StatKey.CRIT_DAMAGE) == 50
                        && snapshot.raw(StatKey.CRIT_CHANCE) == (id.equals("crit") ? 100 : 0)
                        && snapshot.raw(StatKey.FEROCITY) == expectedFerocity.get(id)
                        && snapshot.effective(StatKey.FEROCITY) == expectedFerocity.get(id)) calibratedSnapshots++;
            }
        }
        context.check("all_loadouts_roundtrip", expectedFerocity.size(), roundtrips);
        context.check("all_loadouts_production_snapshots", expectedFerocity.size(), calibratedSnapshots);
        context.check("calibration_no_crit_bonus", true, registry.definitions().keySet().stream()
                .map(id -> registry.resolve(registry.create(id)).resolvedWeapon())
                .allMatch(weapon -> weapon.statModifiers().stream().noneMatch(modifier -> modifier.key() == StatKey.CRIT_DAMAGE)));
        var first = registry.create("duplex");
        var second = registry.create("duplex");
        context.check("distinct_instance_ids", true, !first.identity().instanceId().equals(second.identity().instanceId()));
        var inventory = Bukkit.createInventory(null, 9, Component.text("T02 synthetic inventory"));
        try {
            inventory.setItem(0, bytes(codec.encode(first)));
            inventory.setItem(1, bytes(codec.encode(second)));
            inventory.setItem(8, inventory.getItem(0));
            inventory.setItem(0, null);
            context.check("inventory_move_identity", true, instance(codec, inventory.getItem(8)).equals(first)
                    && instance(codec, inventory.getItem(1)).equals(second));
        } finally { inventory.clear(); }
        var stack = codec.encode(first);
        context.check("presentation_generated", true, stack.getItemMeta().hasDisplayName()
                && stack.getItemMeta().hasLore() && Boolean.TRUE.equals(stack.getItemMeta().getEnchantmentGlintOverride())
                && stack.getEnchantments().isEmpty());
        var ordinary = new ItemStack(Material.BOW);
        ordinary.editMeta(meta -> {
            meta.displayName(stack.getItemMeta().displayName());
            meta.lore(stack.getItemMeta().lore());
            meta.setEnchantmentGlintOverride(true);
        });
        context.check("rename_cannot_grant", true, codec.decode(bytes(ordinary)) instanceof ItemReadResult.NotManaged);
        stack.editMeta(meta -> { meta.displayName(Component.text("Fatal Tempo Bow")); meta.lore(List.of(Component.text("999999 Damage"))); });
        context.check("renamed_managed_identity", true, instance(codec, bytes(stack)).equals(first));
        var base = registry.resolve(registry.create("ordinary"));
        context.check("base_damage_once", true, base.definition().weapon().baseDamage() == 100
                && base.statModifiers().stream().noneMatch(modifier -> modifier.key() == StatKey.WEAPON_DAMAGE));
        var vicious = registry.edit(registry.create("ordinary"), Map.of("vicious", 5), List.of());
        context.check("trusted_vicious_modifier", true, ((ItemReadResult.Valid) codec.decode(bytes(codec.encode(vicious)))).item()
                .statModifiers().contains(new StatModifier("enchant:vicious", StatKey.FEROCITY, ModifierOperation.FLAT, 5, 0)));
        reject(context, codec, "single_ultimate_rejected", MULTIPLE_ULTIMATES,
                changed(codec, registry, data -> { enchant(data, "duplex", 1); enchant(data, "fatal_tempo", 1); }));
        String writeRejection = "accepted";
        try { codec.encode(new ItemInstance(first.identity(), first.registryRevision(), Map.of("duplex", 5, "fatal_tempo", 5), List.of())); }
        catch (ItemValidationException rejected) { writeRejection = rejected.code().name(); }
        context.check("write_boundary_rejected", MULTIPLE_ULTIMATES.name(), writeRejection);
        reject(context, codec, "invalid_level_rejected", INVALID_LEVEL, changed(codec, registry, data -> enchant(data, "duplex", 6)));
        reject(context, codec, "unknown_enchant_rejected", UNKNOWN_ENCHANT, changed(codec, registry, data -> enchant(data, "unknown", 1)));
        reject(context, codec, "unknown_definition_rejected", UNKNOWN_DEFINITION,
                changed(codec, registry, data -> data.set(key("definition"), PersistentDataType.STRING, "unknown")));
        reject(context, codec, "malformed_type_rejected", MALFORMED_DATA,
                changed(codec, registry, data -> data.set(key("schema"), PersistentDataType.STRING, "1")));
        reject(context, codec, "malformed_uuid_rejected", MALFORMED_DATA,
                changed(codec, registry, data -> data.set(key("instance"), PersistentDataType.STRING, "1-1-1-1-1")));
        reject(context, codec, "raw_stats_rejected", MALFORMED_DATA,
                changed(codec, registry, data -> data.set(key("base_damage"), PersistentDataType.DOUBLE, 999999.0)));
        reject(context, codec, "forged_kind_rejected", MALFORMED_DATA,
                changed(codec, registry, data -> data.set(key("kind"), PersistentDataType.STRING, "ORDINARY")));
        // Explicit compatibility fixtures: schema 0 predates the released contract; schema 2 is unknown.
        reject(context, codec, "legacy_schema_rejected", UNSUPPORTED_SCHEMA,
                changed(codec, registry, data -> data.set(key("schema"), PersistentDataType.INTEGER, 0)));
        reject(context, codec, "future_schema_rejected", UNSUPPORTED_SCHEMA,
                changed(codec, registry, data -> data.set(key("schema"), PersistentDataType.INTEGER, 2)));
        reject(context, codec, "revision_mismatch_rejected", REVISION_MISMATCH,
                changed(codec, registry, data -> data.set(key("registry_revision"), PersistentDataType.STRING, "previous-fixture")));
        var wrongMaterial = new ItemStack(Material.STICK);
        wrongMaterial.setItemMeta(codec.encode(first).getItemMeta());
        reject(context, codec, "wrong_material_rejected", WRONG_MATERIAL, wrongMaterial);
        var invalidAmount = codec.encode(first);
        invalidAmount.setAmount(2);
        reject(context, codec, "invalid_amount_rejected", INVALID_AMOUNT, invalidAmount);

        var modifier = new StatModifier("roll:precision", StatKey.CRIT_CHANCE, ModifierOperation.FLAT, 5, 0);
        var rollRegistry = new ItemRegistry("roll-fixture-v1", List.of(new ItemDefinition(base.definition().weapon(),
                "Roll fixture", "BOW", Set.of("precision"))), List.of(registry.enchant("vicious"), registry.enchant("duplex")),
                Map.of("precision", List.of(modifier)));
        var rollCodec = new WeaponItemCodec(rollRegistry);
        var unrolled = rollRegistry.edit(rollRegistry.create("ordinary"), Map.of("duplex", 5), List.of());
        var rolled = rollRegistry.edit(unrolled, Map.of("duplex", 2, "vicious", 3), List.of("precision"));
        var restored = (ItemReadResult.Valid) rollCodec.decode(bytes(rollCodec.encode(rolled)));
        context.check("trusted_roll_roundtrip", true, restored.item().instance().equals(rolled) && restored.item().statModifiers().contains(modifier));
        var projected = restored.item().resolvedWeapon();
        var editedSnapshot = snapshots.create("edited-item-calibration", projected, ModifierSources.empty()).snapshot();
        context.check("edited_item_production_snapshot", true, editedSnapshot.raw(StatKey.WEAPON_DAMAGE) == 100
                && editedSnapshot.raw(StatKey.CRIT_DAMAGE) == 50 && editedSnapshot.raw(StatKey.CRIT_CHANCE) == 5
                && editedSnapshot.raw(StatKey.FEROCITY) == 3);
        var expectedModifiers = new java.util.ArrayList<>(base.definition().weapon().statModifiers());
        expectedModifiers.add(modifier);
        expectedModifiers.add(new StatModifier("enchant:vicious", StatKey.FEROCITY, ModifierOperation.FLAT, 3, 0));
        context.check("resolved_weapon_projection", true, projected.baseDamage() == 100
                && projected.id().equals(rolled.identity().definitionId())
                && projected.revision().equals(rolled.identity().definitionRevision())
                && projected.statModifiers().equals(expectedModifiers.stream().sorted(StatModifier.EXPLANATION_ORDER).toList())
                && projected.enchantments().equals(restored.item().enchantments())
                && projected.enchantments().stream().anyMatch(enchant -> enchant.id().equals("duplex") && enchant.level() == 2)
                && unrolled.enchantLevels().equals(Map.of("duplex", 5)));
        reject(context, codec, "unknown_roll_rejected", UNKNOWN_ROLL, changed(codec, registry, data -> {
            var rolls = data.get(key("rolls"), PersistentDataType.TAG_CONTAINER);
            rolls.set(key("damage_999999"), PersistentDataType.INTEGER, 1);
            data.set(key("rolls"), PersistentDataType.TAG_CONTAINER, rolls);
        }));
        context.observe("registryRevision", registry.revision());
        context.observe("instances", List.of(first.identity().instanceId().toString(), second.identity().instanceId().toString()));
        context.observe("scope", "Production registry/codec; real ItemStack bytes and synthetic inventory. No player login, effects, equipment cache or native combat assertion.");
        context.finish();
    }

    /**
     * Round-trips through Paper's native serialized item bytes, not a Java clone.
     */
    private static ItemStack bytes(ItemStack stack) { return ItemStack.deserializeBytes(stack.serializeAsBytes()); }
    /**
     * Requires a production-valid decode before returning canonical instance identity.
     */
    private static ItemInstance instance(WeaponItemCodec codec, ItemStack stack) {
        var result = codec.decode(stack);
        if (!(result instanceof ItemReadResult.Valid valid)) throw new AssertionError("Expected valid item, got " + result);
        return valid.item().instance();
    }
    /**
     * Re-serializes a malformed item and requires the exact nonempty codec rejection diagnostic.
     */
    private static void reject(ScenarioContext context, WeaponItemCodec codec, String assertion,
                               ItemValidationException.Code expected, ItemStack stack) {
        var result = codec.decode(bytes(stack));
        context.check(assertion, expected.name(), result instanceof ItemReadResult.Invalid invalid && !invalid.reason().isBlank()
                ? invalid.code().name() : "not rejected: " + result);
    }
    /**
     * Mutates the managed PDC subtree of a valid seed to create one explicit invalid-input trial.
     */
    private static ItemStack changed(WeaponItemCodec codec, ItemRegistry registry, Consumer<PersistentDataContainer> change) {
        var stack = codec.encode(registry.create("ordinary"));
        stack.editMeta(meta -> {
            var root = meta.getPersistentDataContainer();
            var data = root.get(WeaponItemCodec.ROOT, PersistentDataType.TAG_CONTAINER);
            change.accept(data);
            root.set(WeaponItemCodec.ROOT, PersistentDataType.TAG_CONTAINER, data);
        });
        return stack;
    }
    /**
     * Writes a deliberately chosen raw enchant tag for codec-boundary trials.
     */
    private static void enchant(PersistentDataContainer data, String id, int level) {
        var enchants = data.get(key("enchants"), PersistentDataType.TAG_CONTAINER);
        enchants.set(key(id), PersistentDataType.INTEGER, level);
        data.set(key("enchants"), PersistentDataType.TAG_CONTAINER, enchants);
    }
    /**
     * Uses the production namespace for controlled malformed-tag construction.
     */
    private static NamespacedKey key(String id) { return new NamespacedKey("onlydragons", id); }
}
