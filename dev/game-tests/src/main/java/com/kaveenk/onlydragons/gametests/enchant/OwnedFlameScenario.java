package com.kaveenk.onlydragons.gametests.enchant;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Real native releases/shortbow inputs and physical hits into the stock production combat authority. */
public final class OwnedFlameScenario implements Scenario,Listener {
    ScenarioContext c;PlayerFixture players;DamageObservationProbe probe;TargetBackend backend;UUID generation;
    final ItemRegistry catalog=CalibrationLoadouts.compatibleRegistry();final WeaponItemCodec codec=new WeaponItemCodec(catalog);
    final List<SettledHit> settled=new ArrayList<>();final Set<UUID> collisions=new HashSet<>();
    final List<Map<String,Object>> evidence=new ArrayList<>();int releases,inputs,fireDeaths,fireXp;UUID lethalEntity;boolean veto,hold;
    final List<Arrow> held=new ArrayList<>();final List<Vector> velocities=new ArrayList<>();
    public void start(ScenarioContext context) throws Exception {
        c=context;c.mechanicRevision("owned-flame-v1");players=new PlayerFixture(c);probe=new DamageObservationProbe(c);c.listen(this);
        var observer=c.production().combat().observeSettled(settled::add);c.cleanup("flame-observer",observer::close);
        c.cleanup("flame-target",()->{if(c.production().combat().activeCount()>0)c.production().combat().reset(players.identity("alpha"));if(backend!=null)backend.close();});
        players.await("two flame actors",300,players::allOnline,this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    void setup() {
        var world=players.player("alpha").getWorld();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)c.tickChunk(world.getChunkAt(x,z));
        for(String actor:List.of("alpha","beta")) {
            var p=players.player(actor);p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);
            p.getInventory().clear();p.getInventory().setHeldItemSlot(0);p.setLevel(0);p.setExp(0);
            players.setupPosition(actor,new Location(world,actor.equals("alpha")?0:10,100,-12,0,0));
            players.setupItem(actor,9,new ItemStack(Material.ARROW,64));players.permission(actor,"onlydragons.fire",true);
        }
        open(true,100000,CombatProfile.calibration(),()->{
            equip("alpha",false,Map.of("flame",2,"infinite_quiver",10),0,0);
            players.setupItem("alpha",1,codec.encode(catalog.create("ordinary_v4")));
            int ammoBefore=ammo();hold=true;
            var session=c.production().bows().currentSession(players.identity("alpha")).orElseThrow();
            draw("alpha","baseline",()->players.await("captured live Flame arrow",80,()->held.size()==1,()->{
                UUID projectile=held.getFirst().getUniqueId();players.request("alpha","baseline-swap");
                players.await("actual no-Flame slot swap",80,()->players.player("alpha").getInventory().getHeldItemSlot()==1,()->{
                    c.check("swap_preserves_live_captured_session",true,c.production().bows().isCurrentSession(players.identity("alpha"),session));
                    hold=false;held.getFirst().setGravity(true);held.getFirst().setVelocity(velocities.getFirst());held.clear();velocities.clear();
                    players.await("baseline accepted physical collision",100,()->settled.stream().anyMatch(h->h.accepted()&&h.projectile().shot().projectileId().equals(projectile)),()->{
                        c.check("baseline_real_physical_collision",true,collisions.contains(projectile)&&Bukkit.getEntity(projectile)==null);
                        var traces=c.production().bows().trace().stream().filter(t->t.kind().equals("ammo-settled")&&t.projectileId().equals(projectile)).toList();
                        c.check("stock_quiver_single_captured_decision",1,traces.size());
                        String detail=traces.getFirst().detail();
                        double sample=Double.parseDouble(detail.substring(detail.indexOf("OptionalDouble[")+15,detail.indexOf("]")));
                        c.check("stock_quiver_survival_conservation",true,sample>=0&&sample<1&&detail.contains("level=10")
                                &&detail.contains("saved="+(sample<.5))&&ammo()==ammoBefore-(sample<.5?0:1));
                        c.observe("stockQuiver",Map.of("trace",detail,"before",ammoBefore,"after",ammo()));
                        awaitBurns(0,()->{
                            var physical=physical().getFirst();var fire=fire();
                            c.check("immune_dragon_three_owned_ticks",true,fire.size()==3&&fire.stream().allMatch(r->near(r.amounts().contributionDamage(),6))
                                    &&fire.stream().map(r->r.tick()-physical.tick()).toList().equals(List.of(20L,40L,60L)));
                            c.check("live_swap_keeps_captured_flame_source",true,fire.stream().allMatch(r->r.parentImpactId().orElseThrow().equals(physical.impactId()))
                                    &&players.player("alpha").getInventory().getHeldItemSlot()==1);
                            accounting("baseline",118,Map.of("alpha",118d));capture("baseline");
                            players.request("alpha","baseline-return");players.await("return weapon slot",80,()->players.player("alpha").getInventory().getHeldItemSlot()==0,this::refresh);
                        });
                    });
                });
            }));
        });
    }
    void refresh() {
        reset();open(true,100000,CombatProfile.calibration(),()->{
            equip("alpha",true,Map.of("flame",2),0,0);click("refresh-start",()->{
                c.later(7,()->{equip("alpha",true,Map.of("flame",1),0,0);click("refresh-weak",()->{
                    players.await("first tick retains stronger source",60,()->!fire().isEmpty(),()->{equip("alpha",true,Map.of("flame",2),100,0);click("refresh-strong",()->awaitBurns(0,()->{
                        var hits=physical();var start=hits.getFirst();var strong=hits.getLast();var fires=fire();
                        var expectedTicks=new ArrayList<Long>();for(long tick=start.tick()+20;tick<=strong.tick()+60;tick+=20)expectedTicks.add(tick);
                        c.check("refresh_preserves_cadence_and_expiry",expectedTicks,fires.stream().map(DamageResult::tick).toList());
                        c.check("weak_retains_strong_source_new_strong_adopted",true,fires.stream().allMatch(r->
                                r.parentImpactId().orElseThrow().equals(r.tick()<strong.tick()?start.impactId():strong.impactId())
                                &&near(r.amounts().contributionDamage(),r.tick()<strong.tick()?6:12)));
                        double total=400+expectedTicks.stream().mapToDouble(t->t<strong.tick()?6:12).sum();
                        accounting("refresh",total,Map.of("alpha",total));capture("refresh");owners();
                    }));});
                });});
            });
        });
    }
    void owners() {
        reset();open(true,100000,CombatProfile.calibration(),()->{
            equip("beta",false,Map.of("duplex",5),0,0);shoot("beta","vulnerability-high",2,()->{
                c.check("duplex_without_flame_does_not_ignite",true,c.production().combat().fireMetrics(generation).burns()==0&&fire().isEmpty());
                equip("alpha",false,Map.of("duplex",3),0,0);shoot("alpha","vulnerability-mid",2,()->{
                    equip("alpha",false,Map.of("duplex",1),0,0);shoot("alpha","vulnerability-low",2,()->{
                        long expires=physical().getLast().tick()+1200;
                        equip("beta",false,Map.of("flame",1),0,0);shoot("beta","owner-beta",1,()->{
                            equip("alpha",true,Map.of("flame",2),0,0);click("owner-alpha",()->{
                                c.later(23,()->{
                                    c.check("two_burn_owners_strongest_vulnerability",true,fire().stream().anyMatch(r->r.ownerId().equals(players.identity("alpha"))&&near(r.amounts().contributionDamage(),9))
                                            &&fire().stream().anyMatch(r->r.ownerId().equals(players.identity("beta"))&&near(r.amounts().contributionDamage(),4.5)));
                                    long exitTick=now();
                                    players.setupPosition("beta",new Location(players.player("beta").getWorld(),60,100,-12));
                                    int before=fire().size();c.later(23,()->{
                                        var later=fire().subList(before,fire().size());
                                        c.check("exit_clears_only_owner_burn_and_vulnerability",true,!later.isEmpty()&&later.stream().allMatch(r->r.ownerId().equals(players.identity("alpha"))&&near(r.amounts().contributionDamage(),6.6))
                                                &&c.production().combat().fireMetrics(generation).vulnerabilities()==1);
                                        awaitBurns(0,()->{
                                            var burns=fire();
                                            double alpha=316+burns.stream().filter(r->r.ownerId().equals(players.identity("alpha"))).mapToDouble(r->r.tick()<=exitTick?9:6.6).sum();
                                            double beta=220+burns.stream().filter(r->r.ownerId().equals(players.identity("beta"))).count()*4.5;
                                            c.check("all_owner_fire_amounts_and_attribution",true,burns.stream().allMatch(r->near(r.amounts().contributionDamage(),
                                                    r.ownerId().equals(players.identity("beta"))?4.5:r.tick()<=exitTick?9:6.6))
                                                    &&burns.stream().filter(r->r.ownerId().equals(players.identity("beta"))).allMatch(r->r.tick()<=exitTick));
                                            accounting("owners",alpha+beta,Map.of("alpha",alpha,"beta",beta));
                                            capture("owners");long wait=Math.max(1,expires-now());c.later(wait,()->{
                                                c.check("vulnerability_exclusive_expiry",0,c.production().combat().fireMetrics(generation).vulnerabilities());
                                                equip("alpha",false,Map.of("flame",2),0,0);long count=view().acceptedImpacts();
                                                shoot("alpha","expired",1,()->awaitBurns(0,()->{
                                                    c.check("expired_vulnerability_no_fire_boost",true,view().impacts().stream().filter(r->r.kind()==DamageResult.Kind.FIRE&&r.tick()>=expires).allMatch(r->near(r.amounts().contributionDamage(),6))
                                                            &&view().acceptedImpacts()==count+4);
                                                    capture("expired");nativeControl();
                                                }));
                                            });
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }
    void nativeControl() {
        reset();open(false,1000,CombatProfile.calibration(),()->{
            var unmanaged=c.own(players.player("alpha").getWorld().spawn(new Location(players.player("alpha").getWorld(),8,100,0),Cow.class));
            unmanaged.setAI(false);unmanaged.setGravity(false);probe.watch(unmanaged);double nativeHp=unmanaged.getHealth();
            backend.entity().setFireTicks(100);unmanaged.setFireTicks(100);c.later(30,()->{
                c.check("native_fire_suppressed_only_on_managed_target",true,view().acceptedImpacts()==0&&view().target().currentHealth()==1000&&backend.entity().getHealth()==20
                        &&unmanaged.getHealth()<nativeHp&&probe.events().stream().anyMatch(e->e.target().equals(backend.entity().getUniqueId())&&e.cancelled()));
                unmanaged.remove();backend.entity().setFireTicks(0);veto=true;equip("alpha",false,Map.of("flame",2),0,0);
                shoot("alpha","veto",1,()->{veto=false;c.later(65,()->{
                    c.check("cancelled_physical_has_no_burn_or_credit",true,view().acceptedImpacts()==0&&c.production().combat().fireMetrics(generation).burns()==0);capped();
                });});
            });
        });
    }
    void capped() {
        reset();open(true,1000,CombatProfile.dragonExperiment(new MechanicRevision("flame-cap","v1"),0),()->{
            equip("alpha",false,Map.of("flame",2),23900,0);shoot("alpha","cap",1,()->awaitBurns(0,()->{
                c.check("fire_uses_capped_credit_full_hp_once",true,near(physical().getFirst().amounts().contributionDamage(),10)
                        &&fire().size()==3&&fire().stream().allMatch(r->near(r.amounts().contributionDamage(),.6)&&near(r.amounts().requestedHealthDamage(),.6)));
                accounting("cap",11.8,Map.of("alpha",11.8));capture("cap");tempo();
            }));
        });
    }
    void tempo() {
        reset();open(true,10000,CombatProfile.calibration(),()->{
            equip("alpha",false,Map.of("flame",2,"fatal_tempo",5),0,100);shoot("alpha","tempo",1,()->awaitBurns(0,()->{
                c.check("fire_no_recursive_ferocity_or_tempo_refresh",true,view().acceptedImpacts()==5&&fire().size()==3
                        &&view().impacts().stream().filter(r->r.kind()==DamageResult.Kind.FEROCITY).count()==1&&view().procs().queued()==0);
                // An ordinary 100-Ferocity follow-up after original/proc expiry must still have exactly one child.
                equip("alpha",false,Map.of(),0,100);long before=view().acceptedImpacts();
                shoot("alpha","after-tempo",1,()->c.later(12,()->{
                    c.check("fire_did_not_extend_tempo",before+2,view().acceptedImpacts());capture("tempo");lethal();
                }));
            }));
        });
    }
    void lethal() {
        reset();open(true,105,CombatProfile.calibration(),()->{
            lethalEntity=backend.entity().getUniqueId();equip("alpha",false,Map.of("flame",2),0,0);shoot("alpha","lethal",1,()->{
                players.await("owned fire lethal",100,()->view().completion().isPresent(),()->{
                    var frozen=view().completion().orElseThrow();
                    c.check("fire_lethal_full_credit_clipped_hp",true,view().acceptedImpacts()==2&&fire().size()==1&&near(fire().getFirst().amounts().actualHealthDamage(),5)
                            &&near(frozen.participants().get(players.identity("alpha")).contributionDamage(),106));
                    players.await("native FIRE lethal removal",400,()->Bukkit.getEntity(lethalEntity)==null,()->c.later(20,()->{c.check("terminal_no_late_fire_or_rewards",true,view().completion().orElseThrow().equals(frozen)&&view().acceptedImpacts()==2
                            &&c.production().combat().fireMetrics(generation).burns()==0&&players.player("alpha").getLevel()==0&&players.player("alpha").getExp()==0
                            &&players.player("alpha").getInventory().all(Material.DIAMOND).isEmpty()&&fireDeaths==1&&fireXp==0);capture("lethal");lifecycle();}));
                });
            });
        });
    }
    void lifecycle() {
        reset();players.setupPosition("beta",new Location(players.player("beta").getWorld(),10,100,-12));
        open(true,10000,CombatProfile.calibration(),()->{
            equip("beta",false,Map.of("flame",2,"duplex",5),0,0);shoot("beta","quit",2,()->{
                players.request("beta","quit");players.await("actual effect owner quit",100,()->players.quits("beta")==1,()->{
                    c.check("quit_clears_owned_fire_and_vulnerability",true,c.production().combat().fireMetrics(generation).burns()==0&&c.production().combat().fireMetrics(generation).vulnerabilities()==0);
                    equip("alpha",false,Map.of("flame",2),0,0);hold=true;
                    long oldCount=view().acceptedImpacts();
                    draw("alpha","old-session",()->players.await("held captured shot",80,()->held.size()==1,()->{
                        var old=c.production().bows().currentSession(players.identity("alpha")).orElseThrow();
                        c.production().bows().clearSession(players.identity("alpha"),old,false);c.production().combat().playerEnded(players.identity("alpha"));
                        hold=false;for(int i=0;i<held.size();i++){held.get(i).setGravity(true);held.get(i).setVelocity(velocities.get(i));}
                        players.await("late old-session physical",100,()->settled.stream().anyMatch(h->h.projectile().sessionToken().equals(old)&&h.accepted()),()->{
                            c.check("old_session_hit_scores_without_new_burn",true,c.production().combat().fireMetrics(generation).burns()==0
                                    &&view().acceptedImpacts()==oldCount+1&&near(view().impacts().getLast().amounts().contributionDamage(),100));
                            held.clear();velocities.clear();equip("alpha",true,Map.of("flame",2,"duplex",5),0,0);
                            click("reset-burn",2,()->{
                                liveEffects("reset",true);
                                UUID oldGeneration=generation;reset();c.check("reset_clears_all_effects",true,c.production().combat().fireMetrics(oldGeneration).burns()==0&&c.production().combat().fireMetrics(oldGeneration).vulnerabilities()==0);
                                deathAndDisable();
                            });
                        });
                    }));
                });
            });
        });
    }
    void deathAndDisable() {
        open(true,10000,CombatProfile.calibration(),()->{
            equip("alpha",true,Map.of("flame",2,"duplex",5),0,0);click("death-burn",2,()->{
                liveEffects("death",true);
                var p=players.player("alpha");p.setHealth(0);
                c.check("death_clears_owned_fire_and_vulnerability",true,c.production().combat().fireMetrics(generation).burns()==0
                        &&c.production().combat().fireMetrics(generation).vulnerabilities()==0);
                // Let the client observe the death packet before requesting its respawn action.
                c.later(3,()->{players.request("alpha","respawn");players.await("actual respawn",100,()->!players.player("alpha").isDead(),()->{
                    var live=players.player("alpha");live.setGameMode(GameMode.SURVIVAL);live.setAllowFlight(true);live.setFlying(true);live.setInvulnerable(true);
                    players.setupPosition("alpha",new Location(live.getWorld(),-1,100,-12));live.getInventory().clear();live.getInventory().setHeldItemSlot(0);
                    players.setupItem("alpha",9,new ItemStack(Material.ARROW,16));
                    equip("alpha",true,Map.of("flame",2),0,0);click("disable-burn",()->{
                        liveEffects("disable",false);c.production().combat().close();c.check("disable_clears_all_effects",true,c.production().combat().activeCount()==0&&c.production().combat().fireMetrics(generation).burns()==0&&c.production().bows().capacityUsed()==0);
                        c.observe("trials",evidence);c.observe("playerActions",players.journal());
                        players.request("alpha","quit");players.await("final quit",100,()->players.quits("alpha")==1,c::finish);
                    });
                });});
            });
        });
    }
    void equip(String actor,boolean shortbow,Map<String,Integer> enchants,double damage,double ferocity) {
        players.setupItem(actor,0,codec.encode(catalog.edit(catalog.create(shortbow?"shortbow_v4":"ordinary_v4"),enchants,List.of())));
        c.production().equipment().bonus(players.player(actor),StatKey.WEAPON_DAMAGE,damage);c.production().equipment().bonus(players.player(actor),StatKey.FEROCITY,ferocity);
    }
    void open(boolean dragon,double hp,CombatProfile profile,Runnable next) {
        var location=new Location(players.player("alpha").getWorld(),0,100,0);
        backend=dragon?new DragonBackend(location,c.production().bows().continuity().tickets(),r->{},b->{}):new DummyBackend(location);probe.watch(backend.entity());
        generation=c.production().combat().open(players.identity("alpha"),backend,org.bukkit.util.BoundingBox.of(location,24,24,24),hp,0,"test",profile,Optional.empty(),()->.99);
        players.await("flame geometry",100,()->!(backend.entity() instanceof EnderDragon d)||d.getParts().stream().allMatch(p->p.getLocation().getY()>75),next::run);
    }
    void reset(){c.production().combat().reset(players.identity("alpha"));if(backend!=null)backend.close();}
    ManagedCombatService.View view(){return c.production().combat().view(generation).orElseThrow();}
    List<DamageResult> physical(){return view().impacts().stream().filter(r->r.kind()==DamageResult.Kind.PHYSICAL||r.kind()==DamageResult.Kind.DUPLEX).toList();}
    List<DamageResult> fire(){return view().impacts().stream().filter(r->r.kind()==DamageResult.Kind.FIRE).toList();}
    void awaitBurns(int count,Runnable next){players.await("owned fire expiry",120,()->c.production().combat().fireMetrics(generation).burns()==count,next::run);}
    void accounting(String id,double hp,Map<String,Double> credits) {
        c.check(id+"_exact_accounting",true,near(view().target().maxHealth()-view().target().currentHealth(),hp)&&credits.entrySet().stream().allMatch(e->near(view().contributions().get(players.identity(e.getKey())).contributionDamage(),e.getValue())));
        c.check(id+"_native_projection",(double)(float)((backend.airborne()?200:20)*view().target().currentHealth()/view().target().maxHealth()),backend.entity().getHealth());
    }
    void capture(String id){evidence.add(Map.of("id",id,"generation",generation.toString(),"health",view().target().currentHealth(),"impacts",view().impacts().toString(),"contributions",view().contributions().toString()));}
    void aim(String actor) {
        var p=players.player(actor);var entity=backend.entity();var box=entity instanceof EnderDragon d?d.getParts().stream().max(Comparator.comparingDouble(part->part.getBoundingBox().getVolume())).orElseThrow().getBoundingBox():entity.getBoundingBox();
        var origin=box.getCenter().add(new Vector(backend.airborne()?(actor.equals("alpha")?-1:1):0,0,-12));players.setupPosition(actor,new Location(p.getWorld(),origin.getX(),origin.getY()-p.getEyeHeight(),origin.getZ(),0,0));p.setVelocity(new Vector());
    }
    void shoot(String actor,String step,int count,Runnable next) {
        int before=settled.size();draw(actor,step,()->players.await("physical flame "+step,100,()->settled.size()>=before+count,()->{
            var hits=settled.subList(before,settled.size());c.check(step+"_real_physical_collision",true,hits.size()==count&&hits.stream().allMatch(h->(veto?!h.accepted():h.accepted())&&collisions.contains(h.projectile().shot().projectileId())&&Bukkit.getEntity(h.projectile().shot().projectileId())==null));next.run();
        }));
    }
    void draw(String actor,String step,Runnable next) {
        aim(actor);int before=releases;var p=players.player(actor);players.request(actor,step+"-use");players.await("flame hand raised "+step,80,p::isHandRaised,()->c.later(22,()->{
            players.request(actor,step+"-release");players.await("flame release "+step,80,()->releases>before,next::run);
        }));
    }
    int ammo(){return players.player("alpha").getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum();}
    void liveEffects(String phase,boolean vulnerability){var m=c.production().combat().fireMetrics(generation);c.check(phase+"_live_effect_precondition",true,m.burns()==1&&(!vulnerability||m.vulnerabilities()==1));}
    void click(String step,Runnable next) {click(step,1,next);}
    void click(String step,int count,Runnable next) {
        aim("alpha");int before=settled.size(),input=inputs;players.request("alpha",step);
        players.await("short flame input and collision "+step,100,()->inputs>input&&settled.size()>=before+count,()->{
            c.check(step+"_real_physical_collision",true,settled.size()==before+count&&settled.subList(before,settled.size()).stream().allMatch(h->h.accepted()&&collisions.contains(h.projectile().shot().projectileId())));next.run();
        });
    }
    @EventHandler(priority=EventPriority.MONITOR)public void died(EntityDeathEvent e){if(e.getEntity().getUniqueId().equals(lethalEntity)){fireDeaths++;c.check("fire_native_death_rewards_empty",true,e.getDroppedExp()==0&&e.getDrops().isEmpty());}}
    @EventHandler(priority=EventPriority.MONITOR)public void reward(EntitySpawnEvent e){if(e.getEntity() instanceof ExperienceOrb orb&&lethalEntity!=null&&lethalEntity.equals(orb.getSourceEntityId()))fireXp+=orb.getExperience();}
    static long now(){return Integer.toUnsignedLong(Bukkit.getCurrentTick());}
    static boolean near(double a,double b){return Math.abs(a-b)<1e-7;}
    @EventHandler(priority=EventPriority.MONITOR)public void release(EntityShootBowEvent e){if(e.getEntity() instanceof Player)releases++;}
    @EventHandler(priority=EventPriority.MONITOR)public void input(PlayerInteractEvent e){if(e.getAction().isLeftClick())inputs++;}
    @EventHandler(priority=EventPriority.MONITOR)public void launch(ProjectileLaunchEvent e){if(hold&&e.getEntity() instanceof Arrow arrow){held.add(arrow);velocities.add(arrow.getVelocity().clone());arrow.setGravity(false);arrow.setVelocity(new Vector());}}
    @EventHandler(priority=EventPriority.HIGHEST)public void hit(ProjectileHitEvent e){Entity parent=e.getHitEntity() instanceof EnderDragonPart part?part.getParent():e.getHitEntity();if(parent!=null&&backend!=null&&parent.getUniqueId().equals(backend.entity().getUniqueId())){collisions.add(e.getEntity().getUniqueId());if(veto)e.setCancelled(true);}}
}
