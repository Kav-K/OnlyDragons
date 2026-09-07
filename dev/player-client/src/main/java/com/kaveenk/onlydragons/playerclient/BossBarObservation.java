package com.kaveenk.onlydragons.playerclient;

import java.util.*;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;

/**
 * Records received boss-bar packets and immutable marker-time samples.
 * Access is serialized by the owning session. Bounds are 512 events, eight live bars
 * and 64 unique samples. Received title, style and health prove protocol delivery;
 * they do not prove full-client rendering or the correctness of production damage.
 */
final class BossBarObservation {
    private final Map<UUID, Map<String,Object>> active = new LinkedHashMap<>();
    private final List<Map<String,Object>> events = new ArrayList<>(), samples = new ArrayList<>();
    /** Applies one received bar update by UUID, rejecting duplicate adds, unknown updates and invalid health. */
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
    /** Captures both bounded plain text and styled component JSON without flattening away presentation evidence. */
    private void title(ClientboundBossEventPacket p, Map<String,Object> state) {
        state.put("title",ActionSession.flatten(p.getTitle()));
        String json=GsonComponentSerializer.gson().serialize(p.getTitle());
        ActionSession.check(json.length()<=8192,"Boss bar title bound exceeded"); state.put("titleJson",json);
    }
    /** Records only a finite boss-bar fraction in the inclusive range 0-1. */
    private void health(ClientboundBossEventPacket p, Map<String,Object> state) {
        ActionSession.check(Float.isFinite(p.getHealth())&&p.getHealth()>=0&&p.getHealth()<=1,"Invalid boss health");
        state.put("percent",p.getHealth());
    }
    /** Copies received color and division names without deriving them from plugin state. */
    private void style(ClientboundBossEventPacket p, Map<String,Object> state) { state.put("color",p.getColor().name());state.put("division",p.getDivision().name()); }
    /** Copies the three received environment/presentation flags. */
    private void flags(ClientboundBossEventPacket p, Map<String,Object> state) { state.put("darkenSky",p.isDarkenSky());state.put("music",p.isPlayEndMusic());state.put("fog",p.isShowFog()); }
    /** Freezes current received bars at a unique bounded marker and records how many events preceded it. */
    void sample(String marker) {
        ActionSession.check(marker.matches("[a-z][a-z0-9-]{0,31}")&&samples.size()<64,"Invalid UI sample marker");
        ActionSession.check(samples.stream().noneMatch(s->s.get("marker").equals(marker)),"Duplicate UI sample");
        samples.add(Map.of("marker",marker,"eventCount",events.size(),"bars",active.values().stream().map(Map::copyOf).toList()));
    }
    /** Returns immutable event/sample lists whose prior state copies survive later updates and removals. */
    Map<String,Object> report() { return Map.of("events",List.copyOf(events),"samples",List.copyOf(samples)); }
    /** Reports whether a marker requested UI evidence; unsampled sessions omit this optional receipt block. */
    boolean sampled() { return !samples.isEmpty(); }
}
