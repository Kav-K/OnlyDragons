package com.kaveenk.onlydragons.domain.item.anvil;

import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnchantRecipesTest {
    private final ItemRegistry registry = CalibrationLoadouts.compatibleRegistry();
    private final EnchantRecipes recipes = new EnchantRecipes(registry);
    private EnchantBook book(String id, int level) { return new EnchantBook("calibration-items-v4", id, level); }

    @Test void everySupportedLevelAppliesAtDeclaredCostWithoutChangingIdentityOrSource() {
        assertEquals(10, registry.enchantments().size());
        for (var enchant : registry.enchantments().values()) {
            for (int level : enchant.levelModifiers().keySet()) {
                var left = registry.create("ordinary_v4");
                var result = recipes.apply(left, book(enchant.id(), level), false);
                assertEquals(left.identity(), result.item().identity());
                assertEquals(left.registryRevision(), result.item().registryRevision());
                assertEquals(Map.of(), left.enchantLevels());
                assertEquals(Map.of(enchant.id(), level), result.item().enchantLevels());
                assertEquals(level * (enchant.kind() == WeaponDefinition.EnchantmentKind.ULTIMATE ? 4 : 2), result.levels());
            }
        }
    }

    @Test void combineRequiresImprovementAndHonorsMaximaAndRenameSurcharge() {
        assertEquals(new EnchantRecipes.Combined(book("power", 3), 7), recipes.combine(book("power", 2), book("power", 2), true));
        assertEquals(book("power", 5), recipes.combine(book("power", 2), book("power", 5), false).book());
        assertEquals(20, recipes.combine(book("duplex", 4), book("duplex", 4), false).levels());
        assertThrows(IllegalArgumentException.class, () -> recipes.combine(book("power", 5), book("power", 2), true));
        assertThrows(IllegalArgumentException.class, () -> recipes.combine(book("power", 7), book("power", 7), false));
        assertThrows(IllegalArgumentException.class, () -> recipes.combine(book("power", 2), book("snipe", 2), false));
    }

    @Test void completeMapPreservesOrdinaryEnchantsAndRejectsConflictingUltimate() {
        var first = recipes.apply(registry.create("ordinary_v4"), book("power", 2), false).item();
        var second = recipes.apply(first, book("duplex", 1), false).item();
        assertEquals(Map.of("power", 2, "duplex", 1), second.enchantLevels());
        assertThrows(IllegalArgumentException.class, () -> recipes.apply(second, book("fatal_tempo", 1), false));
        assertThrows(IllegalArgumentException.class, () -> recipes.apply(second, book("power", 1), true));
        assertEquals(7, recipes.apply(second, book("power", 2), true).levels());
    }

    @Test void legacyCatalogsRemainExactAndUnavailableConsumersReject() {
        var legacy = registry.create("ordinary");
        assertEquals(legacy.registryRevision(), recipes.apply(legacy, book("power", 1), false).item().registryRevision());
        assertThrows(IllegalArgumentException.class, () -> recipes.apply(legacy, book("overload", 1), false));
        assertThrows(IllegalArgumentException.class, () -> recipes.apply(legacy, book("flame", 1), false));
        assertThrows(IllegalArgumentException.class, () -> recipes.validate(new EnchantBook("missing", "power", 1)));
    }

    @Test void armorAndInactiveSlotsHaveNoEnabledBonusesAndNamesAreBoundedPlainText() {
        var power = registry.enchant("power");
        for (var slot : EnchantTarget.Slot.values())
            assertFalse(new EnchantTarget(EnchantTarget.Category.ARMOR, slot, null).supports(power));
        assertFalse(new EnchantTarget(EnchantTarget.Category.BOW, EnchantTarget.Slot.OFF_HAND, WeaponDefinition.FiringMode.DRAWN_BOW).supports(power));
        assertEquals("Test", EnchantRecipes.rename(" Test "));
        assertEquals("", EnchantRecipes.rename(" "));
        assertThrows(IllegalArgumentException.class, () -> EnchantRecipes.rename("a".repeat(51)));
        assertThrows(IllegalArgumentException.class, () -> EnchantRecipes.rename("bad\nname"));
        assertThrows(IllegalArgumentException.class, () -> EnchantRecipes.rename("§aname"));
    }
}
