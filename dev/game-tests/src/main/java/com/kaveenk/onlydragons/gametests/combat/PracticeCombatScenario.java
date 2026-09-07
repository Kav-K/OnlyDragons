package com.kaveenk.onlydragons.gametests.combat;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.encounter.EncounterResult;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Exercises shared practice accounting with two protocol actors and native bow releases.
 * Literal HP/credit totals are compared with the production receiver and separately
 * watched native damage. Equipment, positioning, permissions and intentional native
 * damage/veto pressure are labelled server setup, not client inventory interactions.
 * All steps/listeners run on the server thread; context cleanup retires owned fights
 * and closes the settled-hit subscription even if a stage fails.
 */
public final class PracticeCombatScenario implements Scenario, Listener {
    ScenarioContext c; PlayerFixture players; DamageObservationProbe probe; ManagedCombatService combat;
    UUID encounter; LivingEntity target; boolean veto, nativeDamage, swap;
    final Map<UUID,Integer> releases=new HashMap<>(), commands=new HashMap<>();
    final Map<UUID,UUID> nativeOwners=new LinkedHashMap<>();
    final Map<UUID,Integer> collisions=new LinkedHashMap<>();
    final List<SettledHit> settlements=new ArrayList<>();
    int deaths; boolean completionAtDeath;
    /**
     * Installs native observations and cleanup before awaiting the declared actors.
     * @param context run-owned scheduler, entities, assertions and cleanup registry
     * @throws Exception if fixture initialization cannot establish the declared player plan
     */
    @Override public void start(ScenarioContext context) throws Exception {
        c=context; c.mechanicRevision("practice-combat-v1"); combat=c.production().combat();
        players=new PlayerFixture(c); probe=new DamageObservationProbe(c); c.listen(this);
        var observation=combat.observeSettled(settlements::add);
        c.cleanup("practice-combat",()->{ combat.reset(players.identity("alpha")); combat.reset(players.identity("beta")); observation.close(); });
        players.await("two practice players",300,players::allOnline,this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    /** Prepares survival actors and ticking chunks, then tests permission denial before connected kit/stats commands. */
    void setup() {
        World w=players.player("alpha").getWorld();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.tickChunk(w.getChunkAt(x,z));
        for(String actor:List.of("alpha","beta")) {
            Player p=players.player(actor);p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);p.setCollidable(false);
            p.getInventory().clear();p.getInventory().setHeldItemSlot(0);position(actor);
        }
        c.check("two_real_actors",true,!players.identity("alpha").equals(players.identity("beta")));
        command("alpha","denied",()->{
            c.check("non_admin_kit_denied",true,players.player("alpha").getInventory().isEmpty());
            players.permission("alpha","onlydragons.practice",true);
            command("alpha","kit",()->{
                c.check("production_kit_and_ammo",true,players.player("alpha").getInventory().getItem(0).getType()==Material.BOW
                        && players.player("alpha").getInventory().getItem(1).getAmount()==64);
                command("alpha","stats",this::nativeControl);
            });
        });
    }
    /** Proves an unmanaged bow can remove native cow HP before admitting the managed target and owner-isolated reset controls. */
    void nativeControl() {
        target=c.own(players.player("alpha").getWorld().spawn(new Location(players.player("alpha").getWorld(),.5,100,8.5),Cow.class));
        target.setAI(false);target.setGravity(false);probe.watch(target);double hp=target.getHealth();
        players.setupItem("beta",0,new ItemStack(Material.BOW));players.setupItem("beta",9,new ItemStack(Material.ARROW,64));
        draw("beta","native",()->c.later(12,()->{
            c.check("unmanaged_native_positive_control",true,target.getHealth()<hp && probe.events(players.identity("beta")).stream().anyMatch(e->!e.cancelled()&&e.finalDamage()>0));
            target.remove();command("alpha","open",()->{
                capture();c.check("online_proc_sessions_activated",2,view().procs().sessions());
                command("beta","denied-reset",()->{
                    c.check("non_admin_reset_denied",true,combat.owned(players.identity("alpha")).isPresent());
                    players.permission("beta","onlydragons.practice",true);
                    command("beta","other-reset",()->{c.check("owner_reset_isolation",true,combat.owned(players.identity("alpha")).isPresent()); ordinary();});
                });
            });
        }));
    }
    /** Checks independent literal ordinary/critical totals through real releases and the production last-hit command. */
    void ordinary() {
        kit("alpha","ordinary");draw("alpha","ordinary",()->settled(1,()->{
            totals("ordinary",900,100,0,1);command("alpha","last",()->{
                kit("beta","crit");draw("beta","crit",()->settled(2,()->{totals("critical",750,100,150,2);command("beta","last-crit",()->ferocity(0));}));
            });
        }));
    }
    /** Runs the seeded Ferocity-25 sequence, including a launch-event equipment swap, against fixed cumulative HP and credit totals. */
    void ferocity(int index) {
        if(index==4){cancel();return;}
        kit("alpha","ferocity_25");swap=index==0;
        draw("alpha","fero"+index,()->settled(3+index,()->c.later(4,()->{
            swap=false;
            double alpha=300+index*100;
            totals("ferocity25_"+index,1000-alpha-150,alpha,150,4+index);
            c.check("captured_ferocity25_"+index,25.0,view().impacts().stream().filter(r->r.kind()==DamageResult.Kind.PHYSICAL&&r.ownerId().equals(players.identity("alpha"))).reduce((a,b)->b).orElseThrow().effectiveFerocity());
            ferocity(index+1);
        })));
    }
    /** Pairs a physical veto with explicit native-damage pressure, then verifies suppression and one later lethal managed hit. */
    void cancel() {
        kit("alpha","ordinary");veto=true;int before=settlements.size();
        draw("alpha","veto",()->settled(before+1,()->{
            veto=false;totals("cancelled",250,600,150,7);
            c.check("cancelled_production_claim",true,settlements.getLast().rejection().equals(Optional.of(SettledHit.Rejection.PHYSICAL_VETO)));
            nativeDamage=true;kit("beta","ordinary");int n=settlements.size();
            draw("beta","guard",()->settled(n+1,()->{
                nativeDamage=false;totals("native_plus_managed",150,600,250,8);
                var hit=settlements.getLast();
                c.check("native_guard_event_and_identity",true,probe.events().stream().anyMatch(e->hit.projectile().shot().projectileId().equals(e.directDamager())&&e.cancelled()&&e.settledDamage()==0&&e.initialDamage()>0));
                kit("alpha","crit");int count=settlements.size();draw("alpha","lethal",()->settled(count+1,this::completed));
            }));
        }));
    }
    /** Checks frozen lethal totals and completion-before-native-death ordering, then ensures later ticks cannot republish the result. */
    void completed() {
        var v=view();var r=v.completion().orElseThrow();
        c.check("physical_lethal_one_frozen_completion",true,combat.completions().size()==1&&v.state()==ManagedCombatService.State.DEFEATED&&r.completedOrdinal()==9);
        c.check("physical_lethal_totals",true,r.participants().get(players.identity("alpha")).contributionDamage()==750&&r.participants().get(players.identity("beta")).contributionDamage()==250);
        c.check("completion_precedes_native_death",true,completionAtDeath&&deaths==1);
        c.check("terminal_queue_and_native_cleanup",true,v.procs().queued()==0&&combat.activeCount()==0&&!target.isValid());
        c.later(5,()->{
            c.check("late_tick_does_not_republish",true,combat.completions().size()==1&&view().completion().orElseThrow().equals(r));
            command("alpha","reduced",()->{capture();volley();});
        });
    }
    /** Releases two observed raised bows together and checks same-tick commits, reduced proc HP and immutable child-owner provenance. */
    void volley() {
        for(String actor:List.of("alpha","beta")){
            kit(actor,actor.equals("alpha")?"ferocity_100":"crit");
            if(actor.equals("beta"))c.production().equipment().bonus(players.player(actor),com.kaveenk.onlydragons.domain.stats.StatKey.FEROCITY,100);
            position(actor);players.request(actor,"volley-use");
        }
        players.await("both volley bows",60,()->players.player("alpha").isHandRaised()&&players.player("beta").isHandRaised(),()->c.later(22,()->{
            int count=settlements.size();players.request("alpha","volley-release");players.request("beta","volley-release");
            settled(count+2,()->c.later(5,()->{
                var hits=settlements.subList(count,settlements.size());
                c.check("simultaneous_two_owner_physical_commits",true,hits.size()==2&&hits.stream().map(SettledHit::collisionTick).distinct().count()==1&&hits.stream().map(h->h.projectile().shot().ownerId()).distinct().count()==2);
                totals("reduced_two_owner",187.5,200,300,4);
                c.check("reduced_hp_credit_separate",true,view().contributions().get(players.identity("alpha")).actualHealthDamage()==125&&view().contributions().get(players.identity("beta")).actualHealthDamage()==187.5);
                c.check("asymmetric_child_owner_parent_shot",true,view().impacts().stream().filter(r->r.kind()==DamageResult.Kind.FEROCITY).allMatch(child->{
                    var parent=view().impacts().stream().filter(r->r.impactId().equals(child.parentImpactId().orElseThrow())).findFirst().orElseThrow();
                    double expected=parent.ownerId().equals(players.identity("alpha"))?100:150;
                    return child.ownerId().equals(parent.ownerId())&&child.shotId().equals(parent.shotId())&&child.origin().equals(parent.origin())
                            &&child.crit()==parent.crit()&&child.amounts().contributionDamage()==expected&&child.amounts().actualHealthDamage()==expected*.25
                            &&nativeOwners.get(parent.origin().projectileId()).equals(parent.ownerId());
                }));
                c.production().equipment().clearBonuses(players.identity("beta"));lethalVolley();
            }));
        }));
    }
    /** Checks two same-tick lethal claims preserve full overkill credit while capping removed HP and recording unique commit stamps. */
    void lethalVolley() {
        for(String actor:List.of("alpha","beta")){kit(actor,"crit");players.request(actor,"lethal-volley-use");}
        players.await("both lethal bows",60,()->players.player("alpha").isHandRaised()&&players.player("beta").isHandRaised(),()->c.later(22,()->{
            int before=settlements.size();players.request("alpha","lethal-volley-release");players.request("beta","lethal-volley-release");
            settled(before+2,()->{
                var v=view();var r=v.completion().orElseThrow();var hits=settlements.subList(before,settlements.size());
                c.check("same_tick_lethal_two_owner_order",true,hits.size()==2&&hits.stream().map(SettledHit::collisionTick).distinct().count()==1
                        &&r.completedOrdinal()==6&&v.impacts().get(4).amounts().actualHealthDamage()==150&&v.impacts().get(5).amounts().actualHealthDamage()==37.5);
                c.check("lethal_overkill_and_ghost_totals",true,r.participants().size()==2&&r.participants().get(players.identity("alpha")).contributionDamage()==350&&r.participants().get(players.identity("beta")).contributionDamage()==450
                        &&r.participants().values().stream().mapToDouble(EncounterResult.Contribution::actualHealthDamage).sum()==500);
                c.check("frozen_participant_commit_stamps",true,r.participants().values().stream().map(t->t.lastCreditIncrease().orElseThrow().ordinal()).sorted().toList().equals(List.of(5L,6L))
                        &&r.participants().values().stream().allMatch(t->t.lastCreditIncrease().orElseThrow().tick()==r.completedTick()));
                c.check("one_completion_per_generation",true,combat.completions().size()==2&&combat.completions().stream().map(EncounterResult::encounterId).distinct().count()==2);
                command("alpha","reset-reduced",()->command("alpha","score",()->{capture();scoreOnly();}));
            });
        }));
    }
    /** Uses the explicit score-only practice profile to prove a Ferocity child can add credit without removing HP. */
    void scoreOnly() {
        kit("beta","ferocity_100");int count=settlements.size();
        draw("beta","score",()->settled(count+1,()->c.later(4,()->{
            totals("score_only",900,0,200,2);
            c.check("score_only_child_health_zero",true,view().impacts().getLast().kind()==DamageResult.Kind.FEROCITY&&view().impacts().getLast().amounts().actualHealthDamage()==0);
            command("beta","last-score",this::modifiers);
        })));
    }
    /** Sets trusted Power/Snipe equipment, checks the literal captured-distance formula, then records native claim evidence and final cleanup. */
    void modifiers() {
        // Labelled server item setup through the trusted registry/codec; production captures it at real launch.
        var registry=CalibrationLoadouts.registry();var instance=registry.create("ordinary");
        var edited=registry.edit(instance,Map.of("power",5,"snipe",4),List.of());
        players.setupItem("alpha",0,new WeaponItemCodec(registry).encode(edited));
        players.setupItem("alpha",9,new ItemStack(Material.ARROW,64));position("alpha");int count=settlements.size();
        draw("alpha","modifiers",()->settled(count+1,()->{
            var h=settlements.getLast();var s=h.projectile().shot();var p=h.impact().position();var l=s.launchPosition();
            double distance=Math.sqrt(Math.pow(p.x()-l.x(),2)+Math.pow(p.y()-l.y(),2)+Math.pow(p.z()-l.z(),2));
            double expected=100*(1+.4+.004*distance);
            var result=view().impacts().getLast();
            c.check("captured_power_snipe_numeric",true,Math.abs(result.amounts().contributionDamage()-expected)<1e-8
                    &&Math.abs(view().target().currentHealth()-(900-expected))<1e-8);
            c.check("all_claims_independently_observed",true,settlements.stream().allMatch(hh->nativeOwners.get(hh.projectile().shot().projectileId()).equals(hh.projectile().shot().ownerId())&&collisions.containsKey(hh.projectile().shot().projectileId())));
            c.observe("physicalClaims",settlements.stream().map(hh->Map.of("projectile",hh.projectile().shot().projectileId().toString(),"owner",hh.projectile().shot().ownerId().toString(),"collision",hh.collisionTick(),"settlement",hh.settlementTick(),"accepted",hh.accepted())).toList());
            command("alpha","reset-score",()->{
                c.check("repeated_reset_cleanup",true,combat.activeCount()==0&&c.production().bows().capacityUsed()==0&&c.production().bows().pendingClaims()==0);
                players.request("alpha","quit");players.request("beta","quit");
                players.await("practice actors quit",100,()->players.quits("alpha")==1&&players.quits("beta")==1,()->{
                    c.check("quit_explanations_cleared",true,combat.last(players.identity("alpha")).isEmpty()&&combat.last(players.identity("beta")).isEmpty());
                    c.observe("playerActions",players.journal());c.finish();
                });
            });
        }));
    }
    /** Binds the current owner-created generation and its actual native target to the damage probe. */
    void capture(){encounter=combat.owned(players.identity("alpha")).orElseThrow().encounterId();target=(LivingEntity)Bukkit.getEntity(view().entityId());probe.watch(target);}
    /** Reads the selected generation, including its retained terminal view after native removal. */
    ManagedCombatService.View view(){return combat.view(encounter).orElseThrow();}
    /** Compares literal expected HP/owner credit/impact count and separately scaled native HP for the current generation. */
    void totals(String label,double hp,double alpha,double beta,int impacts){
        var v=view();double nativeHp=20*hp/v.target().maxHealth();
        c.check(label+"_hp",hp,v.target().currentHealth());c.check(label+"_native_hp",true,Math.abs(target.getHealth()-nativeHp)<1e-5);
        c.check(label+"_alpha",alpha,v.contributions().getOrDefault(players.identity("alpha"),new EncounterResult.Contribution(0,0,0,false)).contributionDamage());
        c.check(label+"_beta",beta,v.contributions().getOrDefault(players.identity("beta"),new EncounterResult.Contribution(0,0,0,false)).contributionDamage());
        c.check(label+"_impacts",impacts,v.impacts().size());
    }
    /** Performs labelled server positioning and body aim, clearing player velocity before native use/release input. */
    void position(String actor){
        Player player=players.player(actor);var location=new Location(player.getWorld(),actor.equals("alpha")?-.7:.7,100,.5,0,2);
        if(target!=null&&target.isValid()){
            double dx=target.getLocation().getX()-location.getX(),dz=target.getLocation().getZ()-location.getZ();
            location.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));
            location.setPitch((float)Math.toDegrees(Math.atan2(location.getY()+player.getEyeHeight()-target.getLocation().getY()-1,Math.hypot(dx,dz))));
        }
        player.setVelocity(new org.bukkit.util.Vector());players.setupPosition(actor,location);
    }
    /** Performs labelled server loadout/ammo setup and positioning; this helper does not represent client inventory clicks. */
    void kit(String actor,String id){players.setupItem(actor,0,c.production().equipment().createLoadout(id));players.setupItem(actor,9,new ItemStack(Material.ARROW,64));position(actor);}
    /** Requests a declared command, waits for its native preprocess observation, then allows the command handler to finish. */
    void command(String actor,String step,Runnable next){int before=commands.getOrDefault(players.identity(actor),0);players.request(actor,step);players.await("command "+step,60,()->commands.getOrDefault(players.identity(actor),0)>before,()->c.later(2,next::run));}
    /** Anchors the hold duration to actual raised-hand state and waits for a native bow-release event before continuing. */
    void draw(String actor,String step,Runnable next){int before=releases.getOrDefault(players.identity(actor),0);players.request(actor,step+"-use");players.await("bow use "+step,60,()->players.player(actor).isHandRaised(),()->c.later(22,()->{players.request(actor,step+"-release");players.await("bow release "+step,60,()->releases.getOrDefault(players.identity(actor),0)>before,next::run);}));}
    /** Waits for receiver notifications, with bounded native geometry/trace diagnostics if the expected count is not reached. */
    void settled(int count,Runnable next){
        c.later(80,()->{ if(settlements.size()<count){c.observe("failedBowTrace",c.production().bows().trace().stream().map(t->Map.of("kind",t.kind(),"projectile",t.projectileId().toString(),"tick",t.tick(),"detail",t.detail())).toList());
            c.observe("failedGeometry",Map.of("target",target.getLocation().toVector().toString(),"bounds",target.getBoundingBox().toString(),"nativeHits",collisions.toString(),"nativeOwners",nativeOwners.toString()));}});
        players.await("settled physical count "+count,100,()->settlements.size()>=count,()->c.later(1,next::run));}
    /** Counts native player-command observations; individual stages separately assert the resulting command behavior. */
    @EventHandler(priority=EventPriority.MONITOR) public void command(PlayerCommandPreprocessEvent e){commands.merge(e.getPlayer().getUniqueId(),1,Integer::sum);}
    /** Observes real releases and optionally applies the labelled post-capture equipment-swap control. */
    @EventHandler(priority=EventPriority.MONITOR) public void bow(EntityShootBowEvent e){if(e.getEntity() instanceof Player p){releases.merge(p.getUniqueId(),1,Integer::sum);if(swap)p.getInventory().setItemInMainHand(c.production().equipment().createLoadout("crit"));}}
    /** Records native arrow UUID-to-shooter ownership independently of production settlement records. */
    @EventHandler(priority=EventPriority.MONITOR) public void launch(ProjectileLaunchEvent e){if(e.getEntity() instanceof Arrow arrow&&arrow.getShooter() instanceof Player p){nativeOwners.put(arrow.getUniqueId(),p.getUniqueId());}}
    /** Records collision ticks and applies the selected veto or native-damage sentinel before production settlement. */
    @EventHandler(priority=EventPriority.HIGH) public void hit(ProjectileHitEvent e){if(e.getEntity() instanceof Arrow a&&nativeOwners.containsKey(a.getUniqueId())){collisions.put(a.getUniqueId(),Bukkit.getCurrentTick());if(nativeDamage)a.setDamage(2);if(veto)e.setCancelled(true);}}
    /** Observes whether the target already has a frozen completion and empty proc queue at native death. */
    @EventHandler(priority=EventPriority.MONITOR) public void death(EntityDeathEvent e){if(encounter!=null&&target!=null&&e.getEntity().getUniqueId().equals(target.getUniqueId())){deaths++;completionAtDeath=combat.view(encounter).orElseThrow().completion().isPresent()&&combat.view(encounter).orElseThrow().procs().queued()==0;}}
}
