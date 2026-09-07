package com.kaveenk.onlydragons.paper.projectile;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
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

/**
 * Seeded service-class ammo tests with synthetic native event/debit setup. Literal inventory counts and random-source invocation counts prove one captured decision/refund; the stock bootstrap and real delayed Duplex/native consumption are separately exercised by quiver-ammo on Paper.
 */
class QuiverAmmoTest {
    ServerMock server; OnlyDragonsPlugin plugin; PlayerMock player,other; OwnedBowService bows; UUID generation;
    double sample; int draws;
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void setup() {
        server=MockBukkit.mock();plugin=MockBukkit.load(OnlyDragonsPlugin.class);player=server.addPlayer();other=server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);generation=UUID.randomUUID();
        bows=new OwnedBowService(plugin,plugin.equipment(),20,()->{draws++;return sample;});
        bows.openEncounter(generation,player.getWorld(),new BoundingBox(-1000,-1000,-1000,1000,1000,1000),new MechanicRevision("test","v1"));
        other.getInventory().setItem(9,new ItemStack(Material.ARROW,31));
    }
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void cleanup(){bows.close();MockBukkit.unmock();}
    /**
     * Uses the synthetic pre-debited survival launch setup without native Infinity.
     * @return mutable bow event for final-veto controls
     */
    EntityShootBowEvent shot() { return shot(false); }
    /**
     * Creates a real mock entity but injects bow/launch events and the pinned pre-event debit explicitly.
     * @param infinity whether native Infinity means no debit occurred
     * @return retained bow event for cancellation mutation
     */
    EntityShootBowEvent shot(boolean infinity) {
        var catalog=CalibrationLoadouts.compatibleRegistry();
        var item=catalog.edit(catalog.create("ordinary_v4"),Map.of("duplex",5,"infinite_quiver",10),List.of());
        var bow=new WeaponItemCodec(catalog).encode(item);if(infinity)bow.addEnchantment(org.bukkit.enchantments.Enchantment.INFINITY,1);player.getInventory().setItemInMainHand(bow);
        player.getInventory().remove(Material.ARROW);
        player.getInventory().setItem(9,new ItemStack(Material.ARROW,infinity?10:9)); // pinned native pre-event debit from 10
        player.getEyeLocation().getChunk().load();
        var arrow=player.getWorld().spawn(player.getEyeLocation(),Arrow.class);arrow.setShooter(player);
        var event=new EntityShootBowEvent(player,bow,new ItemStack(Material.ARROW),arrow,EquipmentSlot.HAND,1,true);
        bows.drawn(event);bows.launched(new ProjectileLaunchEvent(arrow));return event;
    }
    /**
     * Counts ordinary-arrow units across the shooter's inventory.
     * @return total units, independent of trace text
     */
    int ammo(){return player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum();}
    // MockBukkit lacks live-arrow isInBlock. Actual delayed emission is asserted by quiver-ammo on Paper.
    /**
     * Exercises direct session exit to settle a retained primary; avoids unsupported mock native flight and does not claim actual delayed child emission.
     */
    void settleRetainedPrimary() {
        bows.clearSession(player.getUniqueId(),bows.currentSession(player.getUniqueId()).orElseThrow(),false);
    }
    /**
     * Just-below-threshold save returns exactly one arrow, keeps the retained primary and leaves the other owner's 31 arrows unchanged.
     */
    @Test void acceptedSavedGroupRestoresOnlyOneAndDoesNotReroll() {
        sample=Math.nextDown(.5);shot();settleRetainedPrimary();
        assertEquals(10,ammo());assertEquals(2,draws);assertEquals(1,bows.projectiles().size());
        bows.endEncounter(generation);assertEquals(10,ammo());assertEquals(31,other.getInventory().getItem(9).getAmount());
    }
    /**
     * Exact 0.5 fails the save; final cancellation refunds once regardless of the sampled decision and later reset cannot mint another refund.
     */
    @Test void exactThresholdChargesOnceAndCancellationRestoresOnceForEitherDecision() {
        sample=.5;shot();settleRetainedPrimary();assertEquals(9,ammo());assertEquals(2,draws);
        for(double value:new double[]{0,.5}) {
            sample=value;var event=shot();event.setCancelled(true);settleRetainedPrimary();assertEquals(10,ammo());
        }
        bows.endEncounter(generation);assertEquals(10,ammo());
    }
    /**
     * Native Infinity supplies no debit token, so neither save nor cancellation can increase the initial arrow count.
     */
    @Test void nativeInfinityWithoutDebitCannotMintAcceptedOrCancelledRefunds() {
        sample=0;shot(true);settleRetainedPrimary();assertEquals(10,ammo());
        var veto=shot(true);veto.setCancelled(true);settleRetainedPrimary();assertEquals(10,ammo());
    }
    /**
     * Immediate synthetic session invalidation preserves a valid primary while settling one captured saving and ignoring repeated stale cleanup.
     */
    @Test void retainedPrimarySessionExitSettlesSaveExactlyOnce() {
        sample=0;var event=shot();var token=bows.currentSession(player.getUniqueId()).orElseThrow();
        bows.clearSession(player.getUniqueId(),token,false);assertEquals(10,ammo());assertEquals(2,draws);
        assertTrue(event.getProjectile().isValid());assertEquals(1,bows.projectiles().size());
        bows.clearSession(player.getUniqueId(),token,false);bows.endEncounter(generation);assertEquals(10,ammo());
    }
}
