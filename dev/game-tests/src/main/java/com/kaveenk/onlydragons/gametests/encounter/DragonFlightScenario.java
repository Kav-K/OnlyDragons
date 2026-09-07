package com.kaveenk.onlydragons.gametests.encounter;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.util.BoundingBox;

/**
 * Checks the deployed flight controller using actual dragon parent/part geometry.
 * Each radius trial runs sustained server-thread samples and literal step/yaw bounds;
 * stationary, cancelled teleport, redirected yaw and lost-AI controls check retirement.
 * No player input or rendered-client smoothness is inferred. Context cleanup resets
 * an active generation and owns the listeners/scheduled checks.
 */
public final class DragonFlightScenario implements Scenario, Listener {
    private ScenarioContext c;
    private DevelopmentDragonService dragons;
    private EnderDragon dragon;
    private Location previous, first;
    private BoundingBox bounds;
    private int radius, tick;
    private double maxStep, maxYaw, path;
    private boolean cancel, redirectYaw;
    private final Set<String> chunks = new HashSet<>();
    private final List<Map<String,Object>> journal = new ArrayList<>();
    /**
     * Registers cleanup and native teleport controls, then starts the smallest-radius trial.
     * @param context run-owned server-thread scheduling, checks and cleanup
     * @throws Exception if arena setup or the first production spawn fails
     */
    public void start(ScenarioContext context) throws Exception {
        c=context; c.mechanicRevision("dragon-flight-v1"); dragons=c.production().dragons(); c.listen(this);
        c.cleanup("flight",()->{if(c.production().combat().activeCount()>0)dragons.reset(dragons.generation().orElseThrow());});
        trial(16);
    }
    /** Resets measurement accumulators and spawns explicit calibration/orbit flight inside the requested arena radius. */
    private void trial(int radius) throws Exception {
        this.radius=radius;tick=0;maxStep=0;maxYaw=0;path=0;previous=null;chunks.clear();
        var world=Bukkit.getWorlds().getFirst();
        var arena=new DevelopmentArena(world.getKey().toString(),160,100,-160,radius,"test_dragon");
        dragons.setup(arena);dragons.spawn(DevelopmentDragonService.SpawnMode.CALIBRATION, DragonFlight.Mode.ORBIT);
        dragon=(EnderDragon)Bukkit.getEntity(dragons.view().orElseThrow().entityId());bounds=arena.bounds();first=dragon.getLocation();
        c.later(5,this::sample);
    }
    /** Measures 480 native updates, rejects escaped multipart boxes immediately, then checks route extent and reset ticket release. */
    private void sample() throws Exception {
        if (!dragon.isValid()) throw new IllegalStateException("Production flight retired: "+dragons.motion()+" "+c.production().combat().diagnostics());
        Location current=dragon.getLocation();
        if(previous!=null){double step=current.distance(previous);maxStep=Math.max(maxStep,step);path+=step;
            maxYaw=Math.max(maxYaw,Math.abs(((current.getYaw()-previous.getYaw()+540)%360)-180));}
        previous=current;chunks.add(current.getChunk().getX()+","+current.getChunk().getZ());
        if(!bounds.contains(dragon.getBoundingBox())||dragon.getParts().stream().anyMatch(p->!bounds.contains(p.getBoundingBox())))
            throw new IllegalStateException("Native geometry escaped");
        if(++tick<480){c.later(1,this::sample);return;}
        c.check("radius_"+radius+"_sustained_geometry",true,path>50&&dragon.getParts().size()==8&&chunks.size()>=3);
        c.check("radius_"+radius+"_step_yaw_phase",true,maxStep<=.25&&maxYaw<=3.00001&&dragon.getPhase()==EnderDragon.Phase.HOVER);
        journal.add(Map.of("radius",radius,"path",path,"maximumStep",maxStep,"maximumYaw",maxYaw,"chunks",List.copyOf(chunks),"motion",dragons.motion().orElseThrow().toString()));
        UUID id=dragon.getUniqueId();dragons.reset(dragons.generation().orElseThrow());
        c.later(5,()->{
            c.check("radius_"+radius+"_reset_cleanup",true,Bukkit.getEntity(id)==null&&c.production().bows().continuity().tickets().demandCount()==0
                    &&c.production().bows().continuity().tickets().reservedCount()==0&&dragons.motion().orElseThrow().state().equals("STOPPED"));
            if(radius==16)trial(24);else if(radius==24)trial(48);else stationary();
        });
    }
    /** Checks exact stationary no-drift before injecting a cancelled native teleport and requiring production retirement. */
    private void stationary() {
        dragons.spawn(DevelopmentDragonService.SpawnMode.CALIBRATION, DragonFlight.Mode.STATIONARY);dragon=(EnderDragon)Bukkit.getEntity(dragons.view().orElseThrow().entityId());first=dragon.getLocation();
        c.later(25,()->{c.check("stationary_calibration_no_drift",true,dragon.getLocation().distance(first)==0&&dragons.motion().orElseThrow().state().equals("STATIONARY"));
            dragons.reset(dragons.generation().orElseThrow());cancel=true;dragons.spawn(DevelopmentDragonService.SpawnMode.CALIBRATION, DragonFlight.Mode.ORBIT);
            UUID id=dragons.view().orElseThrow().entityId();
            c.later(15,()->{cancel=false;c.check("cancelled_motion_retires_without_drift",true,Bukkit.getEntity(id)==null&&c.production().combat().activeCount()==0
                    &&c.production().bows().continuity().tickets().demandCount()==0&&dragons.motion().orElseThrow().state().startsWith("FAILED"));
                redirectedYaw();});});
    }
    /** Checks redirected yaw and externally disabled AI each fail closed and release the production flight resources. */
    private void redirectedYaw() {
        redirectYaw=true;dragons.spawn(DevelopmentDragonService.SpawnMode.CALIBRATION, DragonFlight.Mode.ORBIT);UUID id=dragons.view().orElseThrow().entityId();
        c.later(15,()->{redirectYaw=false;
            c.check("redirected_yaw_retires",true,Bukkit.getEntity(id)==null&&c.production().combat().activeCount()==0
                    &&c.production().bows().continuity().tickets().demandCount()==0&&dragons.motion().orElseThrow().state().startsWith("FAILED"));
            dragons.spawn(DevelopmentDragonService.SpawnMode.CALIBRATION, DragonFlight.Mode.ORBIT);EnderDragon target=(EnderDragon)Bukkit.getEntity(dragons.view().orElseThrow().entityId());
            c.later(10,()->{target.setAI(false);c.later(5,()->{
                c.check("lost_ai_retires",true,Bukkit.getEntity(target.getUniqueId())==null&&c.production().combat().activeCount()==0
                        &&c.production().bows().continuity().tickets().demandCount()==0&&dragons.motion().orElseThrow().state().startsWith("FAILED"));
                c.observe("routes",journal);c.finish();
            });});
        });
    }
    /** Applies only the active fixture cancellation/yaw-redirection control to native dragon teleport events. */
    @EventHandler public void teleport(EntityTeleportEvent event){
        if(!(event.getEntity() instanceof EnderDragon))return;
        if(cancel)event.setCancelled(true);
        if(redirectYaw){Location destination=event.getTo().clone();destination.setYaw(destination.getYaw()+90);event.setTo(destination);}
    }
}
