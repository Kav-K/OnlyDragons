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

/**
 * Synthetic lifecycle and reentrant adapter regressions using a Cow projection backend. Literal HP/credit values and callback/task/registry state prove ordering and isolation. The fixture explicitly manufactures hit events; actual player flight, dragon death animation and native reward suppression require real Paper scenarios.
 */
class ManagedCombatBoundaryTest {
    ServerMock server; OnlyDragonsPlugin plugin; PlayerMock a,b; OwnedBowService bows;
    UUID id; Cow cow;
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void setup() { server=MockBukkit.mock(); plugin=MockBukkit.load(OnlyDragonsPlugin.class); a=server.addPlayer(); b=server.addPlayer(); bows=plugin.bows(); }
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    /**
     * Creates a Cow projection backend that reenters on lethal HP projection, checking that domain defeat/proc cleanup happened first.
     * @param hp initial domain health
     * @param fraction Ferocity child HP fraction; credit remains separately asserted
     */
    void open(double hp, double fraction) {
        cow=a.getWorld().spawn(a.getLocation(),Cow.class);
        var backend=new TargetBackend() {
            /** Returns the mock parent used for literal HP projection checks.
 * @return fixture cow
 */ public LivingEntity entity(){return cow;} /** Keeps these lifecycle oracles independent of Gravity classification.
 * @return false for the fixture cow
 */ public boolean airborne(){return false;}
            /** Asserts domain defeat and proc cleanup before reentering native termination, then mirrors the authoritative HP ratio.
 * @param t committed domain state
 */ public void synchronize(TargetState t){
                if (!t.alive()) {
                    assertEquals(ManagedCombatService.State.DEFEATED,plugin.combat().view(id).orElseThrow().state());
                    assertEquals(0,plugin.combat().view(id).orElseThrow().procs().queued());
                    plugin.combat().entityEnded(cow.getUniqueId());
                }
                cow.setHealth(10*t.currentHealth()/t.maxHealth());
            }
            /** Removes the immediate mock backend after the domain completion is frozen.
 * @param r immutable completion whose ordering was checked during projection
 */ public void defeated(EncounterResult r){close();} /** Observes immediate mock removal.
 * @return true when the fixture cow is invalid
 */ public boolean released(){return !cow.isValid();}
            /** Removes only the healthy fixture's native parent; the service owns accounting and task cleanup.
 */ public void close(){cow.remove();}
        };
        var profile=new CombatProfile(CombatProfile.calibration().mechanic(),CombatProfile.Mitigation.NONE,CombatProfile.Cap.NONE,fraction);
        id=plugin.combat().open(a.getUniqueId(),backend,new BoundingBox(-1000,-1000,-1000,1000,1000,1000),hp,0,"dummy",profile,Optional.empty(),()->0);
    }
    /**
     * Captures a chosen loadout through synthetic native bow/launch events.
     * @param p mock shooter
     * @param loadout trusted fixture definition
     * @return actual mock arrow with service-owned provenance
     */
    Arrow shoot(PlayerMock p,String loadout) {
        var item=plugin.equipment().createLoadout(loadout); p.getInventory().setItemInMainHand(item);
        Arrow arrow=p.getWorld().spawn(p.getEyeLocation(),Arrow.class); arrow.setShooter(p);
        bows.drawn(new EntityShootBowEvent(p,item,new ItemStack(Material.ARROW),arrow,EquipmentSlot.HAND,1,true));
        bows.launched(new ProjectileLaunchEvent(arrow));
        return arrow;
    }
    /**
     * Injects a physical boundary event against the fixture cow; does not simulate native collision geometry.
     * @param arrow registered primary
     */
    void impact(Arrow arrow) { bows.hit(new ProjectileHitEvent(arrow,cow,null,null)); }
    /**
     * A 150-credit hit against 50 HP freezes DEFEATED before backend reentry and retains one completion after reset.
     */
    @Test void physicalLethalPublishesBeforeReentrantDeathAndResetCannotDuplicate() {
        open(50,1); impact(shoot(a,"crit")); server.getScheduler().performOneTick();
        var view=plugin.combat().view(id).orElseThrow(); assertEquals(ManagedCombatService.State.DEFEATED,view.state());
        assertEquals(50,view.contributions().get(a.getUniqueId()).actualHealthDamage());
        assertEquals(150,view.contributions().get(a.getUniqueId()).contributionDamage());
        assertEquals(1,plugin.combat().completions().size()); assertEquals(0,plugin.combat().activeCount());
        plugin.combat().reset(a.getUniqueId()); assertEquals(1,plugin.combat().completions().size());
    }
    /**
     * A captured 100-Ferocity source survives a gear swap; reduced HP proc damage kills 120 HP while retaining 200 total credit.
     */
    @Test void reducedProcLethalAndImmutableCapturedSwapAgreeWithNativeProjection() {
        open(120,.25); var arrow=shoot(a,"ferocity_100"); impact(arrow);
        a.getInventory().setItemInMainHand(plugin.equipment().createLoadout("crit"));
        server.getScheduler().performOneTick(); assertEquals(20,plugin.combat().view(id).orElseThrow().target().currentHealth());
        server.getScheduler().performTicks(2);
        var r=plugin.combat().completions().getFirst(); assertEquals(120,r.participants().get(a.getUniqueId()).actualHealthDamage());
        assertEquals(200,r.participants().get(a.getUniqueId()).contributionDamage());
        assertEquals(2,r.completedOrdinal()); assertEquals(0,plugin.combat().activeCount());
    }
    /**
     * Final physical veto earns nothing, and two callbacks for one accepted arrow still apply only 100 damage.
     */
    @Test void finalCancellationAndDuplicateDeliveryEarnNothingExtra() {
        open(1000,1); var arrow=shoot(a,"ordinary"); var veto=new ProjectileHitEvent(arrow,cow,null,null);
        bows.hit(veto); veto.setCancelled(true); server.getScheduler().performOneTick();
        assertTrue(plugin.combat().view(id).orElseThrow().impacts().isEmpty());
        var accepted=shoot(b,"ordinary"); impact(accepted); impact(accepted); server.getScheduler().performOneTick();
        assertEquals(1,plugin.combat().view(id).orElseThrow().impacts().size());
        assertEquals(900,plugin.combat().view(id).orElseThrow().target().currentHealth());
        assertEquals(9,cow.getHealth());
    }
    /**
     * Old airborne damage can remain physical while the old token cannot reactivate procs or clear a replacement session.
     */
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
    /**
     * Only the control owner resets its target; a new generation clears old claims and admits both online participants.
     */
    @Test void resetPendingClaimsAndNewEncounterActivateAlreadyOnlinePlayers() {
        open(1000,1); impact(shoot(a,"ordinary")); UUID old=id;
        plugin.combat().reset(b.getUniqueId()); assertTrue(plugin.combat().owned(a.getUniqueId()).isPresent());
        plugin.combat().reset(a.getUniqueId()); open(1000,1); server.getScheduler().performOneTick();
        assertTrue(plugin.combat().view(id).orElseThrow().impacts().isEmpty()); assertEquals(2,plugin.combat().view(id).orElseThrow().procs().sessions());
        assertEquals(ManagedCombatService.State.TERMINATED,plugin.combat().view(old).orElseThrow().state());
        impact(shoot(a,"ordinary")); server.getScheduler().performOneTick(); assertEquals(900,plugin.combat().view(id).orElseThrow().target().currentHealth());
    }
    /**
     * Synthetic native damage dispatch is cancelled/zeroed; repeated combat close releases target, firing capacity and its task.
     */
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
    /**
     * Observer exceptions and forbidden reentry are isolated while both owners' claims still commit and diagnostics record each failure.
     */
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
    /**
     * Seventy-plus hits retain exact aggregate totals while diagnostic history caps at 64 and parent provenance clears after proc drain.
     */
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
    /**
     * Injected close failure cannot keep another fight/task alive or prevent receiver detachment and plugin cleanup.
     */
    @Test void failingBackendCleanupStillClosesOtherFightsAndDetachesReceiver() {
        open(1000,1);
        var w=b.getWorld();b.teleport(new Location(w,2500,100,0));
        Cow broken=w.spawn(b.getLocation(),Cow.class);
        var backend=new TargetBackend(){
            /** Returns this isolated failure-injection backend's parent.
 * @return fixture cow, distinct from the healthy fight
 */ public LivingEntity entity(){return broken;}/** Keeps these lifecycle oracles independent of Gravity classification.
 * @return false for the fixture cow
 */ public boolean airborne(){return false;}
            /** Leaves native HP untouched so the surrounding test isolates cleanup/release behavior.
 * @param t committed state intentionally not projected by this failure fixture
 */ public void synchronize(TargetState t){}/** Retains this controlled backend for the surrounding failure/cleanup oracle.
 * @param r frozen completion, deliberately not interpreted here
 */ public void defeated(EncounterResult r){}
            /** Reports whether the controlled failure backend's parent was removed.
 * @return true after native mock removal
 */ public boolean released(){return !broken.isValid();}
            /** Removes its entity then deliberately throws, proving another fight still receives cleanup.
 * @throws IllegalStateException after the controlled native removal
 */ public void close(){broken.remove();throw new IllegalStateException("cleanup fixture");}
        };
        plugin.combat().open(b.getUniqueId(),backend,new BoundingBox(2400,0,-100,2600,200,100),1000,0,"dummy",CombatProfile.calibration(),Optional.empty(),()->0);
        plugin.combat().close();
        assertFalse(cow.isValid());assertFalse(broken.isValid());assertEquals(0,plugin.combat().activeCount());
        assertEquals(0,plugin.combat().taskCount());assertEquals(0,bows.capacityUsed());
        assertDoesNotThrow(()->bows.receiver(hit->{}));
        assertTrue(plugin.combat().diagnostics().stream().anyMatch(d->d.contains("cleanup fixture")));
        plugin.onDisable();assertEquals(0,bows.taskCount());assertEquals(0,plugin.equipment().sessionCount());
    }

