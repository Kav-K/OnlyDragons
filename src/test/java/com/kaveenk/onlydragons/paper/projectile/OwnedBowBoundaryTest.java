package com.kaveenk.onlydragons.paper.projectile;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.projectile.SettledHit;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Synthetic native-event boundary regressions with explicit pre-event ammo debit and next-tick settlement. They isolate final veto, immutable claims and pending-group session exit; real input/collision/quit ordering is proved by the owned-firing Paper scenario.
 */
class OwnedBowBoundaryTest {
    ServerMock server;
    OnlyDragonsPlugin plugin;
    PlayerMock player;
    OwnedBowService bows;
    UUID encounter;
    EntityShootBowEvent lastBowEvent;
    List<SettledHit> hits = new ArrayList<>();
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class);
        player = server.addPlayer(); player.setGameMode(GameMode.SURVIVAL);
        bows = plugin.bows(); encounter = UUID.randomUUID();
        bows.openEncounter(encounter, player.getWorld(), new BoundingBox(-1000, -1000, -1000, 1000, 1000, 1000), new MechanicRevision("calibration", "v1"));
        plugin.combat().observeSettled(hits::add);
    }
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    /**
     * Creates a native mock arrow, injects a half-force bow/launch dispatch and seeds nine arrows to model one prior native debit from ten.
     * @param loadout trusted fixture loadout
     * @return captured primary entity
     */
    Arrow shoot(String loadout) {
        ItemStack bow = plugin.equipment().createLoadout(loadout); player.getInventory().setItemInMainHand(bow);
        // Native ammunition drawing precedes EntityShootBowEvent on the pinned server.
        player.getInventory().setItem(9, new ItemStack(Material.ARROW, 9));
        Arrow arrow = player.getWorld().spawn(player.getEyeLocation(), Arrow.class); arrow.setShooter(player);
        lastBowEvent = new EntityShootBowEvent(player, bow, new ItemStack(Material.ARROW), arrow, EquipmentSlot.HAND, 0.5f, true);
        bows.drawn(lastBowEvent);
        bows.launched(new ProjectileLaunchEvent(arrow));
        return arrow;
    }
    /**
     * Registers a mock living parent in the admitted arena.
     * @return native mock cow used by synthetic collisions
     */
    Cow target() {
        Cow cow = player.getWorld().spawn(player.getLocation(), Cow.class);
        bows.registerTarget(encounter, UUID.randomUUID(), cow); return cow;
    }
    /**
     * A final hit veto produces exactly one rejected claim and removes native/registry identity before any repeat can claim it.
     */
    @Test void finalPhysicalVetoRetainsOneClaimAndRetiresBeforeReceiver() {
        Arrow arrow = shoot("ordinary"); Cow cow = target();
        ProjectileHitEvent event = new ProjectileHitEvent(arrow, cow, null, null); bows.hit(event);
        bows.hit(new ProjectileHitEvent(arrow, cow, null, null)); event.setCancelled(true);
        assertEquals(1, bows.pendingClaims()); server.getScheduler().performOneTick();
        assertEquals(1, hits.size()); assertEquals(SettledHit.Rejection.PHYSICAL_VETO, hits.getFirst().rejection().orElseThrow());
        assertTrue(bows.projectile(arrow.getUniqueId()).isEmpty()); assertFalse(arrow.isValid());
        bows.hit(new ProjectileHitEvent(arrow, cow, null, null)); assertEquals(0, bows.pendingClaims());
    }
    /**
     * Re-registering the same native entity under a different logical target invalidates the captured target object.
     */
    @Test void targetReplacementBeforeSettlementRejectsCapturedRegistration() {
        Arrow arrow = shoot("ordinary"); Cow cow = target(); bows.hit(new ProjectileHitEvent(arrow, cow, null, null));
        bows.unregisterTarget(cow.getUniqueId()); bows.registerTarget(encounter, UUID.randomUUID(), cow);
        server.getScheduler().performOneTick();
        assertEquals(1, hits.size()); assertEquals(SettledHit.Rejection.TARGET_CHANGED, hits.getFirst().rejection().orElseThrow());
    }
    /**
     * Direct session invalidation exercises the pending-child branch without claiming a native quit event can precede next-tick settlement.
     */
    @Test void immediateSessionEndKeepsAcceptedPrimaryButCancelsChildAndRetainsDebit() {
        Arrow arrow = shoot("duplex"); UUID token = bows.currentSession(player.getUniqueId()).orElseThrow();
        assertEquals(1, bows.reservedCapacity()); bows.clearSession(player.getUniqueId(), token, false);
        assertTrue(arrow.isValid()); assertEquals(1, bows.projectiles().size()); assertEquals(0, bows.reservedCapacity());
        assertEquals(9, player.getInventory().getItem(9).getAmount()); assertEquals(0, bows.pendingGroups());
        bows.hit(new ProjectileHitEvent(arrow, target(), null, null)); server.getScheduler().performOneTick();
        assertEquals(1, hits.size()); assertTrue(hits.getFirst().accepted());
        assertEquals(token, hits.getFirst().projectile().sessionToken()); assertEquals(0, bows.capacityUsed());
    }
    /**
     * Reset removes both native arrow and pending claim before the scheduler can notify accounting.
     */
    @Test void resetDuringPendingClaimCannotDeliverToLaterGeneration() {
        Arrow arrow = shoot("ordinary"); Cow cow = target(); bows.hit(new ProjectileHitEvent(arrow, cow, null, null));
        bows.endEncounter(encounter); server.getScheduler().performOneTick();
        assertTrue(hits.isEmpty()); assertFalse(arrow.isValid()); assertEquals(0, bows.capacityUsed()); assertEquals(0, bows.pendingClaims());
    }
    /**
     * Late bow cancellation releases both primary/child reservation and one debit; another reset cannot refund again.
     */
    @Test void finalBowVetoRefundsWholeGroupExactlyOnce() {
        Arrow arrow = shoot("duplex"); lastBowEvent.setCancelled(true);
        server.getScheduler().performOneTick();
        assertFalse(arrow.isValid()); assertEquals(0, bows.capacityUsed()); assertEquals(0, bows.pendingGroups());
        assertEquals(10, player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum());
        bows.endEncounter(encounter); server.getScheduler().performOneTick();
        assertEquals(10, player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum()); assertTrue(hits.isEmpty());
    }
    /**
     * Late launch cancellation removes owned entities/reservation and returns one charge without delivering a collision.
     */
    @Test void nativeLaunchVetoRetiresPrimaryAndReservedDuplexWithoutAClaim() {
        Arrow arrow = shoot("duplex"); var launch = new ProjectileLaunchEvent(arrow);
        bows.launched(launch); launch.setCancelled(true); server.getScheduler().performOneTick();
        assertFalse(arrow.isValid()); assertEquals(0, bows.reservedCapacity()); assertEquals(0, bows.pendingGroups());
        assertEquals(10, player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum()); assertTrue(hits.isEmpty());
    }
    /**
     * Replacement/bow/launch veto remains terminal even on immediate session exit, and the unrelated replacement entity survives.
     * @param veto retained native event mutation to exercise
     * @param sessionExit whether to invalidate the session before scheduled settlement
     */
    @ParameterizedTest
    @CsvSource({"replacement,false", "replacement,true", "bow,true", "launch,true"})
    void finalLaunchVetoCannotRetainPrimaryOrRefundTwice(String veto, boolean sessionExit) {
        Arrow primary = shoot("duplex");
        Arrow replacement = player.getWorld().spawn(player.getEyeLocation(), Arrow.class);
        switch (veto) {
            case "replacement" -> lastBowEvent.setProjectile(replacement);
            case "bow" -> lastBowEvent.setCancelled(true);
            case "launch" -> {
                var event = new ProjectileLaunchEvent(primary);
                bows.launched(event);
                event.setCancelled(true);
            }
            default -> throw new AssertionError(veto);
        }
        if (sessionExit) {
            UUID token = bows.currentSession(player.getUniqueId()).orElseThrow();
            bows.clearSession(player.getUniqueId(), token, false);
        }
        server.getScheduler().performOneTick();
        assertFalse(primary.isValid());
        assertTrue(replacement.isValid());
        assertEquals(0, bows.capacityUsed());
        assertEquals(0, bows.pendingGroups());
        assertEquals(10, player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum());
        bows.endEncounter(encounter);
        server.getScheduler().performOneTick();
        assertEquals(10, player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum());
        assertTrue(hits.isEmpty());
    }
    /**
     * An unregistered arrow keeps its native damage/critical state and entity through encounter reset.
     */
    @Test void unrelatedProjectileIsNotSuppressedClaimedOrRemoved() {
        Arrow arrow = player.getWorld().spawn(player.getEyeLocation(), Arrow.class); arrow.setDamage(4); arrow.setCritical(true);
        bows.launched(new ProjectileLaunchEvent(arrow)); bows.hit(new ProjectileHitEvent(arrow, target(), null, null));
        assertEquals(4, arrow.getDamage()); assertTrue(arrow.isCritical()); assertTrue(arrow.isValid());
        assertEquals(0, bows.pendingClaims()); bows.endEncounter(encounter); assertTrue(arrow.isValid());
    }
    /**
     * Actual mock death cleans a retained arrow even after its session token is gone and prevents later pending delivery.
     */
    @Test void deathAfterSessionExitRetiresRetainedArrowAndPendingClaim() {
        Arrow arrow = shoot("duplex"); UUID token = bows.currentSession(player.getUniqueId()).orElseThrow();
        bows.clearSession(player.getUniqueId(), token, false); assertTrue(arrow.isValid());
        bows.hit(new ProjectileHitEvent(arrow, target(), null, null)); assertEquals(1, bows.pendingClaims());
        player.setHealth(0);
        assertTrue(bows.currentSession(player.getUniqueId()).isEmpty()); assertFalse(arrow.isValid());
        assertEquals(0, bows.capacityUsed()); assertEquals(0, bows.pendingClaims());
        server.getScheduler().performOneTick(); assertTrue(hits.isEmpty());
    }
}
