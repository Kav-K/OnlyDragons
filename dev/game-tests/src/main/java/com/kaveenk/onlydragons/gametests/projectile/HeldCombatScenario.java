package com.kaveenk.onlydragons.gametests.projectile;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.domain.combat.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.paper.projectile.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/** Real held v4 arrows into the deployed orbit/Tracer/combat services, using their existing random ports. */
public final class HeldCombatScenario implements Scenario, Listener {
    private ScenarioContext c; private PlayerFixture players; private OwnedBowService bows; private ManagedCombatService combat;
    private DragonBackend backend; private UUID fight; private DamageObservationProbe probe;
    private final List<OwnedProjectile> launched = new ArrayList<>();
    private final List<SettledHit> settled = new ArrayList<>();
    private final Map<UUID,Long> collisions = new LinkedHashMap<>();
    private final Map<UUID,List<List<Double>>> paths = new LinkedHashMap<>();
    private boolean finished, veto; private int vetoEvents; private Location start;

    public void start(ScenarioContext context) throws Exception {
        c=context;c.mechanicRevision("held-combat-v1");players=new PlayerFixture(c);probe=new DamageObservationProbe(c);
        bows=new OwnedBowService(c.production(),c.production().equipment(),100,()->.75);
        combat=new ManagedCombatService(c.production(),bows);
        c.cleanup("held-combat-services",()->{finished=true;
            c.observe("heldFlightDiagnostics",Map.of("shots",launched.stream().map(p->Map.of("uuid",p.shot().projectileId().toString(),"launch",p.shot().launchPosition().toString(),"velocity",p.shot().initialVelocity().toString())).toList(),
                    "paths",paths.entrySet().stream().map(e->Map.of("uuid",e.getKey().toString(),"frames",e.getValue())).toList(),"settled",settled.size()));
            combat.close();bows.close();});
        c.listen(new OwnedBowListener(bows));c.listen(new ManagedCombatListener(combat));c.listen(this);
        bows.start();combat.start();var observer=combat.observeSettled(settled::add);c.cleanup("held-combat-observer",observer::close);
        players.await("held combat actor",300,players::allOnline,this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    private Player player(){return players.player("alpha");}
    private void setup(){
        player().setGameMode(GameMode.SURVIVAL);player().setAllowFlight(true);player().setFlying(true);player().setInvulnerable(true);
        player().getInventory().clear();player().getInventory().setHeldItemSlot(0);
        players.permission("alpha","onlydragons.fire",true);
        fresh("volley_duplex_v4");sample();c.later(180,this::duplex);
    }
    private void fresh(String id){
        if(fight!=null)combat.reset(players.identity("alpha"));
        var center=new Location(player().getWorld(),160,100,160);var bounds=BoundingBox.of(center,48,48,48);
        backend=new DragonBackend(center,bounds,DragonFlight.Mode.ORBIT,bows.continuity().tickets(),ignored->{},ignored->{});
        fight=combat.open(players.identity("alpha"),backend,bounds,100000,0,"test_dragon",CombatProfile.tempoDragon(),Optional.empty(),()->.99);
        probe.watch(backend.entity());player().clearActiveItem();
        players.setupPosition("alpha",new Location(player().getWorld(),160,100-player().getEyeHeight(),128,0,-24));
        players.setupItem("alpha",0,c.production().equipment().createLoadout(id));
        players.setupItem("alpha",1,c.production().equipment().createLoadout("volley_duplex_v4"));
        players.setupItem("alpha",9,new ItemStack(Material.ARROW,64));player().getInventory().setHeldItemSlot(0);
        launched.clear();settled.clear();collisions.clear();paths.clear();
    }
    private void aimCurrentPart(){
        var part=backend.entity().getParts().stream().filter(p->p.getBoundingBox().getWidthX()==5).findFirst().orElseThrow();
        var pose=player().getLocation().setDirection(part.getBoundingBox().getCenter().subtract(player().getEyeLocation().toVector()));
        pose.setPitch(pose.getPitch()-24);players.setupPosition("alpha",pose);
    }
    private void duplex(){
        aimCurrentPart();
        start=backend.entity().getLocation();hold("duplex",6,()->{
            int total=launched.size();
            players.await("all moving Duplex impacts",140,()->settled.size()==total&&combat.fireMetrics(fight).burns()==0,()->{
                var view=view();int primaries=(int)launched.stream().filter(p->p.shot().ordinal()==0).count();
                c.check("sustained_duplex_real_all_collisions",true,primaries>=6&&total==primaries*2&&settled.stream().allMatch(SettledHit::accepted)
                        &&settled.stream().allMatch(h->collisions.containsKey(h.projectile().shot().projectileId())));
                long firstCollision=Collections.min(collisions.values()), lastCollision=Collections.max(collisions.values());
                int fireTicks=(int)((lastCollision-firstCollision)/20)+3;
                c.check("duplex_literal_damage_fire_hp_credit",true,view.target().currentHealth()==100000-(120*primaries+9*fireTicks)
                        &&credit()==120*primaries+9*fireTicks&&view.acceptedImpacts()==2*primaries+fireTicks);
                c.check("duplex_literal_fire_nine_observed_duration",true,view.impacts().stream().filter(r->r.kind()==DamageResult.Kind.FIRE).count()==fireTicks
                        &&view.impacts().stream().filter(r->r.kind()==DamageResult.Kind.FIRE).allMatch(r->r.amounts().actualHealthDamage()==9&&r.amounts().contributionDamage()==9));
                c.check("duplex_25f_no_proc_at_point99",true,view.impacts().stream().noneMatch(r->r.kind()==DamageResult.Kind.FEROCITY)
                        &&view.impacts().stream().filter(r->r.kind()!=DamageResult.Kind.FIRE).allMatch(r->r.effectiveFerocity()==25));
                c.check("sustained_quiver_one_charge_per_trigger",64-primaries,ammo());
                c.check("moving_dragon_and_same_uuid_return",true,backend.entity().getLocation().distance(start)>2&&paths.values().stream().anyMatch(path->path.stream().anyMatch(row->row.get(1)>0)&&path.stream().anyMatch(row->row.get(2)<-.1)));
                c.check("new_volley_captured_return_profile",true,launched.stream().allMatch(p->p.tracerProfile().revision().equals("tracer-aimed/v3"))
                        &&launched.stream().filter(p->p.shot().ordinal()==1).allMatch(child->launched.stream().anyMatch(parent->parent.shot().projectileId().equals(child.shot().parentProjectileId().orElseThrow())&&parent.groupLaunchTick()==child.groupLaunchTick())));
                c.check("injected_native_positive_guard_observed",true,probe.events().stream().anyMatch(e->e.initialDamage()>0&&e.cancelled()&&e.settledDamage()==0));
                c.check("exact_native_projection",(double)(float)(200*view.target().currentHealth()/100000),backend.entity().getHealth());
                c.observe("duplex",Map.of("primaries",primaries,"projectiles",total,"hp",view.target().currentHealth(),"credit",credit(),"collisions",collisionRows(),"path",paths.values().iterator().next()));
                fresh("volley_tempo_v4");c.later(180,this::tempo);
            });
        });
    }
    private void tempo(){
        aimCurrentPart();
        hold("tempo",4,()->{
            int ft=launched.size();players.request("alpha","duplex-slot");
            players.await("real ultimate swap",80,()->player().getInventory().getHeldItemSlot()==1,()->{
                long nativeSwapObservedTick=Integer.toUnsignedLong(Bukkit.getCurrentTick());
                c.observe("nativeSwap",Map.of("tick",nativeSwapObservedTick,"slot",player().getInventory().getHeldItemSlot()));
                players.await("captured Tempo arrows finish after swap",100,()->settled.size()==ft,()->{
                    c.check("tempo_captured_after_swap",true,collisions.values().stream().anyMatch(tick->tick>nativeSwapObservedTick)&&ft>=4&&launched.stream().allMatch(p->p.shot().ordinal()==0&&p.shot().weapon().definitionId().equals("volley_tempo_v4"))
                            &&settled.stream().allMatch(h->h.projectile().shot().enchantments().stream().anyMatch(e->e.id().equals("fatal_tempo")&&e.level()==5)));
                    c.check("tempo_shared_bonus_built",true,view().procs().tempoStates()==1&&view().impacts().stream().filter(r->r.kind()==DamageResult.Kind.PHYSICAL).map(DamageResult::effectiveFerocity).distinct().count()>=4);
                    long lastFt=view().impacts().stream().filter(r->r.kind()==DamageResult.Kind.PHYSICAL).mapToLong(DamageResult::tick).max().orElseThrow();
                    click("after-swap",()->players.await("new Duplex benefits",100,()->settled.size()==ft+2,()->{
                        var duplex=view().impacts().stream().filter(r->r.kind()==DamageResult.Kind.DUPLEX).findFirst().orElseThrow();
                        c.check("swapped_duplex_uses_shared_base25_buff",true,duplex.effectiveFerocity()==75&&launched.getLast().shot().weapon().definitionId().equals("volley_duplex_v4"));
                        long now=Integer.toUnsignedLong(Bukkit.getCurrentTick());
                        if(now>=lastFt+59)throw new IllegalStateException("Missed original Tempo expiry boundary");
                        c.later(lastFt+59-now,()->{
                            c.check("tempo_before_original_expiry",1,view().procs().tempoStates());
                            c.later(1,()->{
                                c.check("duplex_does_not_refresh_tempo",0,view().procs().tempoStates());
                                players.await("all fire ends",120,()->combat.fireMetrics(fight).burns()==0,()->{
                                    long first=collisions.values().stream().mapToLong(Long::longValue).min().orElseThrow()+1;
                                    long last=collisions.values().stream().mapToLong(Long::longValue).max().orElseThrow()+1;
                                    long duplexHit=settled.stream().filter(h->h.projectile().shot().ordinal()==1).mapToLong(h->collisions.get(h.projectile().shot().projectileId())+1).min().orElseThrow();
                                    int fireCount=0,fireDamage=0;
                                    for(long tick=first+20;tick<=last+60;tick+=20){fireCount++;fireDamage+=tick>=duplexHit?9:6;}
                                    int expected=100*ft+120+fireDamage;
                                    c.check("swapped_literal_health_credit",true,view().target().currentHealth()==100000-expected&&credit()==expected
                                            &&view().acceptedImpacts()==ft+2+fireCount);
                                    c.check("swapped_no_unplanned_procs",true,view().impacts().stream().noneMatch(r->r.kind()==DamageResult.Kind.FEROCITY));
                                    c.observe("swap",Map.of("ftPrimaries",ft,"fireCount",fireCount,"expectedDamage",expected,"hp",view().target().currentHealth(),"credit",credit(),"lastFt",lastFt,"collisions",collisionRows()));
                                    rejection();
                                });
                            });
                        });
                    }));
                });
            });
        });
    }
    private void rejection(){
        fresh("volley_duplex_v4");veto=true;c.later(30,()->{aimCurrentPart();hold("veto",3,()->{
            int arrows=launched.size();players.await("all actual veto collisions",140,()->settled.size()==arrows,()->{
                c.check("held_physical_veto_no_health_score_or_effects",true,collisions.size()==arrows&&vetoEvents>=arrows&&settled.stream().allMatch(h->h.rejection().orElseThrow()==SettledHit.Rejection.PHYSICAL_VETO)&&view().acceptedImpacts()==0&&view().target().currentHealth()==100000&&credit()==0
                        &&combat.fireMetrics(fight).burns()==0&&view().procs().tempoStates()==0);
                c.observe("veto",Map.of("notifications",vetoEvents,"uniqueCollisions",collisionRows(),"settled",settled.size(),"hp",view().target().currentHealth(),"credit",credit(),"accepted",view().acceptedImpacts(),"burns",combat.fireMetrics(fight).burns(),"tempoStates",view().procs().tempoStates()));
                veto=false;combat.reset(players.identity("alpha"));
                c.check("moving_held_reset_releases_resources",true,bows.capacityUsed()==0&&bows.pendingGroups()==0&&bows.continuity().tickets().reservedCount()==0&&combat.activeCount()==0);
                c.observe("playerActions",players.journal());finished=true;players.request("alpha","end");
                players.await("real end",100,()->players.quits("alpha")==1,c::finish);
            });
        });});
    }
    private void hold(String id,int groups,Runnable next){
        int before=launched.size();players.request("alpha",id+"-use");
        players.await("native sustained "+id,100,()->player().isHandRaised()&&launched.subList(before,launched.size()).stream().filter(p->p.shot().ordinal()==0).count()>=groups,()->{
            players.request("alpha",id+"-release");players.await("native release "+id,80,()->!player().isHandRaised(),()->c.later(3,next::run));
        });
    }
    private void click(String id,Runnable next){int count=launched.size();players.request("alpha",id);players.await("native click "+id,80,()->launched.size()>=count+2,next::run);}
    private ManagedCombatService.View view(){return combat.view(fight).orElseThrow();}
    private double credit(){return view().contributions().getOrDefault(players.identity("alpha"),new com.kaveenk.onlydragons.domain.encounter.EncounterResult.Contribution(0,0,0,false)).contributionDamage();}
    private int ammo(){return player().getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum();}
    private List<Map<String,Object>> collisionRows(){return collisions.entrySet().stream().map(e->Map.<String,Object>of("projectile",e.getKey().toString(),"tick",e.getValue())).toList();}
    private void sample(){
        if(finished)return;
        for(var owned:bows.projectiles())bows.continuity().frame(owned.shot().projectileId()).ifPresent(frame->{
            var path=paths.computeIfAbsent(owned.shot().projectileId(),ignored->new ArrayList<>());
            if(path.size()<60)path.add(List.of((double)frame.tick(),frame.before().y(),frame.after().y(),frame.position().x(),frame.position().y(),frame.position().z(),frame.aim().isPresent()?1.0:0.0));
        });
        c.later(1,this::sample);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void launch(ProjectileLaunchEvent e){bows.projectile(e.getEntity().getUniqueId()).ifPresent(launched::add);}
    @EventHandler(priority=EventPriority.MONITOR) public void hit(ProjectileHitEvent e){
        if(e.getHitEntity() instanceof EnderDragonPart part&&backend!=null&&part.getParent().getUniqueId().equals(backend.entity().getUniqueId())&&launched.stream().anyMatch(p->p.shot().projectileId().equals(e.getEntity().getUniqueId()))){
            collisions.putIfAbsent(e.getEntity().getUniqueId(),Integer.toUnsignedLong(Bukkit.getCurrentTick()));if(veto){e.setCancelled(true);vetoEvents++;}
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void sentinel(ProjectileLaunchEvent e) {
        if(e.getEntity() instanceof Arrow arrow && bows.projectile(arrow.getUniqueId()).isPresent())
            c.later(2,()->{if(arrow.isValid())arrow.setDamage(2);});
    }
}