    /**
     * A backend that fails after initial projection terminates its own fight while another owner's claim still commits.
     */
    @Test void repeatedProjectionFailureCannotEscapeReceiverAndStarveOtherFight() {
        open(1000,1);var w=b.getWorld();b.teleport(new Location(w,2500,100,0));Cow broken=w.spawn(b.getLocation(),Cow.class);
        int[] calls={0};var backend=new TargetBackend(){
            /** Returns this isolated failure-injection backend's parent.
 * @return fixture cow, distinct from the healthy fight
 */ public LivingEntity entity(){return broken;}/** Keeps these lifecycle oracles independent of Gravity classification.
 * @return false for the fixture cow
 */ public boolean airborne(){return false;}
            /** Accepts initial admission but fails later projection to exercise receiver isolation.
 * @param t committed state
 * @throws IllegalStateException after the first projection
 */ public void synchronize(TargetState t){if(calls[0]++>0)throw new IllegalStateException("projection fixture");}
            /** Retains this controlled backend for the surrounding failure/cleanup oracle.
 * @param r frozen completion, deliberately not interpreted here
 */ public void defeated(EncounterResult r){}/** Reports whether the controlled failure backend's parent was removed.
 * @return true after native mock removal
 */ public boolean released(){return !broken.isValid();}/** Removes only this isolated failure backend's native parent.
 */ public void close(){broken.remove();}
        };
        UUID other=plugin.combat().open(b.getUniqueId(),backend,new BoundingBox(2400,0,-100,2600,200,100),1000,0,"dummy",CombatProfile.calibration(),Optional.empty(),()->0);
        int[] observed={0};plugin.combat().observeSettled(hit->observed[0]++);
        bows.hit(new ProjectileHitEvent(shoot(b,"ordinary"),broken,null,null));impact(shoot(a,"ordinary"));
        assertDoesNotThrow(()->server.getScheduler().performOneTick());
        assertEquals(2,observed[0]);assertEquals(900,plugin.combat().view(id).orElseThrow().target().currentHealth());
        assertEquals(1,plugin.combat().view(other).orElseThrow().acceptedImpacts());assertEquals(ManagedCombatService.State.TERMINATED,plugin.combat().view(other).orElseThrow().state());
        assertFalse(broken.isValid());assertEquals(0,bows.pendingClaims());
    }
    /**
     * A throwing post-defeat release probe preserves frozen completion and cannot stop another fight's queued Ferocity tick.
     */
    @Test void terminalReleaseProbeFailureDoesNotStarveOtherFightProcTick() {
        open(1000,1);var w=b.getWorld();b.teleport(new Location(w,2500,100,0));Cow broken=w.spawn(b.getLocation(),Cow.class);
        boolean[] fail={false};var backend=new TargetBackend(){
            /** Returns this isolated failure-injection backend's parent.
 * @return fixture cow, distinct from the healthy fight
 */ public LivingEntity entity(){return broken;}/** Keeps these lifecycle oracles independent of Gravity classification.
 * @return false for the fixture cow
 */ public boolean airborne(){return false;}
            /** Leaves native HP untouched so the surrounding test isolates cleanup/release behavior.
 * @param t committed state intentionally not projected by this failure fixture
 */ public void synchronize(TargetState t){}/** Retains this controlled backend for the surrounding failure/cleanup oracle.
 * @param r frozen completion, deliberately not interpreted here
 */ public void defeated(EncounterResult r){}
            /** Injects failure only after a frozen completion exists, allowing the test to check another fight's proc progress.
 * @return false before failure activation
 * @throws IllegalStateException once the test activates the failing release probe
 */ public boolean released(){if(fail[0])throw new IllegalStateException("release probe fixture");return false;}
            /** Removes only this isolated failure backend's native parent.
 */ public void close(){broken.remove();}
        };
        UUID other=plugin.combat().open(b.getUniqueId(),backend,new BoundingBox(2400,0,-100,2600,200,100),50,0,"dummy",CombatProfile.calibration(),Optional.empty(),()->0);
        bows.hit(new ProjectileHitEvent(shoot(b,"ordinary"),broken,null,null));impact(shoot(a,"ferocity_100"));server.getScheduler().performOneTick();
        var result=plugin.combat().view(other).orElseThrow().completion().orElseThrow();fail[0]=true;
        server.getScheduler().performTicks(2);assertEquals(800,plugin.combat().view(id).orElseThrow().target().currentHealth());
        assertSame(result,plugin.combat().view(other).orElseThrow().completion().orElseThrow());assertFalse(broken.isValid());
        assertEquals(1,plugin.combat().completions().size());assertEquals(1,plugin.combat().activeCount());
    }

}
