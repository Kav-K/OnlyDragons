package com.kaveenk.onlydragons.paper.projectile;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.paper.encounter.*;
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

/** Synthetic lifecycle/reentrant event regressions; physical input proof is the real-Paper scenario. */
class ManagedCombatBoundaryTest {
    ServerMock server; OnlyDragonsPlugin plugin; PlayerMock a,b; OwnedBowService bows;
    UUID id; Cow cow;
    @BeforeEach void setup() { server=MockBukkit.mock(); plugin=MockBukkit.load(OnlyDragonsPlugin.class); a=server.addPlayer(); b=server.addPlayer(); bows=plugin.bows(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    void open(double hp, double fraction) {
        cow=a.getWorld().spawn(a.getLocation(),Cow.class);
        var backend=new TargetBackend() {
            public LivingEntity entity(){return cow;} public boolean airborne(){return false;}
            public void synchronize(TargetState t){
                if (!t.alive()) {
                    assertEquals(ManagedCombatService.State.DEFEATED,plugin.combat().view(id).orElseThrow().state());
                    assertEquals(0,plugin.combat().view(id).orElseThrow().procs().queued());
                    plugin.combat().entityEnded(cow.getUniqueId());
                }
                cow.setHealth(10*t.currentHealth()/t.maxHealth());
            }
            public void defeated(EncounterResult r){close();} public boolean released(){return !cow.isValid();}
            public void close(){cow.remove();}
        };
        var profile=new CombatProfile(CombatProfile.calibration().mechanic(),CombatProfile.Mitigation.NONE,CombatProfile.Cap.NONE,fraction);
        id=plugin.combat().open(a.getUniqueId(),backend,new BoundingBox(-1000,-1000,-1000,1000,1000,1000),hp,0,"dummy",profile,Optional.empty(),()->0);
    }
    Arrow shoot(PlayerMock p,String loadout) {
        var item=plugin.equipment().createLoadout(loadout); p.getInventory().setItemInMainHand(item);
        Arrow arrow=p.getWorld().spawn(p.getEyeLocation(),Arrow.class); arrow.setShooter(p);
        bows.drawn(new EntityShootBowEvent(p,item,new ItemStack(Material.ARROW),arrow,EquipmentSlot.HAND,1,true));
        bows.launched(new ProjectileLaunchEvent(arrow));
        return arrow;
    }
    void impact(Arrow arrow) { bows.hit(new ProjectileHitEvent(arrow,cow,null,null)); }
    @Test void physicalLethalPublishesBeforeReentrantDeathAndResetCannotDuplicate() {
        open(50,1); impact(shoot(a,"crit")); server.getScheduler().performOneTick();
        var view=plugin.combat().view(id).orElseThrow(); assertEquals(ManagedCombatService.State.DEFEATED,view.state());
        assertEquals(50,view.contributions().get(a.getUniqueId()).actualHealthDamage());
        assertEquals(150,view.contributions().get(a.getUniqueId()).contributionDamage());
        assertEquals(1,plugin.combat().completions().size()); assertEquals(0,plugin.combat().activeCount());
        plugin.combat().reset(a.getUniqueId()); assertEquals(1,plugin.combat().completions().size());
    }
    @Test void reducedProcLethalAndImmutableCapturedSwapAgreeWithNativeProjection() {
        open(120,.25); var arrow=shoot(a,"ferocity_100"); impact(arrow);
        a.getInventory().setItemInMainHand(plugin.equipment().createLoadout("crit"));
        server.getScheduler().performOneTick(); assertEquals(20,plugin.combat().view(id).orElseThrow().target().currentHealth());
        server.getScheduler().performTicks(2);
        var r=plugin.combat().completions().getFirst(); assertEquals(120,r.participants().get(a.getUniqueId()).actualHealthDamage());
        assertEquals(200,r.participants().get(a.getUniqueId()).contributionDamage());
        assertEquals(2,r.completedOrdinal()); assertEquals(0,plugin.combat().activeCount());
    }
    @Test void finalCancellationAndDuplicateDeliveryEarnNothingExtra() {
        open(1000,1); var arrow=shoot(a,"ordinary"); var veto=new ProjectileHitEvent(arrow,cow,null,null);
        bows.hit(veto); veto.setCancelled(true); server.getScheduler().performOneTick();
        assertTrue(plugin.combat().view(id).orElseThrow().impacts().isEmpty());
        var accepted=shoot(b,"ordinary"); impact(accepted); impact(accepted); server.getScheduler().performOneTick();
        assertEquals(1,plugin.combat().view(id).orElseThrow().impacts().size());
        assertEquals(900,plugin.combat().view(id).orElseThrow().target().currentHealth());
        assertEquals(9,cow.getHealth());
    }
    @Test void queuedSessionLossClearsProcsAndOldAirborneTokenCannotReactivateThem() {
        open(1000,0); impact(shoot(a,"ferocity_100")); server.getScheduler().performOneTick();
        assertEquals(1,plugin.combat().view(id).orElseThrow().procs().queued());
        var oldArrow=shoot(a,"ferocity_100"); UUID old=bows.currentSession(a.getUniqueId()).orElseThrow();
        bows.clearSession(a.getUniqueId(),old,false); bows.activate(a);
        impact(oldArrow); server.getScheduler().performOneTick();
        var explanation=plugin.combat().last(a.getUniqueId()).orElseThrow();
        assertEquals(com.kaveenk.onlydragons.application.proc.ProcCoordinator.Admission.INACTIVE_SESSION,explanation.admission());
        server.getScheduler().performTicks(3);
        var v=plugin.combat().view(id).orElseThrow(); assertEquals(2,v.impacts().size()); assertEquals(800,v.target().currentHealth());
        assertEquals(0,v.procs().queued()); assertEquals(0,v.procs().tempoStates());
        bows.clearSession(a.getUniqueId(),old,true); assertTrue(bows.currentSession(a.getUniqueId()).isPresent());
    }
    @Test void resetPendingClaimsAndNewEncounterActivateAlreadyOnlinePlayers() {
        open(1000,1); impact(shoot(a,"ordinary")); UUID old=id;
        plugin.combat().reset(b.getUniqueId()); assertTrue(plugin.combat().owned(a.getUniqueId()).isPresent());
        plugin.combat().reset(a.getUniqueId()); open(1000,1); server.getScheduler().performOneTick();
        assertTrue(plugin.combat().view(id).orElseThrow().impacts().isEmpty()); assertEquals(2,plugin.combat().view(id).orElseThrow().procs().sessions());
        assertEquals(ManagedCombatService.State.TERMINATED,plugin.combat().view(old).orElseThrow().state());
        impact(shoot(a,"ordinary")); server.getScheduler().performOneTick(); assertEquals(900,plugin.combat().view(id).orElseThrow().target().currentHealth());
    }
    @Test void nativeDamageProtectionAndDisableCleanupRemainCentralized() {
        open(1000,1);
        // MockBukkit damage() subtracts directly; dispatch the synthetic boundary separately.
        var damage = new EntityDamageByEntityEvent(b,cow,EntityDamageEvent.DamageCause.ENTITY_ATTACK,4);
        server.getPluginManager().callEvent(damage); assertTrue(damage.isCancelled()); assertEquals(0,damage.getDamage());
        assertEquals(10,cow.getHealth());
        impact(shoot(a,"ferocity_100")); server.getScheduler().performOneTick();
        plugin.combat().close(); plugin.combat().close();
        assertFalse(cow.isValid()); assertEquals(0,bows.capacityUsed()); assertEquals(0,plugin.combat().taskCount()); assertEquals(0,plugin.combat().activeCount());
    }
    @Test void throwingAndMutatingObserversCannotInterruptTwoSettledClaims() {
        open(1000,1);
        plugin.combat().observeSettled(hit -> { throw new IllegalArgumentException("observer fixture"); });
        plugin.combat().observeSettled(hit -> plugin.combat().reset(a.getUniqueId()));
        int[] observed={0}; plugin.combat().observeSettled(hit -> observed[0]++);
        impact(shoot(a,"ordinary")); impact(shoot(b,"ordinary")); server.getScheduler().performOneTick();
        assertEquals(2,observed[0]); assertEquals(2,plugin.combat().view(id).orElseThrow().acceptedImpacts());
        assertEquals(800,plugin.combat().view(id).orElseThrow().target().currentHealth());
        assertEquals(0,bows.capacityUsed()); assertEquals(4,plugin.combat().diagnostics().size());
        assertTrue(plugin.combat().owned(a.getUniqueId()).isPresent());
    }
    @Test void retainedViewsAreBoundedAndProcProvenanceIsReleasedAfterDrain() {
        open(1_000_000,0);
        for(int i=0;i<70;i++){ impact(shoot(a,"ordinary")); server.getScheduler().performOneTick(); }
        var live=plugin.combat().view(id).orElseThrow();
        assertEquals(70,live.acceptedImpacts()); assertEquals(64,live.impacts().size()); assertEquals(6,live.omittedImpacts());
        assertEquals(7000,live.contributions().get(a.getUniqueId()).contributionDamage()); assertEquals(0,live.retainedParents());
        impact(shoot(a,"ferocity_100"));server.getScheduler().performOneTick();
        assertEquals(1,plugin.combat().view(id).orElseThrow().retainedParents());
        server.getScheduler().performTicks(2);assertEquals(0,plugin.combat().view(id).orElseThrow().retainedParents());
        plugin.combat().reset(a.getUniqueId());var frozen=plugin.combat().view(id).orElseThrow();
        assertEquals(64,frozen.impacts().size());assertEquals(72,frozen.acceptedImpacts());assertEquals(8,frozen.omittedImpacts());
    }
    @Test void failingBackendCleanupStillClosesOtherFightsAndDetachesReceiver() {
        open(1000,1);
        var w=b.getWorld();b.teleport(new Location(w,2500,100,0));
        Cow broken=w.spawn(b.getLocation(),Cow.class);
        var backend=new TargetBackend(){
            public LivingEntity entity(){return broken;}public boolean airborne(){return false;}
            public void synchronize(TargetState t){}public void defeated(EncounterResult r){}
            public boolean released(){return !broken.isValid();}
            public void close(){broken.remove();throw new IllegalStateException("cleanup fixture");}
        };
        plugin.combat().open(b.getUniqueId(),backend,new BoundingBox(2400,0,-100,2600,200,100),1000,0,"dummy",CombatProfile.calibration(),Optional.empty(),()->0);
        plugin.combat().close();
        assertFalse(cow.isValid());assertFalse(broken.isValid());assertEquals(0,plugin.combat().activeCount());
        assertEquals(0,plugin.combat().taskCount());assertEquals(0,bows.capacityUsed());
        assertDoesNotThrow(()->bows.receiver(hit->{}));
        assertTrue(plugin.combat().diagnostics().stream().anyMatch(d->d.contains("cleanup fixture")));
        plugin.onDisable();assertEquals(0,bows.taskCount());assertEquals(0,plugin.equipment().sessionCount());
    }

    @Test void repeatedProjectionFailureCannotEscapeReceiverAndStarveOtherFight() {
        open(1000,1);var w=b.getWorld();b.teleport(new Location(w,2500,100,0));Cow broken=w.spawn(b.getLocation(),Cow.class);
        int[] calls={0};var backend=new TargetBackend(){
            public LivingEntity entity(){return broken;}public boolean airborne(){return false;}
            public void synchronize(TargetState t){if(calls[0]++>0)throw new IllegalStateException("projection fixture");}
            public void defeated(EncounterResult r){}public boolean released(){return !broken.isValid();}public void close(){broken.remove();}
        };
        UUID other=plugin.combat().open(b.getUniqueId(),backend,new BoundingBox(2400,0,-100,2600,200,100),1000,0,"dummy",CombatProfile.calibration(),Optional.empty(),()->0);
        int[] observed={0};plugin.combat().observeSettled(hit->observed[0]++);
        bows.hit(new ProjectileHitEvent(shoot(b,"ordinary"),broken,null,null));impact(shoot(a,"ordinary"));
        assertDoesNotThrow(()->server.getScheduler().performOneTick());
        assertEquals(2,observed[0]);assertEquals(900,plugin.combat().view(id).orElseThrow().target().currentHealth());
        assertEquals(1,plugin.combat().view(other).orElseThrow().acceptedImpacts());assertEquals(ManagedCombatService.State.TERMINATED,plugin.combat().view(other).orElseThrow().state());
        assertFalse(broken.isValid());assertEquals(0,bows.pendingClaims());
    }
    @Test void terminalReleaseProbeFailureDoesNotStarveOtherFightProcTick() {
        open(1000,1);var w=b.getWorld();b.teleport(new Location(w,2500,100,0));Cow broken=w.spawn(b.getLocation(),Cow.class);
        boolean[] fail={false};var backend=new TargetBackend(){
            public LivingEntity entity(){return broken;}public boolean airborne(){return false;}
            public void synchronize(TargetState t){}public void defeated(EncounterResult r){}
            public boolean released(){if(fail[0])throw new IllegalStateException("release probe fixture");return false;}
            public void close(){broken.remove();}
        };
        UUID other=plugin.combat().open(b.getUniqueId(),backend,new BoundingBox(2400,0,-100,2600,200,100),50,0,"dummy",CombatProfile.calibration(),Optional.empty(),()->0);
        bows.hit(new ProjectileHitEvent(shoot(b,"ordinary"),broken,null,null));impact(shoot(a,"ferocity_100"));server.getScheduler().performOneTick();
        var result=plugin.combat().view(other).orElseThrow().completion().orElseThrow();fail[0]=true;
        server.getScheduler().performTicks(2);assertEquals(800,plugin.combat().view(id).orElseThrow().target().currentHealth());
        assertSame(result,plugin.combat().view(other).orElseThrow().completion().orElseThrow());assertFalse(broken.isValid());
        assertEquals(1,plugin.combat().completions().size());assertEquals(1,plugin.combat().activeCount());
    }

}
