package com.kaveenk.onlydragons.paper.item.codec;

import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.item.ItemInstance;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.ItemValidationException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.FutureTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static com.kaveenk.onlydragons.domain.item.ItemValidationException.Code.*;

class WeaponItemCodecTest {
    private final ItemRegistry registry = CalibrationLoadouts.registry();
    private final WeaponItemCodec codec = new WeaponItemCodec(registry);
    @BeforeEach void start() { MockBukkit.mock(); }
    @AfterEach void stop() { MockBukkit.unmock(); }

    @Test void pdcRoundTripRetainsIdentityAndTrustedContributions() {
        for (String definition : registry.definitions().keySet()) {
            ItemInstance input = registry.create(definition);
            var stack = codec.encode(input);
            var decoded = assertInstanceOf(ItemReadResult.Valid.class, codec.decode(stack.clone())).item();
            assertEquals(input, decoded.instance());
            assertEquals(registry.resolve(input), decoded);
            assertEquals(1, stack.getAmount());
            assertNotNull(stack.getItemMeta().displayName());
            assertFalse(stack.getItemMeta().lore().isEmpty());
            assertTrue(stack.getEnchantments().isEmpty(), "Presentation must not apply native damage enchants");
        }
    }

    @Test void textCannotGrantBehaviorAndChangedTextCannotRewriteIdentity() {
        var ordinary = new ItemStack(Material.BOW);
        ordinary.editMeta(meta -> {
            meta.displayName(Component.text("Duplex Calibration Bow"));
            meta.lore(List.of(Component.text("Fatal Tempo 5"), Component.text("weapon_damage: 999999")));
        });
        assertInstanceOf(ItemReadResult.NotManaged.class, codec.decode(ordinary));
        assertInstanceOf(ItemReadResult.NotManaged.class, codec.decode(null));
        var input = registry.create("duplex");
        var stack = codec.encode(input);
        stack.editMeta(meta -> { meta.displayName(Component.text("Renamed")); meta.lore(List.of(Component.text("Power 999"))); });
        assertEquals(input, assertInstanceOf(ItemReadResult.Valid.class, codec.decode(stack)).item().instance());
        assertEquals(codec.encode(input).getItemMeta(), codec.encode(assertInstanceOf(ItemReadResult.Valid.class, codec.decode(stack)).item().instance()).getItemMeta());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 2, Integer.MAX_VALUE})
    void compatibilityFixturesRejectUnsupportedSchemas(int schema) {
        assertInvalid(UNSUPPORTED_SCHEMA, changed(data -> data.set(key("schema"), PersistentDataType.INTEGER, schema)));
    }

    @Test void malformedAndUnknownFieldsCannotGrantUncheckedStatsOrKinds() {
        assertInvalid(MALFORMED_DATA, changed(data -> data.set(key("base_damage"), PersistentDataType.DOUBLE, 999999.0)));
        assertInvalid(MALFORMED_DATA, changed(data -> data.set(key("kind"), PersistentDataType.STRING, "ORDINARY")));
        assertInvalid(MALFORMED_DATA, changed(data -> data.remove(key("instance"))));
        assertInvalid(MALFORMED_DATA, changed(data -> data.set(key("schema"), PersistentDataType.STRING, "1")));
        assertInvalid(MALFORMED_DATA, changed(data -> data.set(key("instance"), PersistentDataType.STRING, "1-1-1-1-1")));
        assertInvalid(UNKNOWN_DEFINITION, changed(data -> data.set(key("definition"), PersistentDataType.STRING, "unknown")));
        assertInvalid(REVISION_MISMATCH, changed(data -> data.set(key("registry_revision"), PersistentDataType.STRING, "old")));
        assertInvalid(REVISION_MISMATCH, changed(data -> data.set(key("definition_revision"), PersistentDataType.STRING, "old")));
        var wrongRoot = new ItemStack(Material.BOW);
        wrongRoot.editMeta(meta -> meta.getPersistentDataContainer().set(WeaponItemCodec.ROOT, PersistentDataType.STRING, "forged"));
        assertInvalid(MALFORMED_DATA, wrongRoot);
    }

    @Test void invalidLevelsUnknownEnchantsAndTwoUltimatesRejectOnLoadAndWrite() {
        assertInvalid(INVALID_LEVEL, changed(data -> enchant(data, "duplex", 6)));
        assertInvalid(INVALID_LEVEL, changed(data -> enchant(data, "duplex", 0)));
        assertInvalid(UNKNOWN_ENCHANT, changed(data -> enchant(data, "unknown", 1)));
        assertInvalid(MULTIPLE_ULTIMATES, changed(data -> { enchant(data, "duplex", 1); enchant(data, "fatal_tempo", 1); }));
        assertInvalid(MALFORMED_DATA, changed(data -> {
            var levels = data.get(key("enchants"), PersistentDataType.TAG_CONTAINER);
            levels.set(new NamespacedKey("foreign", "duplex"), PersistentDataType.INTEGER, 1);
            data.set(key("enchants"), PersistentDataType.TAG_CONTAINER, levels);
        }));
        var input = registry.create("ordinary");
        assertEquals(MULTIPLE_ULTIMATES, assertThrows(ItemValidationException.class, () -> codec.encode(
                new ItemInstance(input.identity(), input.registryRevision(), Map.of("duplex", 1, "fatal_tempo", 1), List.of()))).code());
        assertInvalid(UNKNOWN_ROLL, changed(data -> {
            var rolls = data.get(key("rolls"), PersistentDataType.TAG_CONTAINER);
            rolls.set(key("damage_999999"), PersistentDataType.INTEGER, 1);
            data.set(key("rolls"), PersistentDataType.TAG_CONTAINER, rolls);
        }));
    }

    @Test void materialAndAmountMustMatchWhileUnrelatedPdcIsPreservedOnRead() {
        var stack = codec.encode(registry.create("ordinary"));
        stack.setAmount(2);
        assertInvalid(INVALID_AMOUNT, stack);
        stack.setAmount(1);
        var wrongMaterial = new ItemStack(Material.STICK);
        wrongMaterial.setItemMeta(stack.getItemMeta());
        assertInvalid(WRONG_MATERIAL, wrongMaterial);
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("other", "note"), PersistentDataType.STRING, "value"));
        var before = stack.clone();
        assertInstanceOf(ItemReadResult.Valid.class, codec.decode(stack));
        assertEquals(before, stack);
    }

    @Test void serverThreadBoundaryRejectsBeforeAnyItemAccess() throws Exception {
        var attempt = new FutureTask<>(() -> {
            assertThrows(IllegalStateException.class, () -> codec.decode(null));
            assertThrows(IllegalStateException.class, () -> codec.encode(registry.create("ordinary")));
            return true;
        });
        var thread = new Thread(attempt, "item-codec-thread-boundary-test");
        thread.start();
        try { assertTrue(attempt.get(5, java.util.concurrent.TimeUnit.SECONDS)); }
        finally { thread.interrupt(); thread.join(5000); }
    }

    @Test void nestedWrongTypesInvalidMembershipAndOversizedSetsReject() {
        assertInvalid(MALFORMED_DATA, changed(data -> {
            var levels = data.get(key("enchants"), PersistentDataType.TAG_CONTAINER);
            levels.set(key("duplex"), PersistentDataType.STRING, "5");
            data.set(key("enchants"), PersistentDataType.TAG_CONTAINER, levels);
        }));
        assertInvalid(MALFORMED_DATA, changed(data -> {
            var rolls = data.get(key("rolls"), PersistentDataType.TAG_CONTAINER);
            rolls.set(key("precision"), PersistentDataType.INTEGER, 2);
            data.set(key("rolls"), PersistentDataType.TAG_CONTAINER, rolls);
        }));
        assertInvalid(MALFORMED_DATA, changed(data -> {
            for (int i = 0; i <= ItemRegistry.MAX_ENTRIES; i++) enchant(data, "unknown_" + i, 1);
        }));
    }

    private ItemStack changed(Consumer<PersistentDataContainer> edit) {
        var stack = codec.encode(registry.create("ordinary"));
        stack.editMeta(meta -> {
            var root = meta.getPersistentDataContainer();
            var data = root.get(WeaponItemCodec.ROOT, PersistentDataType.TAG_CONTAINER);
            edit.accept(data);
            root.set(WeaponItemCodec.ROOT, PersistentDataType.TAG_CONTAINER, data);
        });
        return stack;
    }
    private static void enchant(PersistentDataContainer data, String id, int level) {
        var levels = data.get(key("enchants"), PersistentDataType.TAG_CONTAINER);
        levels.set(key(id), PersistentDataType.INTEGER, level);
        data.set(key("enchants"), PersistentDataType.TAG_CONTAINER, levels);
    }
    private void assertInvalid(ItemValidationException.Code code, ItemStack stack) {
        var invalid = assertInstanceOf(ItemReadResult.Invalid.class, codec.decode(stack));
        assertEquals(code, invalid.code());
        assertFalse(invalid.reason().isBlank());
    }
    private static NamespacedKey key(String id) { return new NamespacedKey("onlydragons", id); }
}
