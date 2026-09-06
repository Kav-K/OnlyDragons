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
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic event boundary tests; real input and collision proof belongs to owned-firing. */
class OwnedBowBoundaryTest {
    ServerMock server;
    OnlyDragonsPlugin plugin;
    PlayerMock player;
    OwnedBowService bows;
    UUID encounter;
    EntityShootBowEvent lastBowEvent;
    List<SettledHit> hits = new ArrayList<>();
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.load(OnlyDragonsPlugin.class);
        player = server.addPlayer(); player.setGameMode(GameMode.SURVIVAL);
        bows = plugin.bows(); encounter = UUID.randomUUID();
        bows.openEncounter(encounter, player.getWorld(), new BoundingBox(-1000, -1000, -1000, 1000, 1000, 1000), new MechanicRevision("calibration", "v1"));
        bows.receiver(hits::add);
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
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
    Cow target() {
        Cow cow = player.getWorld().spawn(player.getLocation(), Cow.class);
        bows.registerTarget(encounter, UUID.randomUUID(), cow); return cow;
    }
    @Test void finalPhysicalVetoRetainsOneClaimAndRetiresBeforeReceiver() {
        Arrow arrow = shoot("ordinary"); Cow cow = target();
        ProjectileHitEvent event = new ProjectileHitEvent(arrow, cow, null, null); bows.hit(event);
        bows.hit(new ProjectileHitEvent(arrow, cow, null, null)); event.setCancelled(true);
        assertEquals(1, bows.pendingClaims()); server.getScheduler().performOneTick();
        assertEquals(1, hits.size()); assertEquals(SettledHit.Rejection.PHYSICAL_VETO, hits.getFirst().rejection().orElseThrow());
        assertTrue(bows.projectile(arrow.getUniqueId()).isEmpty()); assertFalse(arrow.isValid());
        bows.hit(new ProjectileHitEvent(arrow, cow, null, null)); assertEquals(0, bows.pendingClaims());
    }
    @Test void targetReplacementBeforeSettlementRejectsCapturedRegistration() {
        Arrow arrow = shoot("ordinary"); Cow cow = target(); bows.hit(new ProjectileHitEvent(arrow, cow, null, null));
        bows.unregisterTarget(cow.getUniqueId()); bows.registerTarget(encounter, UUID.randomUUID(), cow);
        server.getScheduler().performOneTick();
        assertEquals(1, hits.size()); assertEquals(SettledHit.Rejection.TARGET_CHANGED, hits.getFirst().rejection().orElseThrow());
    }
    @Test void immediateSessionEndKeepsAcceptedPrimaryButCancelsChildAndRetainsDebit() {
        Arrow arrow = shoot("duplex"); UUID token = bows.currentSession(player.getUniqueId()).orElseThrow();
        assertEquals(1, bows.reservedCapacity()); bows.clearSession(player.getUniqueId(), token, false);
        assertTrue(arrow.isValid()); assertEquals(1, bows.projectiles().size()); assertEquals(0, bows.reservedCapacity());
        assertEquals(9, player.getInventory().getItem(9).getAmount()); assertEquals(0, bows.pendingGroups());
        bows.hit(new ProjectileHitEvent(arrow, target(), null, null)); server.getScheduler().performOneTick();
        assertEquals(1, hits.size()); assertTrue(hits.getFirst().accepted());
        assertEquals(token, hits.getFirst().projectile().sessionToken()); assertEquals(0, bows.capacityUsed());
    }
    @Test void resetDuringPendingClaimCannotDeliverToLaterGeneration() {
        Arrow arrow = shoot("ordinary"); Cow cow = target(); bows.hit(new ProjectileHitEvent(arrow, cow, null, null));
        bows.endEncounter(encounter); server.getScheduler().performOneTick();
        assertTrue(hits.isEmpty()); assertFalse(arrow.isValid()); assertEquals(0, bows.capacityUsed()); assertEquals(0, bows.pendingClaims());
    }
    @Test void finalBowVetoRefundsWholeGroupExactlyOnce() {
        Arrow arrow = shoot("duplex"); lastBowEvent.setCancelled(true);
        server.getScheduler().performOneTick();
        assertFalse(arrow.isValid()); assertEquals(0, bows.capacityUsed()); assertEquals(0, bows.pendingGroups());
        assertEquals(10, player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum());
        bows.endEncounter(encounter); server.getScheduler().performOneTick();
        assertEquals(10, player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum()); assertTrue(hits.isEmpty());
    }
    @Test void nativeLaunchVetoRetiresPrimaryAndReservedDuplexWithoutAClaim() {
        Arrow arrow = shoot("duplex"); var launch = new ProjectileLaunchEvent(arrow);
        bows.launched(launch); launch.setCancelled(true); server.getScheduler().performOneTick();
        assertFalse(arrow.isValid()); assertEquals(0, bows.reservedCapacity()); assertEquals(0, bows.pendingGroups());
        assertEquals(10, player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum()); assertTrue(hits.isEmpty());
    }
    @Test void unrelatedProjectileIsNotSuppressedClaimedOrRemoved() {
        Arrow arrow = player.getWorld().spawn(player.getEyeLocation(), Arrow.class); arrow.setDamage(4); arrow.setCritical(true);
        bows.launched(new ProjectileLaunchEvent(arrow)); bows.hit(new ProjectileHitEvent(arrow, target(), null, null));
        assertEquals(4, arrow.getDamage()); assertTrue(arrow.isCritical()); assertTrue(arrow.isValid());
        assertEquals(0, bows.pendingClaims()); bows.endEncounter(encounter); assertTrue(arrow.isValid());
    }
}
