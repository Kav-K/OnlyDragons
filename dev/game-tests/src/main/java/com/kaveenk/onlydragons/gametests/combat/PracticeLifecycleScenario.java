package com.kaveenk.onlydragons.gametests.combat;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.application.proc.ProcCoordinator;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Native actors and real arrows; explicit fixture flight holds isolate reconnect/terminal ordering. */
public final class PracticeLifecycleScenario implements Scenario,Listener {
    ScenarioContext c; PlayerFixture players; DamageObservationProbe probe; ManagedCombatService service;
    UUID generation; LivingEntity target; Arrow held; OwnedProjectile captured; Vector velocity;
    String freeze; boolean deathOnHit,exitOnHit,quitOnSettlement,queuedAtDeath,queuedAtQuit,competing;
    int deathCount,peakClaims; final List<SettledHit> settlements=new ArrayList<>();
    final Map<UUID,Integer> releases=new HashMap<>(),commands=new HashMap<>();
    final Map<UUID,Integer> hits=new LinkedHashMap<>();
    final Map<UUID,UUID> owners=new LinkedHashMap<>();
    @Override public void start(ScenarioContext context)throws Exception{
        c=context;c.mechanicRevision("practice-lifecycle-v1");service=c.production().combat();players=new PlayerFixture(c);probe=new DamageObservationProbe(c);c.listen(this);
        var observation=service.observeSettled(hit->{
            settlements.add(hit);
            if(deathOnHit)queuedAtDeath=view().procs().queued()>0;
            if(quitOnSettlement&&hit.projectile().shot().ownerId().equals(players.identity("beta"))){
                queuedAtQuit=view().procs().queued()>0;quitOnSettlement=false;players.request("beta","reconnect");
            }
        });
        c.cleanup("practice-lifecycle",()->{service.reset(players.identity("alpha"));service.reset(players.identity("beta"));observation.close();});
        players.await("lifecycle actors",300,players::allOnline,this::setup);c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    void setup(){World w=players.player("alpha").getWorld();for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.tickChunk(w.getChunkAt(x,z));
        for(String a:List.of("alpha","beta"))prepare(a);players.permission("alpha","onlydragons.practice",true);
        command("alpha","open",()->{capture();freeze="alpha";kit("alpha","ferocity_100");draw("alpha","old",()->{
            players.await("native held arrow",40,()->held!=null,()->{
                c.check("old_arrow_live_before_reconnect",true,held.isValid()&&captured!=null);players.request("alpha","reconnect");
                players.await("alpha reconnect",150,()->players.joins("alpha")==2,()->{
                    prepare("alpha");c.production().bows().activate(players.player("alpha"));
                    c.check("new_session_does_not_reactivate_old",true,!c.production().bows().isCurrentSession(players.identity("alpha"),captured.sessionToken()));
                    freeze=null;held.setGravity(true);held.setVelocity(velocity);settled(1,()->{
                        var last=service.last(players.identity("alpha")).orElseThrow();
                        c.check("old_arrow_physical_only_after_reconnect",true,last.damage().accepted()&&last.admission()==ProcCoordinator.Admission.INACTIVE_SESSION&&last.damage().amounts().contributionDamage()==100
                                &&view().acceptedImpacts()==1&&view().procs().queued()==0&&view().procs().tempoStates()==0);
                        c.check("old_arrow_identity_and_token_frozen",true,settlements.getFirst().projectile().equals(captured));death();
                    });
                });
            });
        });});
    }
    void death(){deathOnHit=true;kit("alpha","ferocity_100");draw("alpha","death",()->{
        players.await("actual alpha death",80,()->deathCount==1,()->c.later(4,()->{
            deathOnHit=false;c.check("queued_child_cleared_on_real_death",true,queuedAtDeath&&view().acceptedImpacts()==2&&view().target().currentHealth()==800&&view().procs().queued()==0);
            players.request("alpha","respawn");players.await("packet respawn",100,()->!players.player("alpha").isDead(),()->{
                prepare("alpha");c.production().bows().activate(players.player("alpha"));quit();
            });
        }));
    });}
    void quit(){kit("beta","ferocity_100");quitOnSettlement=true;draw("beta","quit-shot",()->{
        players.await("beta reconnect after queued parent",150,()->players.joins("beta")==2,()->{
            prepare("beta");c.production().bows().activate(players.player("beta"));c.later(4,()->{
                c.check("queued_child_cleared_on_actual_quit",true,queuedAtQuit&&view().acceptedImpacts()==3&&view().target().currentHealth()==700&&view().procs().queued()==0);
                c.check("captured_two_owner_totals_after_reconnect",true,view().contributions().get(players.identity("alpha")).contributionDamage()==200&&view().contributions().get(players.identity("beta")).contributionDamage()==100);
                exit();
            });
        });
    });}
    void exit(){exitOnHit=true;kit("beta","ferocity_100");int before=settlements.size();draw("beta","exit",()->settled(before+1,()->c.later(5,()->{
        exitOnHit=false;c.check("queued_child_cleared_on_arena_exit",true,view().acceptedImpacts()==4&&view().target().currentHealth()==600&&view().procs().queued()==0
                &&c.production().bows().currentSession(players.identity("beta")).isEmpty());
        prepare("beta");c.production().bows().activate(players.player("beta"));resetAirborne();
    })));}
    void resetAirborne(){freeze="alpha";held=null;kit("alpha","ordinary");draw("alpha","reset-airborne",()->{
        players.await("airborne before command reset",40,()->held!=null,()->{
            Arrow old=held;UUID oldGeneration=generation;c.check("reset_has_real_pending_arrow",true,old.isValid()&&c.production().bows().projectile(old.getUniqueId()).isPresent());
            players.permission("alpha","onlydragons.practice",true);command("alpha","reset",()->{
                c.check("command_reset_retires_old_generation",true,!old.isValid()&&service.view(oldGeneration).orElseThrow().state()==ManagedCombatService.State.TERMINATED&&service.view(oldGeneration).orElseThrow().completion().isEmpty());
                freeze=null;command("alpha","proc-open",()->{
                    capture();c.check("new_encounter_online_activation_without_relog",true,view().procs().sessions()==2&&players.joins("alpha")==2&&players.joins("beta")==2);
                    procLethal();
                });
            });
        });
    });}
    void procLethal(){freeze="beta";held=null;kit("beta","ordinary");draw("beta","late",()->{
        players.await("pending late native arrow",40,()->held!=null,()->{
            Arrow late=held;UUID lateId=late.getUniqueId();c.check("real_late_arrow_pending_before_lethal",true,late.isValid()&&c.production().bows().projectile(lateId).isPresent());
            freeze=null;kit("alpha","ferocity_100");int before=settlements.size();draw("alpha","proc",()->settled(before+1,()->{
                players.await("proc completion",60,()->view().completion().isPresent(),()->{
                    var result=view().completion().orElseThrow();var total=result.participants().get(players.identity("alpha"));
                    c.check("proc_lethal_independent_health_credit",true,total.actualHealthDamage()==120&&total.contributionDamage()==200&&result.participants().size()==1&&result.completedOrdinal()==2);
                    c.check("proc_lethal_parent_stamp_and_completion",true,result.completionId().equals(view().impacts().getLast().impactId())&&view().impacts().getLast().parentImpactId().equals(Optional.of(view().impacts().getFirst().impactId()))
                            &&total.lastCreditIncrease().orElseThrow().ordinal()==2&&total.firstParticipation().orElseThrow().ordinal()==1);
                    c.check("late_arrow_retired_without_credit",true,!late.isValid()&&c.production().bows().projectile(lateId).isEmpty()&&settlements.stream().noneMatch(h->h.projectile().shot().projectileId().equals(lateId)));
                    c.later(5,()->{c.check("proc_completion_frozen_once",true,service.completions().size()==1&&view().completion().orElseThrow().equals(result));
                        command("alpha","compete-open",()->{capture();compete();});});
                });
            }));
        });
    });}
    void compete(){competing=true;peakClaims=0;int hitBefore=hits.size(),before=settlements.size();
        for(String a:List.of("alpha","beta")){kit(a,"ordinary");players.request(a,"compete-use");}
        players.await("competing real draws",60,()->players.player("alpha").isHandRaised()&&players.player("beta").isHandRaised(),()->c.later(22,()->{
            players.request("alpha","compete-release");players.request("beta","compete-release");settled(before+1,()->c.later(5,()->{
                var v=view();var r=v.completion().orElseThrow();var winner=v.impacts().getFirst();
                c.check("two_pending_lethal_claims_one_commit",true,peakClaims==2&&hits.size()==hitBefore+2&&settlements.size()==before+1&&v.acceptedImpacts()==1&&r.completedOrdinal()==1);
                c.check("competing_lethal_overkill_and_owner",true,winner.amounts().actualHealthDamage()==75&&winner.amounts().contributionDamage()==100&&r.participants().size()==1
                        &&r.participants().get(winner.ownerId()).contributionDamage()==100&&owners.get(winner.origin().projectileId()).equals(winner.ownerId()));
                c.check("competing_completion_and_cleanup_once",true,service.completions().size()==2&&c.production().bows().pendingClaims()==0&&c.production().bows().capacityUsed()==0);
                command("alpha","final-reset",()->{
                    c.check("repeat_cleanup_no_live_fights",0,service.activeCount());players.request("alpha","end");players.request("beta","end");
                    players.await("final actual quits",100,()->players.quits("alpha")==2&&players.quits("beta")==2,()->{
                        c.observe("playerActions",players.journal());c.observe("physicalHits",hits.entrySet().stream().map(e->Map.of("projectile",e.getKey().toString(),"tick",e.getValue(),"owner",owners.get(e.getKey()).toString())).toList());c.finish();
                    });
                });
            }));
        }));
    }
    ManagedCombatService.View view(){return service.view(generation).orElseThrow();}
    void capture(){generation=service.owned(players.identity("alpha")).orElseThrow().encounterId();target=(LivingEntity)Bukkit.getEntity(view().entityId());probe.watch(target);}
    void prepare(String a){Player p=players.player(a);p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);p.setCollidable(false);p.getInventory().setHeldItemSlot(0);players.setupPosition(a,new Location(p.getWorld(),a.equals("alpha")?-.7:.7,100,.5,0,2));}
    void kit(String a,String id){prepare(a);
        if(target!=null&&target.isValid()){
            Player p=players.player(a);var location=p.getLocation();double dx=target.getLocation().getX()-location.getX(),dz=target.getLocation().getZ()-location.getZ();
            location.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));location.setPitch((float)Math.toDegrees(Math.atan2(location.getY()+p.getEyeHeight()-target.getLocation().getY()-1,Math.hypot(dx,dz))));
            p.setVelocity(new Vector());players.setupPosition(a,location);
        }
        players.setupItem(a,0,c.production().equipment().createLoadout(id));players.setupItem(a,9,new ItemStack(Material.ARROW,64));}
    void command(String a,String id,Runnable next){int before=commands.getOrDefault(players.identity(a),0);players.request(a,id);players.await("command "+id,60,()->commands.getOrDefault(players.identity(a),0)>before,()->c.later(2,next::run));}
    void draw(String a,String id,Runnable next){int before=releases.getOrDefault(players.identity(a),0);players.request(a,id+"-use");players.await("draw "+id,60,()->players.player(a).isHandRaised(),()->c.later(22,()->{players.request(a,id+"-release");players.await("release "+id,60,()->releases.getOrDefault(players.identity(a),0)>before,next::run);}));}
    void settled(int n,Runnable next){players.await("settlements "+n,100,()->settlements.size()>=n,()->c.later(1,next::run));}
    @EventHandler(priority=EventPriority.MONITOR)public void command(PlayerCommandPreprocessEvent e){commands.merge(e.getPlayer().getUniqueId(),1,Integer::sum);}
    @EventHandler(priority=EventPriority.MONITOR)public void bow(EntityShootBowEvent e){if(e.getEntity()instanceof Player p)releases.merge(p.getUniqueId(),1,Integer::sum);}
    @EventHandler(priority=EventPriority.MONITOR)public void launch(ProjectileLaunchEvent e){if(e.getEntity()instanceof Arrow arrow&&arrow.getShooter()instanceof Player p){
        owners.put(arrow.getUniqueId(),p.getUniqueId());
        if(freeze!=null&&p.getUniqueId().equals(players.identity(freeze))){held=arrow;velocity=arrow.getVelocity().clone();captured=c.production().bows().projectile(arrow.getUniqueId()).orElseThrow();arrow.setGravity(false);arrow.setVelocity(new Vector());}
    }}
    @EventHandler(priority=EventPriority.MONITOR)public void hit(ProjectileHitEvent e){if(e.getHitEntity()!=null&&target!=null&&e.getHitEntity().getUniqueId().equals(target.getUniqueId())&&e.getEntity()instanceof Arrow arrow){
        hits.put(arrow.getUniqueId(),Bukkit.getCurrentTick());if(competing)peakClaims=Math.max(peakClaims,c.production().bows().pendingClaims());
        if(deathOnHit)c.later(2,()->players.player("alpha").setHealth(0));
        if(exitOnHit)c.later(2,()->players.setupPosition("beta",new Location(players.player("beta").getWorld(),2500,100,0)));
    }}
    @EventHandler(priority=EventPriority.MONITOR)public void death(PlayerDeathEvent e){if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))deathCount++;}
}
