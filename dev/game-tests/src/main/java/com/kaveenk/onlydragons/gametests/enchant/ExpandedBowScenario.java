package com.kaveenk.onlydragons.gametests.enchant;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.paper.item.codec.*;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** Real client bow releases, native collisions, production capture/effects/accounting and catalog routing. */
public final class ExpandedBowScenario implements Scenario, Listener {
    ScenarioContext c; PlayerFixture players; DamageObservationProbe probe; TargetBackend backend; UUID generation;
    final ItemRegistry catalog=CalibrationLoadouts.compatibleRegistry();
    final WeaponItemCodec codec=new WeaponItemCodec(catalog);
    final List<SettledHit> settled=new ArrayList<>(); final Set<UUID> collisions=new HashSet<>();
    final List<Arrow> held=new ArrayList<>(); final List<Vector> velocities=new ArrayList<>();
    final List<Map<String,Object>> trials=new ArrayList<>();
    int releases; boolean veto,hold,nativeGuard;
    public void start(ScenarioContext context) {
        c=context;c.mechanicRevision("expanded-bow-v2");players=new PlayerFixture(c);probe=new DamageObservationProbe(c);c.listen(this);
        var observation=c.production().combat().observeSettled(settled::add);c.cleanup("expanded-observer",observation::close);
        c.cleanup("expanded-target",()->{c.production().combat().reset(players.identity("alpha"));if(backend!=null)backend.close();});
        players.await("expanded actor",300,players::allOnline,this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    void setup() {
        var p=players.player("alpha");p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);
        p.getInventory().clear();p.getInventory().setHeldItemSlot(0);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)c.tickChunk(p.getWorld().getChunkAt(x,z));
        players.permission("alpha","onlydragons.fire",true);
        players.setupPosition("alpha",new Location(p.getWorld(),0,100,-12,0,0));
        players.setupItem("alpha",9,new ItemStack(Material.ARROW,64));
        metadata();
        open(true,100000,0,CombatProfile.calibration(),()->{
            equip("ordinary",Map.of(),0,0);
            trial("legacy",1,100,100,()->{
                c.check("legacy_no_mega",true,!settled.getLast().projectile().shot().overload().megaCritical()
                        &&settled.getLast().projectile().shot().overload().sample().isEmpty());
                equip("gravity_v3",Map.of("gravity",6),0,0);
                trial("gravity",1,140,140,()->{
                    equip("ordinary_v3",Map.of("overload",5),95,0);
                    trial("zero-chance",1,155,155,()->{
                        var capture=settled.getLast().projectile().shot().overload();
                        c.check("overload_zero_probability_capture",true,capture.rawCritChance()==100&&!capture.megaCritical()&&capture.sample().isPresent());
                        combo();swap();
                    });
                });
            });
        });
    }
    void metadata() {
        boolean preserved=true;
        for(String id:CalibrationLoadouts.registry().definitions().keySet()) {
            var old=CalibrationLoadouts.registry().create(id);
            var stack=ItemStack.deserializeBytes(new WeaponItemCodec(CalibrationLoadouts.registry()).encode(old).serializeAsBytes());
            var result=(ItemReadResult.Valid)codec.decode(stack);
            preserved &= result.item().instance().equals(old);
        }
        c.check("legacy_native_identity",true,preserved);
        var expected = new TreeMap<String,List<Double>>(Map.of(
                "ordinary_v3",List.of(0d,50d,0d), "crit_v3",List.of(100d,50d,0d),
                "ferocity_25_v3",List.of(0d,50d,25d), "ferocity_100_v3",List.of(0d,50d,100d),
                "ferocity_500_v3",List.of(0d,50d,500d), "tracer_v3",List.of(0d,50d,0d),
                "duplex_v3",List.of(0d,50d,0d), "fatal_tempo_v3",List.of(0d,50d,25d),
                "shortbow_v1_v3",List.of(0d,50d,0d)));
        expected.put("overload_v3",List.of(200d,55d,0d));
        expected.put("gravity_v3",List.of(0d,50d,0d));
        c.check("expanded_all_preset_ids",expected.keySet(),CalibrationLoadouts.expandedRegistry().definitions().keySet());
        boolean expandedRoundtrips=true;
        for (var entry:expected.entrySet()) {
            var original=catalog.create(entry.getKey());
            var bytes=ItemStack.deserializeBytes(codec.encode(original).serializeAsBytes());
            var decoded=(ItemReadResult.Valid)codec.decode(bytes);
            var snapshot=c.production().equipment().refresh(players.identity("alpha"),bytes,null).stats().snapshot();
            expandedRoundtrips &= decoded.item().instance().equals(original)&&snapshot.raw(StatKey.WEAPON_DAMAGE)==100
                    &&List.of(snapshot.raw(StatKey.CRIT_CHANCE),snapshot.raw(StatKey.CRIT_DAMAGE),snapshot.raw(StatKey.FEROCITY)).equals(entry.getValue());
        }
        c.check("expanded_all_native_snapshots",true,expandedRoundtrips);
        var instance=catalog.edit(catalog.create("ordinary_v3"),Map.of("overload",5,"gravity",6),List.of());
        var stack=ItemStack.deserializeBytes(codec.encode(instance).serializeAsBytes());
        c.check("expanded_native_identity",true,instance.equals(((ItemReadResult.Valid)codec.decode(stack)).item().instance()));
        var stats=c.production().equipment().refresh(players.identity("alpha"),stack,null);
        c.check("overload_stats_once",true,stats.stats().snapshot().raw(StatKey.CRIT_CHANCE)==5&&stats.stats().snapshot().raw(StatKey.CRIT_DAMAGE)==55);
        for(String id:List.of("infinite_quiver","flame")) {
            var forged=stack.clone();var meta=forged.getItemMeta();var root=meta.getPersistentDataContainer().get(WeaponItemCodec.ROOT,PersistentDataType.TAG_CONTAINER);
            var levels=root.get(new NamespacedKey("onlydragons","enchants"),PersistentDataType.TAG_CONTAINER);
            levels.set(new NamespacedKey("onlydragons",id),PersistentDataType.INTEGER,1);
            root.set(new NamespacedKey("onlydragons","enchants"),PersistentDataType.TAG_CONTAINER,levels);
            meta.getPersistentDataContainer().set(WeaponItemCodec.ROOT,PersistentDataType.TAG_CONTAINER,root);forged.setItemMeta(meta);
            c.check(id+"_unavailable_pdc",true,codec.decode(forged) instanceof ItemReadResult.Invalid invalid&&invalid.code()==ItemValidationException.Code.UNAVAILABLE_ENCHANT);
        }
        var forged=stack.clone();var meta=forged.getItemMeta();var root=meta.getPersistentDataContainer().get(WeaponItemCodec.ROOT,PersistentDataType.TAG_CONTAINER);
        root.set(new NamespacedKey("onlydragons","registry_revision"),PersistentDataType.STRING,CalibrationLoadouts.REVISION);
        meta.getPersistentDataContainer().set(WeaponItemCodec.ROOT,PersistentDataType.TAG_CONTAINER,root);forged.setItemMeta(meta);
        c.check("cross_catalog_forgery_rejected",true,codec.decode(forged) instanceof ItemReadResult.Invalid);
    }
    void swap() {
        long before=view().acceptedImpacts();double hp=view().target().currentHealth(),credit=credit();hold=true;
        draw("swap",()->players.await("both captured physical arrows",80,()->held.size()==2,()->{
            var captures=c.production().bows().projectiles().stream().map(OwnedProjectile::shot).toList();
            c.check("primary_and_duplex_single_capture",true,captures.size()==2&&captures.getFirst().overload().megaCritical()
                    &&captures.getFirst().overload().equals(captures.getLast().overload())
                    &&captures.stream().allMatch(s->s.stats().raw(StatKey.CRIT_CHANCE)==200&&s.stats().raw(StatKey.CRIT_DAMAGE)==55));
            players.setupItem("alpha",1,c.production().equipment().createLoadout("ordinary"));
            players.request("alpha","swap-slot");players.await("actual bow swap",80,()->players.player("alpha").getInventory().getHeldItemSlot()==1,()->{
                hold=false;for(int i=0;i<held.size();i++){held.get(i).setGravity(true);held.get(i).setVelocity(velocities.get(i));}
                awaitTrial("swap",before,hp,credit,4,1143.9,1143.9,()->{
                    var results=view().impacts().stream().skip(before).toList();
                    c.check("mega_descendant_provenance",true,results.size()==4&&results.stream().allMatch(r->r.modifierBreakdown().get("overload/enchant-checkpoint2-v2/megaCritical")==1)
                            &&results.stream().filter(r->r.kind()==DamageResult.Kind.FEROCITY).count()==2
                            &&results.stream().allMatch(r->near(r.amounts().contributionDamage(),476.625)||near(r.amounts().contributionDamage(),95.325)));
                    held.clear();velocities.clear();players.request("alpha","return-slot");
                    players.await("returned slot",80,()->players.player("alpha").getInventory().getHeldItemSlot()==0,this::rejection);
                });
            });
        }));
    }
    void rejection() {
        veto=true;int before=settled.size();long count=view().acceptedImpacts();double hp=view().target().currentHealth();
        draw("veto",()->players.await("vetoed collisions",100,()->settled.size()==before+2,()->{
            c.check("veto_no_damage_or_procs",true,view().acceptedImpacts()==count&&view().target().currentHealth()==hp&&view().procs().queued()==0
                    &&settled.subList(before,settled.size()).stream().allMatch(h->h.rejection().isPresent()&&Bukkit.getEntity(h.projectile().shot().projectileId())==null));
            veto=false;nativeGuard=true;
            int nativeStartTick=Bukkit.getCurrentTick(), nativeStartHit=settled.size();
            int nativeStartEvent=probe.events().size();
            UUID nativeTarget=backend.entity().getUniqueId(), nativeOwner=players.identity("alpha");
            trial("native",4,1143.9,1143.9,()->{
                nativeGuard=false;
                var nativeProjectiles=settled.subList(nativeStartHit,settled.size()).stream()
                        .map(h->h.projectile().shot().projectileId()).collect(java.util.stream.Collectors.toSet());
                int nativeEndTick=Bukkit.getCurrentTick();
                var nativeEvents=probe.events().subList(nativeStartEvent,probe.events().size()).stream()
                        .filter(e->nativeTarget.equals(e.target())&&nativeOwner.equals(e.player())
                                &&nativeProjectiles.contains(e.directDamager())&&e.tick()>=nativeStartTick&&e.tick()<=nativeEndTick).toList();
                c.check("native_positive_damage_suppressed",true,nativeEvents.stream().anyMatch(e->e.initialDamage()>0)
                        &&nativeEvents.stream().allMatch(e->e.cancelled()&&e.settledDamage()==0&&e.finalDamage()==0));
                c.observe("nativeSuppressionTrial",Map.of("target",nativeTarget.toString(),"owner",nativeOwner.toString(),
                        "projectiles",nativeProjectiles.stream().map(UUID::toString).toList(),"startTick",nativeStartTick,
                        "endTick",nativeEndTick,"events",nativeEvents.stream().map(DamageObservationProbe.Damage::sequence).toList()));
                reset();open(false,1000,0,CombatProfile.calibration(),()->{
                    equip("gravity_v3",Map.of("gravity",6),0,0);
                    trial("grounded",1,100,100,()->{c.check("descriptor_not_height",true,!backend.airborne()&&backend.entity().getLocation().getY()==100);capped();});
                });
            });
        }));
    }
    void capped() {
        reset();open(true,1000,100,CombatProfile.dragonExperiment(new MechanicRevision("expanded-paper-cap","v1"),.5),()->{
            combo();trial("cap",4,21.37640625,28.501875,()->{
                c.check("cap_parent_and_proc_once",true,view().impacts().stream().allMatch(r->near(r.amounts().cappedDamage(),8.0143125)||near(r.amounts().cappedDamage(),6.236625)));
                reset();open(true,200,0,CombatProfile.calibration(),()->{
                    combo();trial("lethal",1,200,476.625,()->{
                        var frozen=view().completion().orElseThrow();c.later(12,()->{
                            c.check("terminal_full_credit_no_late_children",true,view().completion().orElseThrow().equals(frozen)&&view().acceptedImpacts()==1&&view().procs().queued()==0
                                    &&c.production().bows().capacityUsed()==0&&frozen.participants().get(players.identity("alpha")).actualHealthDamage()==200
                                    &&near(frozen.participants().get(players.identity("alpha")).contributionDamage(),476.625));
                            reset();open(true,1000,0,CombatProfile.calibration(),this::resetAirborne);
                        });
                    });
                });
            });
        });
    }
    void resetAirborne() {
        combo();hold=true;draw("reset-flight",()->players.await("airborne reset group",80,()->held.size()==2,()->{
            var old=c.production().bows().projectiles().getFirst().shot();UUID oldGeneration=generation;reset();hold=false;
            c.check("reset_retires_expanded_group",true,held.stream().noneMatch(Entity::isValid)&&c.production().bows().capacityUsed()==0&&c.production().bows().pendingClaims()==0);
            open(false,1000,0,CombatProfile.calibration(),()->{
                c.check("new_generation_preserves_old_capture",true,!generation.equals(oldGeneration)&&old.overload().megaCritical()&&old.stats().raw(StatKey.CRIT_CHANCE)==200&&view().acceptedImpacts()==0);
                reset();c.check("expanded_resource_cleanup",true,c.production().combat().activeCount()==0&&c.production().bows().capacityUsed()==0&&c.production().bows().continuity().tickets().reservedCount()==0);
                c.observe("trials",trials);c.observe("playerActions",players.journal());c.observe("collisions",collisions.stream().map(UUID::toString).toList());
                players.request("alpha","quit");players.await("actual quit",100,()->players.quits("alpha")==1,c::finish);
            });
        }));
    }
    void combo(){equip("ordinary_v3",Map.of("overload",5,"gravity",6,"power",7,"duplex",5),195,100);}
    void equip(String id,Map<String,Integer> levels,double cc,double f) {
        var instance=catalog.edit(catalog.create(id),levels,List.of());players.setupItem("alpha",0,codec.encode(instance));
        c.production().equipment().bonus(players.player("alpha"),StatKey.CRIT_CHANCE,cc);
        c.production().equipment().bonus(players.player("alpha"),StatKey.FEROCITY,f);
    }
    void open(boolean airborne,double hp,double defense,CombatProfile profile,Runnable next) {
        var location=new Location(players.player("alpha").getWorld(),0,100,0);
        backend=airborne?new DragonBackend(location,c.production().bows().continuity().tickets(),r->{},b->{}):new DummyBackend(location);
        probe.watch(backend.entity());
        generation=c.production().combat().open(players.identity("alpha"),backend,org.bukkit.util.BoundingBox.of(location,24,24,24),hp,defense,"test",profile,Optional.empty(),()->.99);
        players.await("native geometry ready",100,()->!(backend.entity() instanceof EnderDragon dragon)||dragon.getParts().stream().allMatch(part->part.getLocation().getY()>75),next::run);
    }
    void reset(){c.production().combat().reset(players.identity("alpha"));if(backend!=null)backend.close();}
    ManagedCombatService.View view(){return c.production().combat().view(generation).orElseThrow();}
    double credit(){var value=view().contributions().get(players.identity("alpha"));return value==null?0:value.contributionDamage();}
    static boolean near(double a,double b){return Math.abs(a-b)<1e-7;}
    void trial(String step,int count,double hp,double score,Runnable next){long before=view().acceptedImpacts();double oldHp=view().target().currentHealth(),oldScore=credit();draw(step,()->awaitTrial(step,before,oldHp,oldScore,count,hp,score,next));}
    void awaitTrial(String step,long before,double oldHp,double oldScore,int count,double hp,double score,Runnable next){
        players.await(step+" accounting",120,()->view().acceptedImpacts()>=before+count&&view().procs().queued()==0,()->{
            var results=view().impacts().stream().skip(before).toList();
            c.check(step+"_exact_accounting",true,view().acceptedImpacts()==before+count&&near(oldHp-view().target().currentHealth(),hp)&&near(credit()-oldScore,score));
            c.check(step+"_physical_identity",true,results.stream().filter(r->r.kind()!=DamageResult.Kind.FEROCITY).allMatch(r->collisions.contains(r.origin().projectileId())&&Bukkit.getEntity(r.origin().projectileId())==null));
            double nativeMax=backend.airborne()?200:20;
            c.check(step+"_native_projection",(double)(float)(nativeMax*view().target().currentHealth()/view().target().maxHealth()),backend.entity().getHealth());
            trials.add(Map.of("step",step,"hp",oldHp-view().target().currentHealth(),"credit",credit()-oldScore,"results",results.toString()));next.run();
        });
    }
    void draw(String step,Runnable next) {
        var p=players.player("alpha");var entity=backend.entity();
        var box=entity instanceof EnderDragon dragon?dragon.getParts().stream().max(Comparator.comparingDouble(part->part.getBoundingBox().getVolume())).orElseThrow().getBoundingBox():entity.getBoundingBox();
        var origin=box.getCenter().add(new Vector(0,0,-12));players.setupPosition("alpha",new Location(p.getWorld(),origin.getX(),origin.getY()-p.getEyeHeight(),origin.getZ(),0,0));p.setVelocity(new Vector());
        int before=releases;players.request("alpha",step+"-use");players.await("draw "+step,80,p::isHandRaised,()->c.later(22,()->{
            players.request("alpha",step+"-release");players.await("release "+step,80,()->releases>before,next::run);
        }));
    }
    @EventHandler(priority=EventPriority.MONITOR)public void release(EntityShootBowEvent e){if(e.getEntity() instanceof Player)releases++;}
    @EventHandler(priority=EventPriority.MONITOR)public void launch(ProjectileLaunchEvent e){if(e.getEntity() instanceof Arrow arrow){
        if(nativeGuard)c.later(1,()->{if(arrow.isValid())arrow.setDamage(2);});
        if(hold){held.add(arrow);velocities.add(arrow.getVelocity().clone());arrow.setGravity(false);arrow.setVelocity(new Vector());}
    }}
    @EventHandler(priority=EventPriority.HIGHEST)public void hit(ProjectileHitEvent e){Entity parent=e.getHitEntity() instanceof EnderDragonPart part?part.getParent():e.getHitEntity();if(parent!=null&&backend!=null&&parent.getUniqueId().equals(backend.entity().getUniqueId())){collisions.add(e.getEntity().getUniqueId());if(veto)e.setCancelled(true);}}
}
