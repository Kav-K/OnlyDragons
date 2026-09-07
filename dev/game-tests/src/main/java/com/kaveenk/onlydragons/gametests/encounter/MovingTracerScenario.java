package com.kaveenk.onlydragons.gametests.encounter;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import com.kaveenk.onlydragons.paper.projectile.homing.ArrowContinuity;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.domain.projectile.homing.TracerRules;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Exercises explicit returning-Tracer v2 against production moving multipart dragons.
 * Protocol releases are anchored to observed raised-hand state. Native velocities,
 * current-part geometry and collisions are compared with captured continuity frames
 * and literal HP/credit oracles. Targetless prefire and a fixture-launched unmanaged
 * native positive control are separate stages, not production accounting claims.
 * The preserved v2 calibration does not imply current aimed-profile behavior or visual
 * smoothness. Context owns subscriptions, temporary obstruction, targets and callbacks.
 */
public final class MovingTracerScenario implements Scenario, Listener {
    private ScenarioContext c; private PlayerFixture players; private DevelopmentDragonService dragons;
    private World world; private EnderDragon dragon; private Arrow latest; private boolean finished, sentinel;
    private long latestReleaseTick; private float latestForce;
    private final List<SettledHit> hits=new ArrayList<>();
    private final Map<UUID,List<ArrowContinuity.Frame>> frames=new LinkedHashMap<>();
    private final Map<UUID,String> collisions=new HashMap<>();
    private final List<Map<String,Object>> journal=new ArrayList<>();
    private int velocitySamples; private boolean velocityMatches=true; private final Set<UUID> nativeTurns=new HashSet<>();
    private UUID prefireGeneration; private DragonBackend prefireBackend;
    private DamageObservationProbe probe; private DragonFlight nativeFlight; private EnderDragon nativeControl; private boolean lethalTrial; private int deaths, rewards;
    /**
     * Registers receiver/native observations and terminal cleanup before awaiting the actor.
     * @param context server-thread fixture scheduler, evidence and resources
     * @throws Exception if the declared actor fixture cannot initialize
     */
    public void start(ScenarioContext context)throws Exception {
        c=context;c.mechanicRevision("moving-tracer-v2");players=new PlayerFixture(c);dragons=c.production().dragons();probe=new DamageObservationProbe(c);c.listen(this);
        var observation=c.production().combat().observeSettled(hits::add);
        c.cleanup("moving-tracer",()->{finished=true;observation.close();
            if(prefireBackend!=null)prefireBackend.close();if(prefireGeneration!=null)c.production().bows().endEncounter(prefireGeneration);
            if(c.production().combat().activeCount()>0)dragons.reset(dragons.generation().orElseThrow());});
        players.await("moving tracer actor",300,players::allOnline,this::setup);c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    /** Sets explicit calibration/orbit production state and begins native sampling before the first returning volley. */
    private void setup()throws Exception {
        var p=players.player("alpha");world=p.getWorld();p.setGameMode(GameMode.CREATIVE);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);
        p.getInventory().clear();p.getInventory().setHeldItemSlot(0);players.permission("alpha","onlydragons.practice",true);
        dragons.setup(new DevelopmentArena(world.getKey().toString(),160,100,160,24,"test_dragon"));
        dragons.spawn(DevelopmentDragonService.SpawnMode.CALIBRATION, DragonFlight.Mode.ORBIT);dragon=(EnderDragon)Bukkit.getEntity(dragons.view().orElseThrow().entityId());
        probe.watch(dragon);sample();c.later(180,this::returning);
    }
    /** Copies one frame per projectile/tick and independently compares applied native velocity with the nearest real part; also advances explicit control backends. */
    private void sample() {
        if(finished)return;
        for(var owned:c.production().bows().projectiles())c.production().bows().continuity().frame(owned.shot().projectileId()).ifPresent(f->{
            var rows=frames.computeIfAbsent(owned.shot().projectileId(),key->new ArrayList<>());
            if(rows.isEmpty()||rows.getLast().tick()!=f.tick())rows.add(f);
            if(f.tick()==Integer.toUnsignedLong(Bukkit.getCurrentTick())){
                Arrow actual=c.production().bows().arrow(owned.shot().projectileId()).orElseThrow();Vector applied=actual.getVelocity();
                velocitySamples++;velocityMatches &= applied.distance(bukkit(f.after()))<1e-9;
                if(f.aim().isPresent()&&dragon!=null){var aim=f.aim().orElseThrow();
                    dragon.getParts().stream().filter(part->part.getUniqueId().equals(aim.part().partId())).findFirst().ifPresent(part->{
                        var box=part.getBoundingBox();Vector position=actual.getLocation().toVector();
                        Vector nearest=new Vector(Math.max(box.getMinX(),Math.min(box.getMaxX(),position.getX())),
                                Math.max(box.getMinY(),Math.min(box.getMaxY(),position.getY())),Math.max(box.getMinZ(),Math.min(box.getMaxZ(),position.getZ())));
                        Vector desired=nearest.clone().subtract(position),before=bukkit(f.before());
                        if(desired.length()>1e-8&&before.length()>1e-8&&applied.length()>1e-8&&nearest.distance(bukkit(aim.point()))<1e-9
                                &&applied.clone().normalize().dot(desired.clone().normalize())>before.clone().normalize().dot(desired.clone().normalize())+1e-8)
                            nativeTurns.add(actual.getUniqueId());
                    });
                }
            }
        });
        if(prefireBackend!=null&&!prefireBackend.lethalRequested()&&prefireBackend.entity().isValid())
            prefireBackend.synchronize(new com.kaveenk.onlydragons.domain.encounter.TargetState(prefireGeneration, dragon.getUniqueId(),1000,1000,0));
        if(nativeFlight!=null)nativeFlight.tick(nativeControl);
        c.later(1,this::sample);
    }
    /** Encodes a trusted legacy calibration item with the chosen Tracer level as labelled server equipment setup. */
    private void kit(String id,int level) {
        var registry=CalibrationLoadouts.registry();var item=registry.create(id);
        if(level>=0)item=registry.edit(item,level==0?Map.of():Map.of("dragon_tracer",level),List.of());
        players.setupItem("alpha",0,new WeaponItemCodec(registry).encode(item));players.setupItem("alpha",9,new ItemStack(Material.ARROW,64));
    }
    /** Places the actor at the fixed trial launch location with a supplied pitch; subsequent firing remains native client input. */
    private void position(float pitch){players.setupPosition("alpha",new Location(world,160,100-players.player("alpha").getEyeHeight(),146,0,pitch));}
    /** Requests the standard 25-tick observed draw used by the positive and most control stages. */
    private void draw(String id,ScenarioContext.Step next) { draw(id,25,next); }
    /**
     * Starts the hold clock only after the native hand is raised, then verifies release
     * tick/force against the requested duration. Queued protocol markers alone do not prove a draw.
     */
    private void draw(String id,int holdTicks,ScenarioContext.Step next) {
        var player=players.player("alpha");long requested=Integer.toUnsignedLong(Bukkit.getCurrentTick());
        latest=null;players.request("alpha",id+"-use");
        // A queued client request is not an observed draw. Chunk/teleport traffic
        // can delay use until the release request is already waiting behind it.
        players.await("actual draw "+id,50,player::isHandRaised,()->{
            long started=Integer.toUnsignedLong(Bukkit.getCurrentTick());
            c.later(holdTicks,()->{
                long released=Integer.toUnsignedLong(Bukkit.getCurrentTick());players.request("alpha",id+"-release");
                players.await("real release "+id,50,()->latest!=null,()->{
                    double duration=holdTicks/20.0,minForce=Math.min(1,(duration*duration+2*duration)/3);
                    c.observe(id+"Draw",Map.of("requestedTick",requested,"confirmedTick",started,"releaseRequestTick",released,
                            "nativeReleaseTick",latestReleaseTick,"holdTicks",holdTicks,"force",latestForce,"projectile",latest.getUniqueId().toString()));
                    c.check(id+"_confirmed_draw",true,started>=requested&&released-started==holdTicks
                            &&latestReleaseTick>=released&&latestForce+1e-6>=minForce);
                    next.run();
                });
            });
        });
    }
    /** Checks two real v2 volley collisions, literal HP/credit, upward-to-descending curvature and independent current-part native turning. */
    private void returning(){kit("tracer_return_v2",-1);position(-45);players.bind("moving-dragon",dragon.getUniqueId());sentinel=true;
        Location targetStart=dragon.getLocation();int start=hits.size();
        draw("return",()->{UUID primary=latest.getUniqueId();c.later(65,()->{
            sentinel=false;var impacts=hits.subList(start,hits.size());
            c.check("returning_volley_real_collisions",true,impacts.size()==2&&impacts.stream().allMatch(SettledHit::accepted)
                    &&impacts.stream().allMatch(h->"DRAGON".equals(collisions.get(h.projectile().shot().projectileId()))));
            c.check("moving_volley_exact_health_credit",true,dragons.view().orElseThrow().target().currentHealth()==880
                    &&dragons.view().orElseThrow().contributions().get(players.identity("alpha")).contributionDamage()==120&&dragon.getHealth()==176);
            c.check("dragon_moved_during_volley",true,dragon.getLocation().distance(targetStart)>2);
            var path=frames.getOrDefault(primary,List.of());
            c.check("upward_arrow_curves_down_same_uuid",true,path.stream().anyMatch(f->f.before().y()>0)
                    &&path.stream().anyMatch(f->f.after().y()<-.1&&f.aim().isPresent())
                    &&impacts.stream().anyMatch(h->h.projectile().shot().projectileId().equals(primary)));
            c.check("v2_turn_speed_and_grace",true,!path.isEmpty()&&path.stream().allMatch(this::validFrame)
                    &&path.stream().anyMatch(f->f.groupLaunchAge()<3&&f.aim().isEmpty()));
            c.check("native_velocity_curves_to_current_part",true,velocitySamples>10&&velocityMatches&&nativeTurns.contains(primary));
            c.check("duplex_captured_v2_origin",true,impacts.size()==2&&impacts.stream().map(h->h.projectile().groupLaunchTick()).distinct().count()==1
                    &&impacts.stream().allMatch(h->h.projectile().tracerProfile().revision().equals("tracer-return/v2")));
            c.check("moving_owned_native_suppression",true,probe.events().stream().anyMatch(e->e.initialDamage()>0&&e.cancelled()&&e.settledDamage()==0));
            c.check("v2_trace_profile",true,c.production().bows().trace().stream().anyMatch(t->t.kind().equals("tracer-acquired")&&t.detail().contains("tracer-return/v2")));
            journal.add(Map.of("stage","return","primary",primary.toString(),"frames",path.stream().map(f->Map.of("tick",f.tick(),"position",vec(f.position()),"before",vec(f.before()),"after",vec(f.after()),"aim",f.aim().isPresent())).toList()));
            plain();});});
    }
    /** Uses an unenchanted launch to require ballistic unchanged frames and no managed HP loss. */
    private void plain(){kit("ordinary",-1);position(-45);double hp=dragons.view().orElseThrow().target().currentHealth();
        draw("plain",()->{UUID id=latest.getUniqueId();c.later(60,()->{
            c.check("unenchanted_miss_honest",true,frames.getOrDefault(id,List.of()).stream().allMatch(f->f.aim().isEmpty()&&f.before().equals(f.after()))
                    &&dragons.view().orElseThrow().target().currentHealth()==hp);blocked();});});}
    /** Owns a temporary stone wall and checks an observed short draw ends in a real block collision without homing or damage. */
    private void blocked(){
        var originals=new LinkedHashMap<org.bukkit.block.Block,org.bukkit.block.data.BlockData>();
        c.cleanup("obstruction",()->originals.forEach((b,data)->b.setBlockData(data,false)));
        for(int x=136;x<=184;x++)for(int y=76;y<=124;y++){var b=world.getBlockAt(x,y,140);originals.put(b,b.getBlockData());b.setType(Material.STONE,false);}
        kit("tracer_return_v2",5);players.setupPosition("alpha",new Location(world,160,100-players.player("alpha").getEyeHeight(),137,0,0));double hp=dragons.view().orElseThrow().target().currentHealth();
        draw("blocked",6,()->{UUID id=latest.getUniqueId();c.later(25,()->{
            c.observe("blockedControl",Map.of("uuid",id.toString(),"collision",collisions.getOrDefault(id,"NONE"),
                    "frameCount",frames.getOrDefault(id,List.of()).size(),"aimAges",frames.getOrDefault(id,List.of()).stream().filter(f->f.aim().isPresent()).map(ArrowContinuity.Frame::groupLaunchAge).toList(),
                    "beforeHP",hp,"afterHP",dragons.view().orElseThrow().target().currentHealth()));
            c.check("blocked_arrow_real_block_collision",true,"BLOCK".equals(collisions.get(id))&&frames.getOrDefault(id,List.of()).stream().allMatch(f->f.aim().isEmpty())
                    &&dragons.view().orElseThrow().target().currentHealth()==hp);originals.forEach((b,data)->b.setBlockData(data,false));range();});});
    }
    /** Checks the level-one away-facing launch exits without acquisition, then retires the production generation before prefire. */
    private void range(){kit("tracer_return_v2",1);
        players.setupPosition("alpha",new Location(world,160,100-players.player("alpha").getEyeHeight(),138,180,0));
        double hp=dragons.view().orElseThrow().target().currentHealth();draw("range",()->{UUID id=latest.getUniqueId();c.later(20,()->{
            c.check("out_of_range_exit_honest",true,!frames.getOrDefault(id,List.of()).isEmpty()&&frames.get(id).stream().allMatch(f->f.aim().isEmpty())
                    &&c.production().bows().projectile(id).isEmpty()&&dragons.view().orElseThrow().target().currentHealth()==hp);
            dragons.reset(dragons.generation().orElseThrow());prefire();});});
    }
    /** Launches in an explicitly targetless fixture encounter, registers a native target later, and checks the original UUID acquires and collides. */
    private void prefire(){
        prefireGeneration=UUID.randomUUID();c.production().bows().openEncounter(prefireGeneration,world,
                new org.bukkit.util.BoundingBox(136,76,136,184,124,184),new com.kaveenk.onlydragons.domain.MechanicRevision("prefire-v2-control","v1"));
        kit("tracer_return_v2",5);position(-35);draw("prefire",()->{UUID id=latest.getUniqueId();long launched=c.production().bows().projectile(id).orElseThrow().shot().launchTick();
            c.later(2,()->{long spawned=Integer.toUnsignedLong(Bukkit.getCurrentTick());
                c.check("targetless_v2_initial_ballistic",true,frames.getOrDefault(id,List.of()).stream().allMatch(f->f.aim().isEmpty()));
                prefireBackend=new DragonBackend(new Location(world,160,100,160),new org.bukkit.util.BoundingBox(136,76,136,184,124,184),
                        DragonFlight.Mode.ORBIT,c.production().bows().continuity().tickets(),r->{},b->{});dragon=prefireBackend.entity();
                c.production().bows().registerTarget(prefireGeneration,dragon.getUniqueId(),dragon);
                c.later(60,()->{var impact=hits.stream().filter(h->h.projectile().shot().projectileId().equals(id)).findFirst();
                    c.check("original_prefire_arrow_acquires_and_collides",true,impact.isPresent()&&impact.get().accepted()&&launched<spawned&&spawned<impact.get().collisionTick()
                            &&"DRAGON".equals(collisions.get(id))&&frames.getOrDefault(id,List.of()).stream().anyMatch(f->f.aim().isPresent()));
                    prefireBackend.close();prefireBackend=null;c.production().bows().endEncounter(prefireGeneration);prefireGeneration=null;
                    c.check("parent_projectile_tickets_released",true,c.production().bows().continuity().tickets().reservedCount()==0
                            &&c.production().bows().continuity().tickets().demandCount()==0&&c.production().bows().continuity().frameCount()==0);
                    nativePositive();});});});
    }
    /** Spawns an unmanaged moving native dragon and a labelled fixture arrow to prove uncancelled native damage can be observed. */
    private void nativePositive(){
        nativeControl=c.own(world.spawn(new Location(world,160,100,160),EnderDragon.class,d->{d.setPersistent(false);d.setAI(true);d.setGravity(false);d.setPhase(EnderDragon.Phase.HOVER);}));
        probe.watch(nativeControl);nativeFlight=new DragonFlight(new Location(world,160,100,160),new org.bukkit.util.BoundingBox(136,76,136,184,124,184),DragonFlight.Mode.ORBIT);
        position(0);c.later(180,()->{
            var part=nativeControl.getParts().stream().filter(p->p.getBoundingBox().getWidthX()==5).findFirst().orElseThrow();
            var center=part.getBoundingBox().getCenter();double hp=nativeControl.getHealth();Location before=nativeControl.getLocation();
            Arrow arrow=c.own(world.spawnArrow(center.clone().add(new Vector(0,0,-5)).toLocation(world),new Vector(0,0,1),2,0));
            arrow.setShooter(players.player("alpha"));arrow.setDamage(2);arrow.setCritical(false);UUID id=arrow.getUniqueId();
            c.later(12,()->{
                c.check("moving_native_positive_control",true,nativeControl.getHealth()<hp&&nativeControl.getLocation().distance(before)>.5
                        &&"DRAGON".equals(collisions.get(id))&&probe.events().stream().anyMatch(e->!e.cancelled()&&e.settledDamage()>0));
                c.observe("nativePositive",Map.of("uuid",id.toString(),"beforeHP",hp,"afterHP",nativeControl.getHealth(),"motion",nativeFlight.view().toString()));
                nativeFlight.stop();nativeFlight=null;nativeControl.remove();graceCollision();
            });
        });
    }
    /** Places a native release close enough to collide during the three-tick ballistic grace and requires damage without any homing frame. */
    private void graceCollision(){
        dragons.spawn(DevelopmentDragonService.SpawnMode.CALIBRATION, DragonFlight.Mode.ORBIT);dragon=(EnderDragon)Bukkit.getEntity(dragons.view().orElseThrow().entityId());kit("tracer_return_v2",5);
        c.later(10,()->{
            var part=dragon.getParts().stream().filter(p->p.getBoundingBox().getWidthX()==5).findFirst().orElseThrow();var center=part.getBoundingBox().getCenter();
            players.setupPosition("alpha",new Location(world,center.getX(),center.getY()-players.player("alpha").getEyeHeight(),center.getZ()-4,0,0));
            int start=hits.size();draw("grace",()->{UUID id=latest.getUniqueId();c.later(10,()->{
                var impact=hits.subList(start,hits.size()).stream().filter(h->h.projectile().shot().projectileId().equals(id)).findFirst();
                c.check("real_collision_during_ballistic_grace",true,impact.isPresent()&&impact.get().accepted()
                        &&impact.get().collisionTick()-impact.get().projectile().groupLaunchTick()<3&&"DRAGON".equals(collisions.get(id))
                        &&dragons.view().orElseThrow().target().currentHealth()==900
                        &&frames.getOrDefault(id,List.of()).stream().allMatch(f->f.aim().isEmpty()));
                c.observe("graceCollision",Map.of("uuid",id.toString(),"collisionAge",impact.map(h->h.collisionTick()-h.projectile().groupLaunchTick()).orElse(-1L),
                        "hp",dragons.view().orElseThrow().target().currentHealth()));
                dragons.reset(dragons.generation().orElseThrow());lethal();
            });});
        });
    }
    /** Checks exact moving lethal accounting, stopped motion during native animation and eventual parent-ticket release without rewards. */
    private void lethal(){
        dragons.spawn(DevelopmentDragonService.SpawnMode.CALIBRATION, DragonFlight.Mode.ORBIT);dragon=(EnderDragon)Bukkit.getEntity(dragons.view().orElseThrow().entityId());
        kit("tracer_return_v2",5);position(-45);c.production().equipment().bonus(players.player("alpha"),com.kaveenk.onlydragons.domain.stats.StatKey.WEAPON_DAMAGE,900);
        c.later(180,()->{lethalTrial=true;draw("lethal",()->players.await("moving managed lethal",100,()->dragons.view().orElseThrow().completion().isPresent(),()->{
            var result=dragons.view().orElseThrow().completion().orElseThrow();long steps=dragons.motion().orElseThrow().steps();UUID nativeId=dragon.getUniqueId();
            c.check("moving_lethal_freezes_exact_result",true,result.participants().get(players.identity("alpha")).actualHealthDamage()==1000
                    &&result.participants().get(players.identity("alpha")).contributionDamage()==1000&&deaths==1&&dragons.motion().orElseThrow().state().equals("STOPPED"));
            c.later(5,()->{
                c.check("animation_retains_parent_tickets_motion_stopped",true,Bukkit.getEntity(nativeId)!=null&&dragon.getDeathAnimationTicks()>0
                        &&c.production().bows().continuity().tickets().demandCount()>0&&dragons.motion().orElseThrow().steps()==steps);
                players.await("native terminal removal",350,()->Bukkit.getEntity(nativeId)==null,()->c.later(3,()->{
                    c.check("native_death_releases_all_tickets_no_rewards",true,rewards==0&&dragons.motion().orElseThrow().steps()==steps
                            &&c.production().bows().continuity().tickets().demandCount()==0&&c.production().bows().continuity().tickets().reservedCount()==0
                            &&dragons.view().orElseThrow().completion().orElseThrow().equals(result));
                    c.observe("flightJournal",journal);players.request("alpha","end");players.await("quit",100,()->Bukkit.getPlayer(players.identity("alpha"))==null,c::finish);
                }));
            });
        }));});
    }
    /** Counts native deaths and nonempty drop/XP pressure only during the managed lethal trial. */
    @EventHandler(priority=EventPriority.MONITOR)public void death(EntityDeathEvent event){if(lethalTrial&&event.getEntity()==dragon){deaths++;if(event.getDroppedExp()!=0||!event.getDrops().isEmpty())rewards++;}}
    /** Counts delayed native XP attributed to the lethal trial's exact dragon UUID. */
    @EventHandler(priority=EventPriority.MONITOR)public void reward(EntitySpawnEvent event){if(lethalTrial&&event.getEntity() instanceof ExperienceOrb orb&&dragon.getUniqueId().equals(orb.getSourceEntityId()))rewards+=orb.getExperience();}
    /** Checks literal v2 speed preservation, 18-degree turn bound, three-tick group grace and retained profile revision. */
    private boolean validFrame(ArrowContinuity.Frame f){double before=TracerRules.length(f.before()),after=TracerRules.length(f.after());
        double dot=f.before().x()*f.after().x()+f.before().y()*f.after().y()+f.before().z()*f.after().z();
        return Math.abs(before-after)<1e-9&&(before==0||Math.acos(Math.max(-1,Math.min(1,dot/(before*after))))<=Math.toRadians(18)+1e-7)
                &&(f.groupLaunchAge()>=3||f.aim().isEmpty())&&f.profileRevision().equals("tracer-return/v2");}
    /** Copies a captured domain vector into a Bukkit vector for independent native-velocity comparisons. */
    private static Vector bukkit(Vector3 v){return new Vector(v.x(),v.y(),v.z());}
    /** Serializes a domain vector as immutable XYZ evidence. */
    private static List<Double> vec(Vector3 v){return List.of(v.x(),v.y(),v.z());}
    /** Adds labelled native-damage pressure to actual launched arrows so production suppression has a positive sentinel. */
    @EventHandler(priority=EventPriority.MONITOR)public void nativeDamageSentinel(ProjectileLaunchEvent event){
        if(sentinel&&event.getEntity() instanceof Arrow arrow)c.later(2,()->{if(arrow.isValid())arrow.setDamage(2);});
    }
    /** Captures alpha's actual native arrow, release tick and force for the draw-duration receipt. */
    @EventHandler(priority=EventPriority.MONITOR)public void release(EntityShootBowEvent e){if(e.getEntity() instanceof Player p&&p.getUniqueId().equals(players.identity("alpha"))&&e.getProjectile() instanceof Arrow a){latest=a;latestReleaseTick=Integer.toUnsignedLong(Bukkit.getCurrentTick());latestForce=e.getForce();}}
    /** Classifies real arrow collisions as dragon part, block or other independently of receiver acceptance. */
    @EventHandler(priority=EventPriority.LOWEST)public void impact(ProjectileHitEvent e){if(e.getEntity() instanceof Arrow a){collisions.put(a.getUniqueId(),e.getHitEntity() instanceof EnderDragonPart?"DRAGON":e.getHitBlock()!=null?"BLOCK":"OTHER");}}
}
