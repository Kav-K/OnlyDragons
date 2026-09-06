package com.kaveenk.onlydragons.playerclient;

import java.util.*;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;

/** Bounded packet receipt, independent of the production UI and damage services. */
final class BossBarObservation {
    private final Map<UUID, Map<String,Object>> active = new LinkedHashMap<>();
    private final List<Map<String,Object>> events = new ArrayList<>(), samples = new ArrayList<>();
    void receive(ClientboundBossEventPacket packet) {
        ActionSession.check(events.size() < 512, "Boss bar packet bound exceeded");
        UUID id = packet.getUuid(); String action = packet.getAction().name();
        var row = new LinkedHashMap<String,Object>(); row.put("id",id.toString()); row.put("action",action);
        if (action.equals("ADD")) {
            ActionSession.check(!active.containsKey(id) && active.size()<8,"Duplicate/too many boss bars");
            active.put(id,new LinkedHashMap<>());
        } else ActionSession.check(active.containsKey(id),"Unknown boss bar update/removal");
        if (action.equals("REMOVE")) active.remove(id);
        else {
            var state = active.get(id);
            switch (packet.getAction()) {
                case ADD -> {
                    state.put("id",id.toString()); title(packet,state); health(packet,state); style(packet,state); flags(packet,state);
                }
                case UPDATE_TITLE -> title(packet,state);
                case UPDATE_HEALTH -> health(packet,state);
                case UPDATE_STYLE -> style(packet,state);
                case UPDATE_FLAGS -> flags(packet,state);
                default -> throw new IllegalStateException("Unexpected boss action");
            }
            row.put("state",Map.copyOf(state));
        }
        events.add(Map.copyOf(row));
    }
    private void title(ClientboundBossEventPacket p, Map<String,Object> state) {
        state.put("title",ActionSession.flatten(p.getTitle()));
        String json=GsonComponentSerializer.gson().serialize(p.getTitle());
        ActionSession.check(json.length()<=8192,"Boss bar title bound exceeded"); state.put("titleJson",json);
    }
    private void health(ClientboundBossEventPacket p, Map<String,Object> state) {
        ActionSession.check(Float.isFinite(p.getHealth())&&p.getHealth()>=0&&p.getHealth()<=1,"Invalid boss health");
        state.put("percent",p.getHealth());
    }
    private void style(ClientboundBossEventPacket p, Map<String,Object> state) { state.put("color",p.getColor().name());state.put("division",p.getDivision().name()); }
    private void flags(ClientboundBossEventPacket p, Map<String,Object> state) { state.put("darkenSky",p.isDarkenSky());state.put("music",p.isPlayEndMusic());state.put("fog",p.isShowFog()); }
    void sample(String marker) {
        ActionSession.check(marker.matches("[a-z][a-z0-9-]{0,31}")&&samples.size()<64,"Invalid UI sample marker");
        ActionSession.check(samples.stream().noneMatch(s->s.get("marker").equals(marker)),"Duplicate UI sample");
        samples.add(Map.of("marker",marker,"eventCount",events.size(),"bars",active.values().stream().map(Map::copyOf).toList()));
    }
    Map<String,Object> report() { return Map.of("events",List.copyOf(events),"samples",List.copyOf(samples)); }
    boolean sampled() { return !samples.isEmpty(); }
}
