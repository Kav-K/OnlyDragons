package com.kaveenk.onlydragons.gametests.enchant;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Real bow/physical Duplex trials through the deployed dragon and proc authority.
 * Independent totals distinguish full contribution from active-Tempo proc HP. An
 * explicit pause of a real airborne arrow permits a slot swap without changing its
 * identity or capture; release resumes native flight. Original eligible expiry is
 * checked before/at its tick, so ineligible Duplex activity cannot silently refresh it.
 */
public final class TempoGhostScenario implements Scenario, Listener {
    ScenarioContext c; PlayerFixture players; DevelopmentDragonService dragons; EnderDragon dragon;
    int commands,releases; boolean veto,hold; Arrow held; Vector velocity;
    final List<com.kaveenk.onlydragons.domain.projectile.SettledHit> settled=new ArrayList<>();
    final Set<UUID> collisions=new HashSet<>(); final List<Map<String,Object>> trials=new ArrayList<>();
    /**
     * Owns the settled-hit subscription and generation reset before admitting the real actor.
     * @param context server-thread report/resource owner
     * @throws Exception if the staged actor plan cannot be admitted
     */
    public void start(ScenarioContext context) throws Exception {
        c=context;c.mechanicRevision("tempo-ghost-v2");players=new PlayerFixture(c);dragons=c.production().dragons();c.listen(this);
        var observer=c.production().combat().observeSettled(settled::add);
        c.cleanup("tempo-observer",observer::close);
        c.cleanup("tempo-dragon",()->{if(dragons.generation().isPresent()&&c.production().combat().ownsEntity(dragons.view().orElseThrow().entityId()))dragons.reset(dragons.generation().orElseThrow());});
        players.await("tempo actor",300,players::allOnline,this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    /**
     * Uses real permission/setup/spawn/status commands, then stages the two separate ultimate bows.
     */
    void setup() {
        var p=players.player("alpha");p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);
        p.getInventory().clear();p.getInventory().setHeldItemSlot(0);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)c.tickChunk(p.getWorld().getChunkAt(x,z));
        players.permission("alpha","onlydragons.practice",false);
        command("denied",()->{
            c.check("training_permission_denied",true,dragons.generation().isEmpty());
            players.permission("alpha","onlydragons.practice",true);
            command("setup",()->command("spawn",()->{
                dragon=(EnderDragon)Bukkit.getEntity(view().entityId());
                c.check("training_selection",true,view().target().maxHealth()==100_000&&dragons.selection().orElseThrow().maxHealth()==100_000
                        &&dragons.selection().orElseThrow().identity().id().equals("test_dragon")&&dragons.selection().orElseThrow().combatProfile().equals(CombatProfile.tempoDragon())
                        &&c.production().dragonDefinitions().snapshot().select("test_dragon").maxHealth()==1000&&dragon.getHealth()==200);
                command("status",()->{
                    c.check("training_status",true,dragons.status().contains("remainingHP=100000.0")&&dragons.status().contains("maxHP=100000.0")&&dragons.status().contains("rewards disabled"));
                    players.setupItem("alpha",0,c.production().equipment().createLoadout("fatal_tempo"));
                    players.setupItem("alpha",1,c.production().equipment().createLoadout("duplex"));
                    players.setupItem("alpha",9,new ItemStack(Material.ARROW,64));
                    players.request("alpha","duplex-slot");players.await("selected Duplex",80,()->p.getInventory().getHeldItemSlot()==1,()->{
                        c.production().equipment().bonus(p,StatKey.FEROCITY,200);
                        players.await("native parts",80,()->dragon.getParts().stream().allMatch(part->part.getLocation().getY()>75),this::p08);
                    });
                });
            }));
        });
    }
    /**
     * Requires vetoed native contacts to earn nothing before proving the unbuffed Duplex/Ferocity counts.
     */
    void p08() {
        veto=true;draw("veto",()->players.await("veto physical arrows",100,()->collisions.size()>=2,()->{
            c.later(3,()->{
                c.check("rejected_duplex_no_damage_or_buff",true,view().acceptedImpacts()==0&&view().target().currentHealth()==100_000&&view().procs().tempoStates()==0);
                veto=false;trial("p08",6,360,360,200,()->{
                    var impacts=view().impacts();
                    c.check("p08_counts_and_scaled_children",true,impacts.stream().filter(r->r.kind()==DamageResult.Kind.PHYSICAL).count()==1
                            &&impacts.stream().filter(r->r.kind()==DamageResult.Kind.DUPLEX).count()==1
                            &&impacts.stream().filter(r->r.kind()==DamageResult.Kind.FEROCITY&&r.amounts().contributionDamage()==100).count()==2
                            &&impacts.stream().filter(r->r.kind()==DamageResult.Kind.FEROCITY&&r.amounts().contributionDamage()==20).count()==2);
                    tempo();
                });
            });
        }));
    }
    /**
     * Pauses the captured Tempo arrow for a real swap, resumes it, and checks the original expiry window.
     */
    void tempo() {
        players.request("alpha","tempo-slot");players.await("selected FT",80,()->players.player("alpha").getInventory().getHeldItemSlot()==0,()->{
            c.production().equipment().bonus(players.player("alpha"),StatKey.FEROCITY,175);hold=true;
            long before=view().acceptedImpacts();draw("tempo",()->{
                c.check("ft_arrow_captured_before_swap",true,held!=null&&held.isValid()&&view().acceptedImpacts()==before);
                players.request("alpha","swap-slot");players.await("actual swap with airborne FT",80,()->players.player("alpha").getInventory().getHeldItemSlot()==1,()->{
                    c.production().equipment().bonus(players.player("alpha"),StatKey.FEROCITY,200);
                    hold=false;held.setGravity(true);held.setVelocity(velocity);
                    players.await("FT source and fixed children",100,()->view().acceptedImpacts()==before+3&&view().procs().queued()==0,()->{
                        c.check("ft_pre_hit_unbuffed_after_swap",true,view().target().currentHealth()==99340&&credit()==660
                                &&settled.getLast().projectile().shot().stats().effective(StatKey.FEROCITY)==200
                                &&settled.getLast().projectile().shot().enchantments().stream().anyMatch(e->e.id().equals("fatal_tempo")&&e.level()==5)
                                &&view().impacts().subList(6,9).stream().allMatch(r->r.amounts().actualHealthDamage()==100&&r.effectiveFerocity()==200));
                        long lastFtTick=view().impacts().subList(6,9).stream().mapToLong(DamageResult::tick).max().orElseThrow();
                        trial("buffed",12,420,720,500,()->{
                            c.check("duplex_global_tempo_without_refresh",true,view().target().currentHealth()==98920&&credit()==1380);
                            long originalExpiry=lastFtTick+60;
                            long duplexTick=view().impacts().getLast().tick();
                            long now=Integer.toUnsignedLong(Bukkit.getCurrentTick());
                            c.check("original_ft_expiry_window",true,duplexTick>lastFtTick&&now<originalExpiry-1);
                            c.observe("expiryTicks",Map.of("lastEligibleFt",lastFtTick,"lastIneligibleDuplex",duplexTick,"originalExpiry",originalExpiry));
                            if(now>=originalExpiry-1)throw new IllegalStateException("Missed original FT expiry observation window");
                            c.later(originalExpiry-1-now,()->{
                                c.check("tempo_present_before_original_expiry",1,view().procs().tempoStates());
                                c.later(1,()->{
                                    c.check("original_ft_expiry_tick",originalExpiry,Integer.toUnsignedLong(Bukkit.getCurrentTick()));
                                    c.check("duplex_children_did_not_keep_tempo_alive",0,view().procs().tempoStates());
                                    trial("expired",6,360,360,200,this::lethal);
                                });
                            });
                        });
                    });
                });
            });
        });
    }
    /**
     * Requires exact new impact count, HP delta, contribution delta and captured physical identities.
     * Native HP is compared to the pinned public double-to-float projection, independently
     * of the domain HP total.
     */
    void trial(String step,int expectedCount,double hp,double score,double f,Runnable next) {
        long before=view().acceptedImpacts();double oldHp=view().target().currentHealth(),oldCredit=credit();
        draw(step,()->players.await(step+" expected procs",120,()->view().acceptedImpacts()>=before+expectedCount&&view().procs().queued()==0,()->{
            var results=view().impacts().stream().skip(before).toList();
            c.check(step+"_exact_accounting",true,view().acceptedImpacts()==before+expectedCount&&oldHp-view().target().currentHealth()==hp&&credit()-oldCredit==score);
            // Paper 121 setHealth(double) narrows to float; compare that exact public projection.
            double nativeExpected=(double)(float)(200*view().target().currentHealth()/view().target().maxHealth());
            c.check(step+"_native_health_projection",nativeExpected,dragon.getHealth());
            c.check(step+"_physical_event_identity",true,results.stream().filter(r->r.kind()!=DamageResult.Kind.FEROCITY).count()==2
                    &&results.stream().filter(r->r.kind()!=DamageResult.Kind.FEROCITY).allMatch(r->collisions.contains(r.origin().projectileId())&&r.effectiveFerocity()==f&&Bukkit.getEntity(r.origin().projectileId())==null));
            trials.add(Map.of("trial",step,"hp",oldHp-view().target().currentHealth(),"credit",credit()-oldCredit,"count",results.size(),"nativeHealth",dragon.getHealth(),"results",results.toString()));next.run();
        }));
    }
    /**
     * Checks one frozen training selection with full overkill credit and no late proc mutation.
     */
    void lethal() {
        var selection=dragons.selection().orElseThrow();
        c.production().equipment().bonus(players.player("alpha"),StatKey.WEAPON_DAMAGE,99900);
        draw("lethal",()->players.await("training frozen result",100,()->view().completion().isPresent(),()->{
            var result=view().completion().orElseThrow();
            c.check("training_frozen_selection_and_overkill",true,result.selection().orElseThrow().equals(selection)
                    &&result.participants().get(players.identity("alpha")).actualHealthDamage()==100000
                    &&result.participants().get(players.identity("alpha")).contributionDamage()==101740);
            c.later(12,()->{c.check("training_late_procs_no_extra_credit",true,view().completion().orElseThrow().equals(result)&&view().procs().queued()==0);reset();});
        }));
    }
    /**
     * Checks reset cleanup and separates current standard/training policy from legacy calibration selection.
     */
    void reset() {
        UUID old=dragon.getUniqueId();command("reset",()->{
            c.check("training_reset_cleanup",true,Bukkit.getEntity(old)==null&&c.production().combat().activeCount()==0&&view().completion().isPresent()
                    &&view().procs().queued()==0&&c.production().bows().capacityUsed()==0&&c.production().bows().continuity().tickets().reservedCount()==0);
            command("ordinary",()->{
                c.check("ordinary_spawn_uses_tempo_v2",true,view().target().maxHealth()==1000&&dragons.selection().orElseThrow().combatProfile().equals(CombatProfile.tempoDragon()));
                command("reset-again",()->{
                    command("calibration",()->{
                        c.check("explicit_calibration_preserves_v1",true,dragons.selection().orElseThrow().equals(c.production().dragonDefinitions().snapshot().select("test_dragon")));
                        command("calibration-reset",()->finish());
                    });
                });
            });
        });
    }
    /**
     * Publishes trial/collision provenance and completes only after the real actor quits.
     */
    void finish() {
                    c.observe("trials",trials);c.observe("physicalCollisions",collisions.stream().map(UUID::toString).toList());c.observe("playerActions",players.journal());
                    players.request("alpha","quit");players.await("quit",100,()->players.quits("alpha")==1,c::finish);
    }
    /**
     * Reads the immutable production view for the service's current generation.
     */
    ManagedCombatService.View view(){return dragons.view().orElseThrow();}
    /**
     * Reads alpha's full contribution, using zero before first accepted participation.
     */
    double credit(){var contribution=view().contributions().get(players.identity("alpha"));return contribution==null?0:contribution.contributionDamage();}
    /**
     * Positions setup relative to a measured real part box, then requests actual full draw and release.
     */
    void draw(String step,Runnable next) {
        var p=players.player("alpha");var box=dragon.getParts().stream().max(Comparator.comparingDouble(part->part.getBoundingBox().getVolume())).orElseThrow().getBoundingBox();
        var origin=box.getCenter().add(new Vector(0,0,-12));players.setupPosition("alpha",new Location(p.getWorld(),origin.getX(),origin.getY()-p.getEyeHeight(),origin.getZ(),0,0));p.setVelocity(new Vector());
        int before=releases;players.request("alpha",step+"-use");players.await("draw "+step,80,p::isHandRaised,()->c.later(22,()->{
            players.request("alpha",step+"-release");players.await("release "+step,80,()->releases>before,next::run);
        }));
    }
    /**
     * Waits for native command dispatch and two settling ticks before reading production state.
     */
    void command(String step,Runnable next){int before=commands;players.request("alpha",step);players.await("command "+step,80,()->commands>before,()->c.later(2,next::run));}
    /**
     * Counts native command callbacks in this isolated actor plan.
     * @param e real command event
     */
    @EventHandler(priority=EventPriority.MONITOR)public void command(PlayerCommandPreprocessEvent e){commands++;}
    /**
     * Counts actual player bow release callbacks, independently of action requests.
     * @param e native bow release event
     */
    @EventHandler(priority=EventPriority.MONITOR)public void release(EntityShootBowEvent e){if(e.getEntity() instanceof Player)releases++;}
    /**
     * Applies only the explicit airborne-pause setup, retaining the original velocity for resumption.
     * @param e native projectile launch event
     */
    @EventHandler(priority=EventPriority.MONITOR)public void launch(ProjectileLaunchEvent e){if(hold&&e.getEntity() instanceof Arrow arrow){held=arrow;velocity=arrow.getVelocity().clone();arrow.setGravity(false);arrow.setVelocity(new Vector());}}
    /**
     * Records real target/part collisions and applies the named trial veto when enabled.
     * @param e native projectile hit event
     */
    @EventHandler(priority=EventPriority.HIGHEST)public void hit(ProjectileHitEvent e){Entity parent=e.getHitEntity() instanceof EnderDragonPart part?part.getParent():e.getHitEntity();if(parent!=null&&dragon!=null&&parent.getUniqueId().equals(dragon.getUniqueId())){collisions.add(e.getEntity().getUniqueId());if(veto)e.setCancelled(true);}}
}
