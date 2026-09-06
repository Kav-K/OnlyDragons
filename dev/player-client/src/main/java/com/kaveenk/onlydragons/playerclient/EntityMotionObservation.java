package com.kaveenk.onlydragons.playerclient;

import java.util.*;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;

/** Bounded received dragon-position segments. Packet evidence only, not rendered-client smoothness. */
final class EntityMotionObservation {
    private record Point(double x,double y,double z) {
        Point { if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Nonfinite entity position"); }
        List<Double> values(){return List.of(x,y,z);}
        double distance(Point p){return Math.hypot(Math.hypot(x-p.x,y-p.y),z-p.z);}
        Point add(double dx,double dy,double dz){return new Point(x+dx,y+dy,z+dz);}
    }
    private static final class Segment {
        final UUID uuid; final Point first; final long started;
        Point last; long ended; int packets; double path,maxStep;
        Segment(UUID uuid,Point p,long now){this.uuid=uuid;first=last=p;started=ended=now;}
        void move(Point p,long now){double step=last.distance(p);path+=step;maxStep=Math.max(maxStep,step);last=p;ended=now;
            if(++packets>10000||!Double.isFinite(path))throw new IllegalStateException("Entity motion capture bound exceeded");}
        Map<String,Object> report(int index){return Map.of("segment",index,"uuid",uuid.toString(),"startedAtEpochMs",started,
                "endedAtEpochMs",ended,"packets",packets,"first",first.values(),"last",last.values(),"path",path,"maxStep",maxStep);}
    }
    private final Map<Integer,Segment> live=new HashMap<>();
    private final List<Segment> history=new ArrayList<>();
    void receive(Packet packet){long now=System.currentTimeMillis();
        if(packet instanceof ClientboundAddEntityPacket p){
            live.remove(p.getEntityId());
            if(p.getType()==EntityType.ENDER_DRAGON){
                if(history.size()>=128)throw new IllegalStateException("Dragon observation segment bound exceeded");
                var segment=new Segment(p.getUuid(),new Point(p.getX(),p.getY(),p.getZ()),now);history.add(segment);live.put(p.getEntityId(),segment);
            }
        } else if(packet instanceof ClientboundRemoveEntitiesPacket p){for(int id:p.getEntityIds())live.remove(id);}
        else if(packet instanceof ClientboundRespawnPacket){live.clear();}
        else if(packet instanceof ClientboundMoveEntityPosPacket p){relative(p.getEntityId(),p.getMoveX(),p.getMoveY(),p.getMoveZ(),now);}
        else if(packet instanceof ClientboundMoveEntityPosRotPacket p){relative(p.getEntityId(),p.getMoveX(),p.getMoveY(),p.getMoveZ(),now);}
        else if(packet instanceof ClientboundEntityPositionSyncPacket p){absolute(p.getId(),new Point(p.getPosition().getX(),p.getPosition().getY(),p.getPosition().getZ()),now);}
        else if(packet instanceof ClientboundTeleportEntityPacket p){var segment=live.get(p.getId());if(segment!=null){var pos=p.getPosition();var flags=p.getRelatives();
            absolute(p.getId(),new Point(pos.getX()+(flags.contains(PositionElement.X)?segment.last.x:0),
                    pos.getY()+(flags.contains(PositionElement.Y)?segment.last.y:0),pos.getZ()+(flags.contains(PositionElement.Z)?segment.last.z:0)),now);}}
    }
    private void relative(int id,double x,double y,double z,long now){var segment=live.get(id);if(segment!=null)segment.move(segment.last.add(x,y,z),now);}
    private void absolute(int id,Point p,long now){var segment=live.get(id);if(segment!=null)segment.move(p,now);}
    List<Map<String,Object>> report(){var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<history.size();i++)rows.add(history.get(i).report(i));return List.copyOf(rows);}
}
