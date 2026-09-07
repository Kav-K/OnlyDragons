package com.kaveenk.onlydragons.paper.item.equipment;

import static org.junit.jupiter.api.Assertions.*;
import static com.kaveenk.onlydragons.domain.stats.StatKey.*;
import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.stats.*;
import com.kaveenk.onlydragons.paper.item.codec.*;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit equipment-cache and listener regressions. Literal stat values, immutable prior snapshots, full fingerprints and queued-task cleanup are independent oracles. Manually dispatched inventory events exercise adapter ordering, not protocol inventory transactions.
 */
class EquipmentStatsTest {
    ServerMock server;
    OnlyDragonsPlugin plugin;
    EquipmentStatsService stats;
    PlayerMock player;
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class);
        stats = plugin.equipment(); player = server.addPlayer(); drain();
    }
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    /**
     * Discards mock join messages before command-specific feedback assertions.
     */
    void drain() { while (player.nextMessage() != null) {} }
    /**
     * Dispatches arguments through registered production command routing.
     * @param text arguments after onlydragons
     */
    void command(String text) { server.dispatchCommand(player, "onlydragons " + text); }

    /**
     * Equal cloned contents reuse the validated snapshot; swapping hands selects only the main-hand source and leaves the old snapshot unchanged.
     */
    @Test void cacheReusesValidatedContentsAndReplacesHandsWithoutStacking() {
        var main = stats.createLoadout("ferocity_25");
        var off = stats.createLoadout("ferocity_500");
        var old = stats.refresh(player.getUniqueId(), main, off);
        assertSame(old, stats.refresh(player.getUniqueId(), main.clone(), off.clone()));
        assertEquals(25, old.stats().snapshot().raw(FEROCITY));
        assertEquals(100, old.stats().snapshot().raw(WEAPON_DAMAGE));
        assertEquals(50, old.stats().snapshot().raw(CRIT_DAMAGE));
        var next = stats.refresh(player.getUniqueId(), off, main);
        assertEquals(500, next.stats().snapshot().raw(FEROCITY));
        assertNotEquals(old.stats().snapshot().revision(), next.stats().snapshot().revision());
        assertEquals(25, old.stats().snapshot().raw(FEROCITY));
        var empty = stats.refresh(player.getUniqueId(), null, main);
        assertEquals(0, empty.stats().snapshot().raw(WEAPON_DAMAGE));
        assertEquals(0, empty.stats().snapshot().raw(FEROCITY));
    }
    /**
     * An enchant edit under the same UUID must change the fingerprint; corrupt stack count contributes no weapon stats.
     */
    @Test void sameUuidEditsAndCorruptContentsInvalidateWithoutLosingOldSnapshot() {
        var registry = CalibrationLoadouts.registry(); var codec = new WeaponItemCodec(registry);
        var instance = registry.create("ordinary");
        var old = stats.refresh(player.getUniqueId(), codec.encode(instance), null);
        var edited = codec.encode(registry.edit(instance, Map.of("vicious", 3), List.of()));
        var next = stats.refresh(player.getUniqueId(), edited, null);
        assertEquals(3, next.stats().snapshot().raw(FEROCITY));
        assertEquals(0, old.stats().snapshot().raw(FEROCITY));
        assertNotEquals(old.fingerprint(), next.fingerprint());
        edited.setAmount(2);
        var invalid = stats.refresh(player.getUniqueId(), edited, null);
        assertInstanceOf(ItemReadResult.Invalid.class, invalid.fingerprint().mainHand());
        assertEquals(0, invalid.stats().snapshot().raw(WEAPON_DAMAGE));
    }
    /**
     * A trusted 2.5 damage roll and a profile revision change independently invalidate cached identity.
     */
    @Test void changedRollContentsAndProfileRevisionsArePartOfFingerprint() {
        var base = CalibrationLoadouts.registry().definitions().get("ordinary");
        var definition = new ItemDefinition(base.weapon(), base.displayName(), base.material(), java.util.Set.of("damage"));
        var registry = new ItemRegistry("roll-v1", List.of(definition), List.of(), Map.of("damage",
                List.of(new StatModifier("roll:damage", WEAPON_DAMAGE, ModifierOperation.FLAT, 2.5, 0))));
        var service = new EquipmentStatsService(registry, StatProfile.calibration());
        var codec = new WeaponItemCodec(registry); var instance = registry.create("ordinary");
        var before = service.refresh(player.getUniqueId(), codec.encode(instance), null);
        var after = service.refresh(player.getUniqueId(), codec.encode(registry.edit(instance, Map.of(), List.of("damage"))), null);
        assertEquals(102.5, after.stats().snapshot().raw(WEAPON_DAMAGE));
        assertEquals(100, before.stats().snapshot().raw(WEAPON_DAMAGE));
        assertNotEquals(before.fingerprint(), after.fingerprint());
        var p = StatProfile.calibration();
        var other = new EquipmentStatsService(registry, new StatProfile(p.id(), 2, p.definitions(), p.effectiveCaps()));
        assertNotEquals(before.fingerprint(), other.refresh(player.getUniqueId(), codec.encode(instance), null).fingerprint());
    }
    /**
     * Exercises command denial, actual encoded grant, unknown IDs, full storage and malformed bonus values.
     */
    @Test void permissionsGrantsFullInventoryAndMalformedRequests() {
        command("dev loadout ordinary"); assertTrue(player.nextMessage().contains("permission"));
        assertEquals(-1, player.getInventory().first(Material.BOW));
        player.setOp(true); command("dev loadout ordinary"); assertTrue(player.nextMessage().contains("Loadout")); assertTrue(org.bukkit.ChatColor.stripColor(player.nextMessage()).startsWith("Granted"));
        assertInstanceOf(ItemReadResult.Valid.class, new WeaponItemCodec(CalibrationLoadouts.registry()).decode(player.getInventory().getItem(0)));
        command("dev loadout missing"); assertTrue(org.bukkit.ChatColor.stripColor(player.nextMessage()).startsWith("Invalid"));
        for (int i=0;i<36;i++) player.getInventory().setItem(i, new ItemStack(Material.STONE,64));
        command("dev loadout ordinary"); assertEquals("Inventory full; no loadout granted.",org.bukkit.ChatColor.stripColor(player.nextMessage()));
        command("dev bonus ferocity NaN"); assertTrue(org.bukkit.ChatColor.stripColor(player.nextMessage()).startsWith("Invalid"));
        command("dev bonus unknown 1"); assertTrue(org.bukkit.ChatColor.stripColor(player.nextMessage()).startsWith("Invalid"));
    }
    /**
     * Repeated bonus replacement does not stack, rejected overflow retains the snapshot, and quit clears session-only sources.
     */
    @Test void bonusReplacementIsAtomicAndSessionOnly() {
        player.setOp(true); player.getInventory().setItemInMainHand(stats.createLoadout("ordinary"));
        command("dev bonus ferocity 25"); command("dev bonus ferocity 25");
        assertEquals(25, stats.refresh(player).stats().snapshot().raw(FEROCITY));
        var old = stats.refresh(player);
        command("dev bonus weapon_damage 1000000000");
        assertEquals(old, stats.refresh(player));
        command("dev clear"); assertEquals(0, stats.refresh(player).stats().snapshot().raw(FEROCITY));
        stats.bonus(player, FEROCITY, 25);
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        server.getScheduler().performTicks(2);
        assertNull(stats.cached(player.getUniqueId()));
        assertEquals(0, stats.refresh(player).stats().snapshot().raw(FEROCITY));
    }
    /**
     * A bounded mock inventory view permits synthetic slot events; later refresh sees completed mutations while quit/disable cancel stale work.
     */
    @Test void eventsRefreshAfterMutationAndQuitCancelsQueuedWork() {
        server.getScheduler().performTicks(2);
        player.getInventory().setItemInMainHand(stats.createLoadout("ferocity_25"));
        // MockBukkit's default view cannot convert slots. Supply a bounded test view;
        // the production listener only uses the event player, never the converted slot.
        player.openInventory(new org.mockbukkit.mockbukkit.inventory.SimpleInventoryViewMock(
                player, player.getInventory(), player.getInventory(), org.bukkit.event.inventory.InventoryType.PLAYER) {
            /** Uses identity slot conversion only to let MockBukkit construct this event; production does not consume the converted slot.
 * @param raw synthetic raw slot
 * @return the same slot index
 */ @Override public int convertSlot(int raw) { return raw; }
        });
        server.getPluginManager().callEvent(new PlayerInventorySlotChangeEvent(player, 0,
                new ItemStack(Material.AIR), player.getInventory().getItemInMainHand()));
        server.getScheduler().performTicks(2);
        assertEquals(25, stats.cached(player.getUniqueId()).stats().snapshot().raw(FEROCITY));
        server.getPluginManager().callEvent(new PlayerSwapHandItemsEvent(player, new ItemStack(Material.AIR), player.getInventory().getItemInMainHand()));
        player.getInventory().setItemInOffHand(player.getInventory().getItemInMainHand());
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        server.getScheduler().performTicks(2);
        assertEquals(0, stats.cached(player.getUniqueId()).stats().snapshot().raw(FEROCITY));
        server.getPluginManager().callEvent(new PlayerItemHeldEvent(player, 0, 1));
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        server.getScheduler().performTicks(2);
        assertNull(stats.cached(player.getUniqueId()));
        stats.refresh(player);
        server.getPluginManager().disablePlugin(plugin);
        assertEquals(0, stats.sessionCount());
    }
    /**
     * A bonus valid without gear but invalid after equipping is cleared and explained instead of retaining obsolete stats.
     */
    @Test void incompatibleBonusOnNewEquipmentClearsInsteadOfServingStaleStats() {
        stats.bonus(player, WEAPON_DAMAGE, 1000000000);
        player.getInventory().setItemInMainHand(stats.createLoadout("ordinary"));
        var next = stats.refresh(player);
        assertEquals(100, next.stats().snapshot().raw(WEAPON_DAMAGE));
        assertTrue(next.notice().contains("Session bonuses cleared"));
        assertTrue(next.fingerprint().externalSources().isEmpty());
    }
    /**
     * Checks exact Ferocity explanation and source identity, then verifies inspection permission denial.
     */
    @Test void statsExplainShowsIdentitySourcesAndPermissionDenial() {
        player.getInventory().setItemInMainHand(stats.createLoadout("ferocity_25"));
        command("stats explain");
        var messages = new StringBuilder(); String message;
        while ((message=player.nextMessage())!=null) messages.append(message).append('\n');
        assertTrue(messages.toString().contains("item:ferocity_25"));
        assertTrue(messages.toString().contains("raw=25.0 effective=25.0"));
        player.addAttachment(plugin,"onlydragons.stats",false);
        command("stats"); assertTrue(player.nextMessage().contains("permission"));
    }
}
