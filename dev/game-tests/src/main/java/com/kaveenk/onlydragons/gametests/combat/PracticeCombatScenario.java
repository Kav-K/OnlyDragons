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

/** Actual two-actor native launches through the production receiver/procs, with independent numeric oracles. */
public final class PracticeCombatScenario implements Scenario, Listener {
    ScenarioContext c; PlayerFixture players; DamageObservationProbe probe; ManagedCombatService combat;
    UUID encounter; LivingEntity target; boolean veto, nativeDamage, swap;
    final Map<UUID,Integer> releases=new HashMap<>(), commands=new HashMap<>();
    final Map<UUID,UUID> nativeOwners=new LinkedHashMap<>();
    final Map<UUID,Integer> collisions=new LinkedHashMap<>();
    final List<SettledHit> settlements=new ArrayList<>();
    int deaths; boolean completionAtDeath;
    @Override public void start(ScenarioContext context) throws Exception {
        c=context; c.mechanicRevision("practice-combat-v1"); combat=c.production().combat();
        players=new PlayerFixture(c); probe=new DamageObservationProbe(c); c.listen(this);
        var observation=combat.observeSettled(settlements::add);
        c.cleanup("practice-combat",()->{ combat.reset(players.identity("alpha")); combat.reset(players.identity("beta")); observation.close(); });
        players.await("two practice players",300,players::allOnline,this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    void setup() {
        World w=players.player("alpha").getWorld();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.tickChunk(w.getChunkAt(x,z));
        for(String actor:List.of("alpha","beta")) {
            Player p=players.player(actor);p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);
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
    void ordinary() {
        kit("alpha","ordinary");draw("alpha","ordinary",()->settled(1,()->{
            totals("ordinary",900,100,0,1);command("alpha","last",()->{
                kit("beta","crit");draw("beta","crit",()->settled(2,()->{totals("critical",750,100,150,2);ferocity(0);}));
            });
        }));
    }
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
    void volley() {
        for(String actor:List.of("alpha","beta")){kit(actor,"ferocity_100");position(actor);players.request(actor,"volley-use");}
        players.await("both volley bows",60,()->players.player("alpha").isHandRaised()&&players.player("beta").isHandRaised(),()->c.later(22,()->{
            int count=settlements.size();players.request("alpha","volley-release");players.request("beta","volley-release");
            settled(count+2,()->c.later(5,()->{
                var hits=settlements.subList(count,settlements.size());
                c.check("simultaneous_two_owner_physical_commits",true,hits.size()==2&&hits.stream().map(SettledHit::collisionTick).distinct().count()==1&&hits.stream().map(h->h.projectile().shot().ownerId()).distinct().count()==2);
                totals("reduced_two_owner",250,200,200,4);
                c.check("reduced_hp_credit_separate",true,view().contributions().values().stream().allMatch(t->t.actualHealthDamage()==125&&t.contributionDamage()==200));
                lethalVolley();
            }));
        }));
    }
    void lethalVolley() {
        for(String actor:List.of("alpha","beta")){kit(actor,"crit");players.request(actor,"lethal-volley-use");}
        players.await("both lethal bows",60,()->players.player("alpha").isHandRaised()&&players.player("beta").isHandRaised(),()->c.later(22,()->{
            int before=settlements.size();players.request("alpha","lethal-volley-release");players.request("beta","lethal-volley-release");
            settled(before+2,()->{
                var v=view();var r=v.completion().orElseThrow();var hits=settlements.subList(before,settlements.size());
                c.check("same_tick_lethal_two_owner_order",true,hits.size()==2&&hits.stream().map(SettledHit::collisionTick).distinct().count()==1
                        &&r.completedOrdinal()==6&&v.impacts().get(4).amounts().actualHealthDamage()==150&&v.impacts().get(5).amounts().actualHealthDamage()==100);
                c.check("lethal_overkill_and_ghost_totals",true,r.participants().size()==2&&r.participants().values().stream().allMatch(t->t.contributionDamage()==350)
                        &&r.participants().values().stream().mapToDouble(EncounterResult.Contribution::actualHealthDamage).sum()==500);
                c.check("frozen_participant_commit_stamps",true,r.participants().values().stream().map(t->t.lastCreditIncrease().orElseThrow().ordinal()).sorted().toList().equals(List.of(5L,6L))
                        &&r.participants().values().stream().allMatch(t->t.lastCreditIncrease().orElseThrow().tick()==r.completedTick()));
                c.check("one_completion_per_generation",true,combat.completions().size()==2&&combat.completions().stream().map(EncounterResult::encounterId).distinct().count()==2);
                command("alpha","reset-reduced",()->command("alpha","score",()->{capture();scoreOnly();}));
            });
        }));
    }
    void scoreOnly() {
        kit("beta","ferocity_100");int count=settlements.size();
        draw("beta","score",()->settled(count+1,()->c.later(4,()->{
            totals("score_only",900,0,200,2);
            c.check("score_only_child_health_zero",true,view().impacts().getLast().kind()==DamageResult.Kind.FEROCITY&&view().impacts().getLast().amounts().actualHealthDamage()==0);
            command("beta","last-score",this::modifiers);
        })));
    }
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
    void capture(){encounter=combat.owned(players.identity("alpha")).orElseThrow().encounterId();target=(LivingEntity)Bukkit.getEntity(view().entityId());probe.watch(target);}
    ManagedCombatService.View view(){return combat.view(encounter).orElseThrow();}
    void totals(String label,double hp,double alpha,double beta,int impacts){
        var v=view();double nativeHp=20*hp/v.target().maxHealth();
        c.check(label+"_hp",hp,v.target().currentHealth());c.check(label+"_native_hp",true,Math.abs(target.getHealth()-nativeHp)<1e-5);
        c.check(label+"_alpha",alpha,v.contributions().getOrDefault(players.identity("alpha"),new EncounterResult.Contribution(0,0,0,false)).contributionDamage());
        c.check(label+"_beta",beta,v.contributions().getOrDefault(players.identity("beta"),new EncounterResult.Contribution(0,0,0,false)).contributionDamage());
        c.check(label+"_impacts",impacts,v.impacts().size());
    }
    void position(String actor){players.setupPosition(actor,new Location(players.player(actor).getWorld(),actor.equals("alpha")?.35:.65,100,.5,0,2));}
    void kit(String actor,String id){players.setupItem(actor,0,c.production().equipment().createLoadout(id));players.setupItem(actor,9,new ItemStack(Material.ARROW,64));position(actor);}
    void command(String actor,String step,Runnable next){int before=commands.getOrDefault(players.identity(actor),0);players.request(actor,step);players.await("command "+step,60,()->commands.getOrDefault(players.identity(actor),0)>before,()->c.later(2,next::run));}
    void draw(String actor,String step,Runnable next){int before=releases.getOrDefault(players.identity(actor),0);players.request(actor,step+"-use");players.await("bow use "+step,60,()->players.player(actor).isHandRaised(),()->c.later(22,()->{players.request(actor,step+"-release");players.await("bow release "+step,60,()->releases.getOrDefault(players.identity(actor),0)>before,next::run);}));}
    void settled(int count,Runnable next){players.await("settled physical count "+count,100,()->settlements.size()>=count,()->c.later(1,next::run));}
    @EventHandler(priority=EventPriority.MONITOR) public void command(PlayerCommandPreprocessEvent e){commands.merge(e.getPlayer().getUniqueId(),1,Integer::sum);}
    @EventHandler(priority=EventPriority.MONITOR) public void bow(EntityShootBowEvent e){if(e.getEntity() instanceof Player p){releases.merge(p.getUniqueId(),1,Integer::sum);if(swap)p.getInventory().setItemInMainHand(c.production().equipment().createLoadout("crit"));}}
    @EventHandler(priority=EventPriority.MONITOR) public void launch(ProjectileLaunchEvent e){if(e.getEntity() instanceof Arrow arrow&&arrow.getShooter() instanceof Player p){nativeOwners.put(arrow.getUniqueId(),p.getUniqueId());if(nativeDamage)arrow.setDamage(2);}}
    @EventHandler(priority=EventPriority.HIGH) public void hit(ProjectileHitEvent e){if(e.getEntity() instanceof Arrow a&&nativeOwners.containsKey(a.getUniqueId())){collisions.put(a.getUniqueId(),Bukkit.getCurrentTick());if(veto)e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.MONITOR) public void death(EntityDeathEvent e){if(encounter!=null&&target!=null&&e.getEntity().getUniqueId().equals(target.getUniqueId())){deaths++;completionAtDeath=combat.view(encounter).orElseThrow().completion().isPresent()&&combat.view(encounter).orElseThrow().procs().queued()==0;}}
}
