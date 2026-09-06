package com.kaveenk.onlydragons.gametests.fixtures;

import com.google.gson.*;
import com.kaveenk.onlydragons.gametests.ScenarioContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;

/** Real protocol identities and ordered requests. Setup operations are explicitly recorded as setup. */
public final class PlayerFixture implements Listener {
    private final ScenarioContext context;
    private final String hash;
    private final Map<String, JsonObject> plans = new LinkedHashMap<>();
    private final Map<String, UUID> identities = new LinkedHashMap<>();
    private final Map<String, Integer> joins = new HashMap<>(), quits = new HashMap<>(), cursors = new HashMap<>();
    private final Map<String, Player> online = new HashMap<>();
    private final List<PermissionAttachment> permissions = new ArrayList<>();
    private final List<Map<String, Object>> journal = new ArrayList<>();

    public PlayerFixture(ScenarioContext context) throws Exception {
        this.context = context;
        if (!"protocol-actions-v1".equals(System.getProperty("onlydragons.test.playerMode")) || Bukkit.getOnlineMode())
            throw new IllegalStateException("Action fixture requires disposable offline mode");
        byte[] bytes = Files.readAllBytes(Path.of(System.getProperty("onlydragons.test.playerPlan")));
        hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!hash.equals(System.getProperty("onlydragons.test.playerPlanSha256"))) throw new IllegalStateException("Plan hash mismatch");
        JsonObject plan = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        int index = 0;
        for (JsonElement value : plan.getAsJsonArray("actors")) {
            JsonObject actor = value.getAsJsonObject();
            String id = actor.get("id").getAsString();
            String name = "od_" + context.harness().runId().substring(0, 10) + "_" + index++;
            plans.put(id, actor);
            identities.put(id, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
        }
        context.listen(this);
        context.cleanup("player-fixture", () -> {
            permissions.forEach(attachment -> attachment.getPermissible().removeAttachment(attachment));
            permissions.clear();
        });
    }

    public UUID identity(String actor) { return Objects.requireNonNull(identities.get(actor), "Unknown actor"); }
    public Player player(String actor) { return Objects.requireNonNull(online.get(actor), "Actor not online: " + actor); }
    public int joins(String actor) { return joins.getOrDefault(actor, 0); }
    public int quits(String actor) { return quits.getOrDefault(actor, 0); }
    public boolean allOnline() { return online.size() == plans.size(); }
    public List<Map<String, Object>> journal() { return List.copyOf(journal); }

    private String actor(Player player) {
        return identities.entrySet().stream().filter(entry -> entry.getValue().equals(player.getUniqueId()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void joined(PlayerJoinEvent event) {
        String actor = actor(event.getPlayer());
        if (actor == null) return;
        int count = joins.merge(actor, 1, Integer::sum);
        if (count > plans.get(actor).getAsJsonArray("sessions").size() || online.containsKey(actor)) {
            context.fail(new IllegalStateException("Unexpected actor session")); return;
        }
        online.put(actor, event.getPlayer());
        cursors.put(actor, 0);
        record("join", actor, Map.of("uuid", event.getPlayer().getUniqueId().toString(), "sessionOrdinal", count));
    }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) {
        String actor = actor(event.getPlayer());
        if (actor == null) return;
        online.remove(actor);
        quits.merge(actor, 1, Integer::sum);
        record("quit", actor, Map.of("uuid", event.getPlayer().getUniqueId().toString()));
    }
    public void setupPosition(String actor, Location location) {
        record("server-setup-position", actor, Map.of("x", location.getX(), "y", location.getY(), "z", location.getZ()));
        if (!player(actor).teleport(location)) throw new IllegalStateException("Fixture teleport rejected");
    }
    public void setupItem(String actor, int slot, ItemStack stack) {
        record("server-setup-item", actor, Map.of("slot", slot, "material", stack.getType().name(), "amount", stack.getAmount()));
        player(actor).getInventory().setItem(slot, stack);
    }
    public PermissionAttachment permission(String actor, String permission, boolean value) {
        record("server-setup-permission", actor, Map.of("permission", permission, "value", value));
        PermissionAttachment attachment = player(actor).addAttachment(context.harness(), permission, value);
        permissions.add(attachment);
        return attachment;
    }
    public void removePermission(PermissionAttachment attachment) {
        if (permissions.remove(attachment)) attachment.getPermissible().removeAttachment(attachment);
    }
    public void bind(String target, UUID entity) {
        online.forEach((actor, player) -> {
            player.sendMessage(Component.text("OD_BIND:" + context.harness().runId() + ":" + hash + ":" + target + ":" + entity));
            record("bind", actor, Map.of("targetRef", target, "uuid", entity.toString()));
        });
    }
    public void request(String actor, String step) {
        JsonObject session = plans.get(actor).getAsJsonArray("sessions").get(joins(actor) - 1).getAsJsonObject();
        JsonArray steps = session.getAsJsonArray("steps");
        int cursor = cursors.get(actor);
        if (cursor >= steps.size() || !steps.get(cursor).getAsJsonObject().get("id").getAsString().equals(step))
            throw new IllegalArgumentException("Out-of-order fixture request: " + actor + "/" + step);
        cursors.put(actor, cursor + 1);
        record("request", actor, Map.of("session", session.get("id").getAsString(), "step", step));
        player(actor).sendMessage(Component.text("OD_ACTION:" + context.harness().runId() + ":" + hash + ":" + actor + ":"
                + session.get("id").getAsString() + ":" + step));
    }
    public void await(String label, int ticks, BooleanSupplier condition, ScenarioContext.Step next) {
        if (condition.getAsBoolean()) { context.later(1, next); return; }
        if (ticks <= 0) { context.fail(new IllegalStateException("Timed out awaiting " + label)); return; }
        context.later(1, () -> await(label, ticks - 1, condition, next));
    }
    private void record(String kind, String actor, Map<String, Object> detail) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Player fixture requires server thread");
        if (journal.size() >= 512) throw new IllegalStateException("Player fixture journal exceeded bound");
        journal.add(Map.of("kind", kind, "actor", actor, "tick", Bukkit.getCurrentTick(), "detail", detail));
        context.observe("playerFixture", journal());
    }
}
