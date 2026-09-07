package com.kaveenk.onlydragons.gametests.encounter;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Exercises managed dragon commands, native physical/proc defeat and terminal cleanup.
 * The protocol actor supplies real releases; permissions, equipment, bonuses, held
 * arrows, vetoes and native-damage/reward sentinels are explicit fixture interventions.
 * Independent event journals distinguish frozen accounting from native death,
 * animation, removal and suppressed loot. Failure/reentry controls must not publish
 * an ordinary completion. Context owns callbacks/listeners and production reset cleanup.
 */
public final class DragonCombatScenario implements Scenario, Listener {
    ScenarioContext c; PlayerFixture players; DevelopmentDragonService dragons; DamageObservationProbe probe;
    double unrelatedHealth; org.bukkit.entity.Cow unrelated; EnderDragon dragon, control; UUID generation; int commands, releases, deaths, animationStart=-1, removedAt=-1, xp, items, controlXp;
    boolean veto, sentinel, cancelDeath, failSpawn, nativeGuard, hold; final List<Arrow> held=new ArrayList<>(); final List<Vector> velocities=new ArrayList<>(); String loadout="ordinary"; double bonus; final List<DevelopmentDragonService.Completion> notifications=new ArrayList<>(); boolean reentryRejected, settledCloseRejected, handlesChecked, removeOnRetirement;
    DevelopmentDragonService.Subscription oldHandle;
    final java.util.function.Consumer<DevelopmentDragonService.Completion> repeatedConsumer=event->{};
    final List<SettledHit> settled=new ArrayList<>();
    final Map<UUID,Integer> hits=new LinkedHashMap<>(); final List<Map<String,Object>> rewards=new ArrayList<>();
    /**
     * Registers settled-hit reentry observation and cleanup before awaiting the actor.
     * @param context run-owned server-thread checks, fixtures and cleanup
     * @throws Exception if initialization cannot establish the declared player fixture
     */
    public void start(ScenarioContext context)throws Exception {
        c=context;c.mechanicRevision("dragon-combat-v1");dragons=c.production().dragons();players=new PlayerFixture(c);probe=new DamageObservationProbe(c);c.listen(this);
        var observation=c.production().combat().observeSettled(hit->{
            settled.add(hit);
            if(!settledCloseRejected)try{dragons.close();}catch(IllegalStateException expected){settledCloseRejected=true;}
        });
        c.cleanup("managed-dragon",()->{ if(dragons.generation().isPresent()&&c.production().combat().ownsEntity(dragons.view().orElseThrow().entityId()))dragons.reset(dragons.generation().orElseThrow());observation.close(); });
        players.await("dragon actor",300,players::allOnline,this::setup);c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    /** Creates an unrelated native sentinel and checks connected setup/spawn, veto, suppression, stale tokens and active-arena rejection. */
    void setup(){
        var p=players.player("alpha");c.check("native_mob_drops_enabled",true,Boolean.TRUE.equals(p.getWorld().getGameRuleValue(GameRules.MOB_DROPS)));p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);
        p.getInventory().clear();p.getInventory().setHeldItemSlot(0);p.setTotalExperience(0);p.setLevel(0);p.setExp(0);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)c.tickChunk(p.getWorld().getChunkAt(x,z));
        unrelated=c.own(p.getWorld().spawn(new Location(p.getWorld(),16,100,16),org.bukkit.entity.Cow.class));
        unrelatedHealth=unrelated.getHealth();unrelated.setAI(false);unrelated.setGravity(false);unrelated.setInvulnerable(true);
        players.permission("alpha","onlydragons.practice",true);
        command("setup",()->command("spawn",()->{
            capture();c.check("real_dragon_initialized",true,dragon.isValid()&&dragon.getDragonBattle()==null&&dragon.getPhase()==EnderDragon.Phase.HOVER&&view().target().currentHealth()==1000);
            c.check("selection_retained",true,dragons.selection().orElseThrow().equals(c.production().dragonDefinitions().snapshot().select("test_dragon")));
            sentinel=true;veto=true;players.await("native parts at arena",80,()->dragon.getParts().stream().allMatch(part->part.getLocation().getY()>75),()->draw("veto",()->awaitSettled(1,()->{
                veto=false;c.check("cancelled_hit_zero_credit",true,view().acceptedImpacts()==0&&view().target().currentHealth()==1000&&settled.getLast().rejection().isPresent());
                nativeGuard=true;draw("ordinary",()->awaitSettled(2,()->{
                    nativeGuard=false;c.check("owned_arrow_exact_damage",true,view().target().currentHealth()==900&&view().acceptedImpacts()==1&&view().contributions().get(players.identity("alpha")).contributionDamage()==100&&dragon.getHealth()==180);
                    c.check("physical_owner_and_retirement",true,settled.getLast().projectile().shot().ownerId().equals(players.identity("alpha"))&&Bukkit.getEntity(settled.getLast().projectile().shot().projectileId())==null);
                    c.check("native_suppression_observed",true,probe.events().stream().anyMatch(e->e.cancelled()&&e.settledDamage()==0&&e.initialDamage()>0));
                    command("status",()->command("stale-token",()->command("active-setup",()->{
                        c.check("connected_stale_and_active_setup_reject",true,dragons.generation().orElseThrow().equals(generation)&&dragons.arena().orElseThrow().x()==0&&dragon.isValid());killShot(0);
                    })));
                }));
            })));
        }));
    }
    /** Accumulates fixed physical hits, then releases two fixture-held arrows together while leaving a third pending across lethal cleanup. */
    void killShot(int index){
        if(index==7){
            hold=true;draw("late",()->draw("hit7",()->draw("hit8",()->{
                hold=false;c.check("three_real_arrows_held_before_lethal",true,held.size()==3&&held.stream().allMatch(Entity::isValid));
                for(int i=1;i<3;i++){held.get(i).setGravity(true);held.get(i).setVelocity(velocities.get(i));}
                awaitSettled(11,()->{
                    c.check("same_tick_lethal_order",true,hits.get(held.get(1).getUniqueId()).equals(hits.get(held.get(2).getUniqueId()))&&view().acceptedImpacts()==10);
                    c.check("late_arrow_retired_without_claim",true,!held.getFirst().isValid()&&!hits.containsKey(held.getFirst().getUniqueId()));
                    terminal();
                });
            })));return;
        }
        draw("hit"+index,()->awaitSettled(3+index,()->killShot(index+1)));
    }
    /** Checks frozen result/subscriber behavior separately from native death animation, delayed reward suppression and ticket retirement. */
    void terminal(){
        var v=view();var result=v.completion().orElseThrow();
        c.check("settled_close_rejected_without_poisoning",true,settledCloseRejected);
        c.check("native_completion_subscription",true,notifications.size()==1&&notifications.getFirst().result().equals(result)&&notifications.getFirst().nativeId().equals(dragon.getUniqueId())&&reentryRejected&&dragons.subscriberCount()==0&&dragons.notificationFailures()==1);
        c.check("one_lethal_frozen_result",true,result.completedOrdinal()==10&&v.target().currentHealth()==0&&result.participants().get(players.identity("alpha")).actualHealthDamage()==1000&&result.participants().get(players.identity("alpha")).contributionDamage()==1000&&c.production().combat().completions().size()==1);
        c.check("native_death_separate_from_domain",true,deaths==1&&dragon.getHealth()==0);
        c.check("terminal_work_closed",true,v.procs().queued()==0&&v.procs().sessions()==0&&c.production().bows().pendingClaims()==0&&c.production().bows().capacityUsed()==0);
        command("result",()->{
            players.await("native death animation starts",80,()->dragon.getDeathAnimationTicks()>0,()->{
                animationStart=Bukkit.getCurrentTick();c.check("native_animation_advances",true,dragon.getDeathAnimationTicks()>0&&Bukkit.getEntity(dragon.getUniqueId())==dragon&&c.production().combat().ownsEntity(dragon.getUniqueId())&&c.production().bows().continuity().tickets().demandCount()>0&&c.production().bows().continuity().tickets().reservedCount()>0);
                players.await("native DEATH removal",400,()->removedAt>=0,()->{
                    c.check("one_native_death_removal",true,Bukkit.getEntity(dragon.getUniqueId())==null&&deaths==1);
                    long remaining=Math.max(220-(Bukkit.getCurrentTick()-animationStart),20);
                    c.later(remaining,()->{
                        c.check("delayed_rewards_suppressed",true,xp==0&&items==0&&players.player("alpha").getTotalExperience()==0&&players.player("alpha").getInventory().all(Material.DIAMOND).isEmpty());
                        c.check("frozen_after_late_ticks",true,view().completion().orElseThrow().equals(result)&&c.production().combat().completions().size()==1);
                        c.check("death_releases_owned_tickets",true,c.production().combat().activeCount()==0&&c.production().bows().continuity().tickets().demandCount()==0&&c.production().bows().continuity().tickets().reservedCount()==0);
                        positiveControl();
                    });
                });
            });
        });
    }
    /** Kills an unmanaged native dragon to prove the XP observer works, then checks reset and repeated reset never fabricate a result. */
    void positiveControl(){
        var w=players.player("alpha").getWorld();control=c.own(w.spawn(new Location(w,35,100,0),EnderDragon.class));control.setPersistent(false);control.setPhase(EnderDragon.Phase.HOVER);control.setHealth(0);
        players.await("native unmanaged XP positive control",400,()->controlXp>0,()->{
            c.check("native_xp_observer_positive_control",true,controlXp>0);control.remove();
            command("spawn-again",()->{capture();UUID old=generation;
                try{dragons.reset(UUID.randomUUID());c.check("stale_reset_rejected",true,false);}catch(IllegalArgumentException expected){c.check("stale_reset_rejected",true,dragon.isValid());}
                command("reset",()->{
                    c.check("reset_no_ordinary_result",true,Bukkit.getEntity(dragon.getUniqueId())==null&&c.production().combat().completions().size()==1&&view().completion().isEmpty());
                    command("repeat",()->{c.check("repeat_reset_no_extra_completion",1,c.production().combat().completions().size());failedSpawn(0);});
                });
            });
        });
    }
    /** Repeats the explicit cancelled-spawn control and checks combat/ticket capacity returns to baseline on every attempt. */
    void failedSpawn(int index){
        if(index==3){procTrial();return;}
        failSpawn=true;command("failed"+index,()->{
            failSpawn=false;c.check("failed_spawn_cleanup_"+index,true,c.production().combat().activeCount()==0&&c.production().bows().continuity().tickets().reservedCount()==0&&c.production().bows().continuity().tickets().demandCount()==0);
            failedSpawn(index+1);
        });
    }
    /** Uses trusted Ferocity plus a labelled damage bonus for proc-lethal credit/HP and reset-during-animation preservation. */
    void procTrial(){command("proc-spawn",()->{
        capture();loadout="ferocity_100";bonus=500;
        draw("proc",()->players.await("proc lethal",100,()->view().completion().isPresent(),()->{
            var r=view().completion().orElseThrow();
            c.check("proc_lethal_subscription",true,notifications.size()==2&&notifications.getLast().result().equals(r)&&dragons.subscriberCount()==0);
            c.check("proc_lethal_native_boundary",true,r.completedOrdinal()==2&&r.participants().get(players.identity("alpha")).actualHealthDamage()==1000&&r.participants().get(players.identity("alpha")).contributionDamage()==1200&&dragon.getHealth()==0);
            command("animation-reset",()->{
                c.check("animation_reset_keeps_frozen_no_rewards",true,Bukkit.getEntity(dragon.getUniqueId())==null&&view().completion().orElseThrow().equals(r)&&xp==0&&items==0);
                cancelledTrial();
            });
        }));
    });}
    /** Cancels native lethal death, proving the frozen diagnostic cannot become an ordinary notification even after administrative follow-up. */
    void cancelledTrial(){command("cancel-spawn",()->{
        capture();cancelDeath=true;loadout="ordinary";bonus=900;
        draw("cancel",()->players.await("cancelled native lethal",100,()->view().completion().isPresent(),()->{
            cancelDeath=false;
            c.check("cancelled_death_no_notification",true,notifications.size()==2&&dragons.subscriberCount()==0);
            c.check("cancelled_death_is_diagnostic",true,dragon.isValid()&&dragon.getHealth()>0&&dragons.status().contains("DEATH_CANCELLED")&&view().acceptedImpacts()==1);
            double revived=dragon.getHealth();dragon.damage(50);
            c.check("revived_terminal_native_guard",revived,dragon.getHealth());
            var frozen=view().completion().orElseThrow();c.later(5,()->{
                c.check("cancelled_death_not_retried",true,view().completion().orElseThrow().equals(frozen)&&dragon.getHealth()>0);
                dragon.setHealth(0); // Explicit administrative second death must never validate the disqualified completion.
                c.check("later_native_death_cannot_publish_cancelled_result",true,notifications.size()==2&&view().completion().orElseThrow().equals(frozen));
                command("cancel-reset",()->{c.check("cancelled_death_cleanup",true,Bukkit.getEntity(dragon.getUniqueId())==null&&c.production().combat().activeCount()==0&&c.production().bows().continuity().tickets().reservedCount()==0);removalTrial();});
            });
        }));
    });}
    /** Removes the native dragon from an arrow-retirement callback to test nonordinary removal before completion delivery. */
    void removalTrial(){command("removal-spawn",()->{
        capture();held.clear();velocities.clear();hold=true;bonus=900;
        draw("removal-held",()->{
            hold=false;removeOnRetirement=true;
            draw("removal-lethal",()->players.await("retirement callback removes dragon",100,()->Bukkit.getEntity(dragon.getUniqueId())==null,()->{
                removeOnRetirement=false;
                c.check("nonordinary_removal_before_delivery_disqualifies",true,notifications.size()==2&&view().completion().isPresent()&&dragons.subscriberCount()==0);
                c.later(2,()->{c.check("retirement_callback_cleanup",true,c.production().combat().activeCount()==0&&c.production().bows().continuity().tickets().reservedCount()==0);finish();});
            }));
        });
    });}
    /** Checks unrelated-entity/subscription preservation, records native journals and waits for the actual final quit. */
    void finish(){c.check("unrelated_entity_preserved",true,unrelated.isValid()&&unrelated.getHealth()==unrelatedHealth);c.check("subscriptions_return_to_baseline",0,dragons.subscriberCount());c.observe("rewardEvents",rewards);c.observe("physicalHits",hits.entrySet().stream().map(e->Map.of("uuid",e.getKey().toString(),"tick",e.getValue())).toList());c.observe("playerActions",players.journal());players.request("alpha","quit");players.await("actual quit",100,()->players.quits("alpha")==1,c::finish);}
    /** Reads the retained combat view for this exact captured generation, including after native retirement. */
    ManagedCombatService.View view(){return c.production().combat().view(generation).orElseThrow();}
    /** Captures native identity and registers deliberate throwing/reentrant subscribers, including stale/equal-handle isolation controls. */
    void capture(){generation=dragons.generation().orElseThrow();dragon=(EnderDragon)Bukkit.getEntity(view().entityId());probe.watch(dragon);
        if(oldHandle==null)oldHandle=dragons.subscribe(generation,repeatedConsumer);
        else if(!handlesChecked){
            handlesChecked=true;
            var one=dragons.subscribe(generation,repeatedConsumer);var two=dragons.subscribe(generation,repeatedConsumer);
            oldHandle.close();oldHandle.close();c.check("stale_subscription_handle_preserves_new_generation",3,dragons.subscriberCount());
            one.close();one.close();c.check("repeated_handle_preserves_equal_registration",2,dragons.subscriberCount());two.close();
        }
        dragons.subscribe(generation,event->{throw new IllegalStateException("deliberate consumer failure");});
        dragons.subscribe(generation,event->{
            notifications.add(event);
            try{dragons.close();}catch(IllegalStateException expected){reentryRejected=true;}
        });
    }
    /** Aims from the largest current native part, sets labelled loadout/ammo/bonus, then anchors a real release to observed raised-hand state. */
    void draw(String step,Runnable next){
        var p=players.player("alpha");var box=dragon.getParts().stream().max(Comparator.comparingDouble(part->part.getBoundingBox().getVolume())).orElseThrow().getBoundingBox();
        var aim=box.getCenter();var origin=aim.clone().add(new Vector(0,0,-12));
        players.setupPosition("alpha",new Location(p.getWorld(),origin.getX(),origin.getY()-p.getEyeHeight(),origin.getZ(),0,0));
        p.setVelocity(new Vector());players.setupItem("alpha",0,c.production().equipment().createLoadout(loadout));players.setupItem("alpha",9,new ItemStack(Material.ARROW,64));
        c.production().equipment().bonus(p,com.kaveenk.onlydragons.domain.stats.StatKey.WEAPON_DAMAGE,bonus);
        int before=releases;players.request("alpha",step+"-use");players.await("draw "+step,60,p::isHandRaised,()->c.later(22,()->{players.request("alpha",step+"-release");players.await("release "+step,60,()->releases>before,next::run);}));
    }
    /** Waits for receiver notifications and one additional tick before evaluating the next boundary. */
    void awaitSettled(int count,Runnable next){players.await("settled "+count,100,()->settled.size()>=count,()->c.later(1,next::run));}
    /** Requests a declared protocol command and waits beyond its native preprocess event before checking results. */
    void command(String step,Runnable next){int before=commands;players.request("alpha",step);players.await("command "+step,80,()->commands>before,()->c.later(2,next::run));}
    /** Counts native command preprocess events used to sequence the fixture. */
    @EventHandler(priority=EventPriority.MONITOR)public void command(PlayerCommandPreprocessEvent e){commands++;}
    /** Counts real player bow-release events rather than queued client requests. */
    @EventHandler(priority=EventPriority.MONITOR)public void release(EntityShootBowEvent e){if(e.getEntity()instanceof Player)releases++;}
    /** Cancels native dragon creation only during the explicit failed-spawn trials. */
    @EventHandler(priority=EventPriority.HIGHEST)public void spawn(CreatureSpawnEvent e){if(failSpawn&&e.getEntity()instanceof EnderDragon)e.setCancelled(true);}
    /** Applies the labelled cancellation only to this generation's native dragon death. */
    @EventHandler(priority=EventPriority.HIGHEST)public void cancelDeath(EntityDeathEvent e){if(cancelDeath&&dragon!=null&&e.getEntity().getUniqueId().equals(dragon.getUniqueId()))e.setCancelled(true);}
    /** Optionally restores native-damage pressure or holds real arrows with saved velocities to isolate terminal ordering. */
    @EventHandler(priority=EventPriority.MONITOR)public void launch(ProjectileLaunchEvent e){if(e.getEntity()instanceof Arrow arrow){
        if(nativeGuard)c.later(1,()->{if(arrow.isValid())arrow.setDamage(2);});
        if(hold){held.add(arrow);velocities.add(arrow.getVelocity().clone());arrow.setGravity(false);arrow.setVelocity(new Vector());}
    }}
    /** Normalizes part hits to the parent, records physical collision ticks and applies the selected veto. */
    @EventHandler(priority=EventPriority.HIGHEST)public void hit(ProjectileHitEvent e){Entity parent=e.getHitEntity()instanceof EnderDragonPart part?part.getParent():e.getHitEntity();if(parent!=null&&dragon!=null&&parent.getUniqueId().equals(dragon.getUniqueId())){hits.put(e.getEntity().getUniqueId(),Bukkit.getCurrentTick());if(veto)e.setCancelled(true);}}
    /** Injects a diamond drop before production death handling so suppression is tested against positive loot pressure. */
    @EventHandler(priority=EventPriority.LOWEST)public void sentinel(EntityDeathEvent e){if(dragon!=null&&e.getEntity().getUniqueId().equals(dragon.getUniqueId())&&sentinel)e.getDrops().add(new ItemStack(Material.DIAMOND));}
    /** Checks the first native death has a frozen domain completion and cleared drops/XP after production handling. */
    @EventHandler(priority=EventPriority.MONITOR)public void death(EntityDeathEvent e){if(dragon!=null&&e.getEntity().getUniqueId().equals(dragon.getUniqueId())){deaths++;if(deaths==1)c.check("native_death_rewards_cleared",true,e.getDrops().isEmpty()&&e.getDroppedExp()==0&&!e.isCancelled()&&view().completion().isPresent());}}
    /** Injects the retirement-removal race when selected and separately records actual native DEATH removal timing. */
    @EventHandler(priority=EventPriority.MONITOR)public void removed(EntityRemoveEvent e){if(removeOnRetirement&&held.stream().anyMatch(a->a.getUniqueId().equals(e.getEntity().getUniqueId())))dragon.remove();if(dragon!=null&&e.getEntity().getUniqueId().equals(dragon.getUniqueId())&&e.getCause()==EntityRemoveEvent.Cause.DEATH)removedAt=Bukkit.getCurrentTick();}
    /** Attributes native XP by source UUID, retaining an unmanaged positive control and owning spawned orbs for cleanup. */
    @EventHandler(priority=EventPriority.MONITOR)public void xp(EntitySpawnEvent e){if(e.getEntity()instanceof ExperienceOrb orb){UUID source=orb.getSourceEntityId();rewards.add(Map.of("source",String.valueOf(source),"reason",orb.getSpawnReason().toString(),"value",orb.getExperience()));if(dragon!=null&&dragon.getUniqueId().equals(source))xp+=orb.getExperience();if(control!=null&&control.getUniqueId().equals(source))controlXp+=orb.getExperience();c.own(orb);}}
    /** Counts the injected diamond sentinel and owns every observed item entity for cleanup. */
    @EventHandler(priority=EventPriority.MONITOR)public void item(ItemSpawnEvent e){if(e.getEntity().getItemStack().getType()==Material.DIAMOND)items++;c.own(e.getEntity());}
}
