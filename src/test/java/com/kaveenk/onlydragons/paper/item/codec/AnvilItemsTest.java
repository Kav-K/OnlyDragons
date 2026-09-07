package com.kaveenk.onlydragons.paper.item.codec;

import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.item.anvil.EnchantBook;
import com.kaveenk.onlydragons.paper.item.anvil.AnvilRecipeService;
import com.kaveenk.onlydragons.paper.item.anvil.EnchantBookCodec;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MockBukkit metadata/preview tests for all ten custom books and clone-preserving weapon edits. Exact fields, literal cost and unchanged inputs are checked; native XP debit, extraction and client slots belong to real anvil scenarios.
 */
class AnvilItemsTest {
    private final com.kaveenk.onlydragons.domain.item.ItemRegistry registry = CalibrationLoadouts.compatibleRegistry();
    private final WeaponItemCodec weapons = new WeaponItemCodec(registry);
    private final EnchantBookCodec books = new EnchantBookCodec(registry);
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void start() { MockBukkit.mock(); }
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void stop() { MockBukkit.unmock(); }
    /**
     * Encodes a trusted current-catalog book for preview/metadata tests.
     * @param id catalog enchant ID
     * @param level valid requested level
     * @return fresh native book item
     */
    private ItemStack book(String id, int level) { return books.encode(new EnchantBook("calibration-items-v4", id, level)); }

    /**
     * Compares original input, UUID, name, durability, prior-work cost, native enchant and foreign PDC while rejecting edits with a different item identity.
     */
    @Test void cloneEditPreservesEveryUnownedFieldAndDoesNotMutatePreviewInputs() {
        var input = registry.create("ordinary_v4");
        var left = weapons.encode(input);
        var foreign = new NamespacedKey("other", "keep");
        left.editMeta(meta -> {
            meta.displayName(Component.text("My treasured bow"));
            ((Damageable) meta).setDamage(37);
            ((Repairable) meta).setRepairCost(9);
            meta.addEnchant(Enchantment.UNBREAKING, 3, false);
            meta.getPersistentDataContainer().set(foreign, PersistentDataType.STRING, "foreign payload");
        });
        var before = left.clone();
        var edited = weapons.edit(left, registry.edit(input, Map.of("power", 3), input.rolledModifierIds()));
        assertEquals(before, left);
        assertEquals(input.identity(), ((ItemReadResult.Valid) weapons.decode(edited)).item().instance().identity());
        var meta = edited.getItemMeta();
        assertEquals(left.getItemMeta().displayName(), meta.displayName());
        assertEquals(37, ((Damageable) meta).getDamage());
        assertEquals(9, ((Repairable) meta).getRepairCost());
        assertEquals(3, meta.getEnchantLevel(Enchantment.UNBREAKING));
        assertEquals("foreign payload", meta.getPersistentDataContainer().get(foreign, PersistentDataType.STRING));
        assertThrows(IllegalArgumentException.class, () -> weapons.edit(left, registry.create("ordinary_v4")));
    }

    /**
     * Every catalog enchant produces typed metadata and nonitalic Roman-level lore; copied display text cannot grant book authority.
     */
    @Test void allTenBooksHaveValidatedStyledLoreAndSpoofedNamesAreOrdinary() {
        for (var enchant : registry.enchantments().values()) {
            var item = book(enchant.id(), enchant.maxLevel());
            assertInstanceOf(EnchantBookCodec.Read.Valid.class, books.decode(item));
            assertTrue(item.getItemMeta().getEnchantmentGlintOverride());
            for (var line : item.getItemMeta().lore()) assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
            String text = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().lore().getFirst());
            assertFalse(text.matches(".* [0-9]+$"));
            var fake = new ItemStack(Material.BOOK);
            fake.editMeta(meta -> { meta.displayName(item.getItemMeta().displayName()); meta.lore(item.getItemMeta().lore()); });
            assertInstanceOf(EnchantBookCodec.Read.Ordinary.class, books.decode(fake));
            item.editMeta(meta -> meta.getPersistentDataContainer().set(EnchantBookCodec.ROOT, PersistentDataType.STRING, "fake"));
            assertInstanceOf(EnchantBookCodec.Read.Invalid.class, books.decode(item));
        }
    }

    /**
     * Literal cost five for Power II plus rename and cost one for rename-only are checked without mutation; vanilla repair stays outside custom ownership.
     */
    @Test void renameAndRejectionPreviewsDoNotConsumeOrChangeInputsAndVanillaRemainsUnmanaged() {
        var service = new AnvilRecipeService(registry);
        var left = weapons.encode(registry.create("ordinary_v4"));
        var right = book("power", 2);
        var before = left.clone(); var rightBefore = right.clone();
        var result = service.preview(left, right, "Named bow");
        assertEquals(5, result.cost()); assertEquals(1, result.rightCount());
        assertEquals(Component.text("Named bow").decoration(TextDecoration.ITALIC, false), result.output().getItemMeta().displayName());
        assertEquals(before, left); assertEquals(rightBefore, right);
        assertEquals(1, service.preview(left, null, "Renamed").cost());
        assertNull(service.preview(left, null, PlainTextComponentSerializer.plainText().serialize(left.getItemMeta().displayName())).output());
        assertNull(service.preview(left, new ItemStack(Material.BOOK), "Named bow").output());
        assertFalse(service.preview(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.IRON_INGOT), "Sword").managed());
        left.setAmount(2); assertNull(service.preview(left, right, "Named bow").output());
    }
}
