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

class QuiverAmmoTest {
    ServerMock server; OnlyDragonsPlugin plugin; PlayerMock player,other; OwnedBowService bows; UUID generation;
    double sample; int draws;
    @BeforeEach void setup() {
        server=MockBukkit.mock();plugin=MockBukkit.load(OnlyDragonsPlugin.class);player=server.addPlayer();other=server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);generation=UUID.randomUUID();
        bows=new OwnedBowService(plugin,plugin.equipment(),20,()->{draws++;return sample;});
        bows.openEncounter(generation,player.getWorld(),new BoundingBox(-1000,-1000,-1000,1000,1000,1000),new MechanicRevision("test","v1"));
        other.getInventory().setItem(9,new ItemStack(Material.ARROW,31));
    }
    @AfterEach void cleanup(){bows.close();MockBukkit.unmock();}
    EntityShootBowEvent shot() { return shot(false); }
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
    int ammo(){return player.getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum();}
    // MockBukkit lacks live-arrow isInBlock. Actual delayed emission is asserted by quiver-ammo on Paper.
    void settleRetainedPrimary() {
        bows.clearSession(player.getUniqueId(),bows.currentSession(player.getUniqueId()).orElseThrow(),false);
    }
    @Test void acceptedSavedGroupRestoresOnlyOneAndDoesNotReroll() {
        sample=Math.nextDown(.5);shot();settleRetainedPrimary();
        assertEquals(10,ammo());assertEquals(2,draws);assertEquals(1,bows.projectiles().size());
        bows.endEncounter(generation);assertEquals(10,ammo());assertEquals(31,other.getInventory().getItem(9).getAmount());
    }
    @Test void exactThresholdChargesOnceAndCancellationRestoresOnceForEitherDecision() {
        sample=.5;shot();settleRetainedPrimary();assertEquals(9,ammo());assertEquals(2,draws);
        for(double value:new double[]{0,.5}) {
            sample=value;var event=shot();event.setCancelled(true);settleRetainedPrimary();assertEquals(10,ammo());
        }
        bows.endEncounter(generation);assertEquals(10,ammo());
    }
    @Test void nativeInfinityWithoutDebitCannotMintAcceptedOrCancelledRefunds() {
        sample=0;shot(true);settleRetainedPrimary();assertEquals(10,ammo());
        var veto=shot(true);veto.setCancelled(true);settleRetainedPrimary();assertEquals(10,ammo());
    }
    @Test void retainedPrimarySessionExitSettlesSaveExactlyOnce() {
        sample=0;var event=shot();var token=bows.currentSession(player.getUniqueId()).orElseThrow();
        bows.clearSession(player.getUniqueId(),token,false);assertEquals(10,ammo());assertEquals(2,draws);
        assertTrue(event.getProjectile().isValid());assertEquals(1,bows.projectiles().size());
        bows.clearSession(player.getUniqueId(),token,false);bows.endEncounter(generation);assertEquals(10,ammo());
    }
}
