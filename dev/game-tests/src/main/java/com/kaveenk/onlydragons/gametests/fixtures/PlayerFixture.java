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

/**
 * Server-side coordinator for the plan's real protocol connections.
 * Offline UUIDs derive from the run prefix and actor index, so reconnect preserves
 * identity while join ordinals distinguish sessions. A request records permission
 * to send a declared action; it does not prove packet receipt or gameplay success.
 * Setup operations are journaled separately and run only in the disposable profile.
 */
public final class PlayerFixture implements Listener {
    private final ScenarioContext context;
    private final String hash;
    private final Map<String, JsonObject> plans = new LinkedHashMap<>();
    private final Map<String, UUID> identities = new LinkedHashMap<>();
    private final Map<String, Integer> joins = new HashMap<>(), quits = new HashMap<>(), cursors = new HashMap<>();
    private final Map<String, Player> online = new HashMap<>();
    private final List<PermissionAttachment> permissions = new ArrayList<>();
    private final List<Map<String, Object>> journal = new ArrayList<>();

    /**
     * Binds identities to the exact staged plan bytes and registers owned listener cleanup.
     * @param context current scenario's server-thread report/resource owner
     * @throws Exception if mode, online-mode policy, plan I/O or the staged hash is invalid
     */
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

    /**
     * Returns the deterministic UUID declared for an actor, even between connections.
     * @param actor plan actor ID
     * @return stable offline UUID
     * @throws NullPointerException for an unknown actor
     */
    public UUID identity(String actor) { return Objects.requireNonNull(identities.get(actor), "Unknown actor"); }
    /**
     * Resolves only the currently joined connection, never a retained pre-reconnect Player.
     * @param actor plan actor ID
     * @return live player from the latest accepted join
     * @throws NullPointerException when that actor is offline
     */
    public Player player(String actor) { return Objects.requireNonNull(online.get(actor), "Actor not online: " + actor); }
    /**
     * Counts observed native joins, including reconnects.
     * @param actor plan actor ID
     * @return observed count, zero before the first join
     */
    public int joins(String actor) { return joins.getOrDefault(actor, 0); }
    /**
     * Counts observed native quits independently of client disconnect claims.
     * @param actor plan actor ID
     * @return observed quit count
     */
    public int quits(String actor) { return quits.getOrDefault(actor, 0); }
    /**
     * Checks whether every declared actor currently has an observed connection.
     * @return whether all plan identities are online; it does not prove client loading ACKs
     */
    public boolean allOnline() { return online.size() == plans.size(); }
    /**
     * Copies the ordered server journal for external plan/receipt cross-checking.
     * @return immutable list snapshot of setup, join, quit, binding and request rows
     */
    public List<Map<String, Object>> journal() { return List.copyOf(journal); }

    /**
     * Maps a native player's UUID to the declared roster; unrelated players are ignored.
     */
    private String actor(Player player) {
        return identities.entrySet().stream().filter(entry -> entry.getValue().equals(player.getUniqueId()))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    /**
     * Observes joins and resets the action cursor for the next declared session.
     * Duplicate live connections and extra sessions fail the scenario.
     * @param event native join event, observed without synthesizing a player
     */
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
    /**
     * Drops the current live connection and records a native quit for that actor.
     * @param event native quit event
     */
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) {
        String actor = actor(event.getPlayer());
        if (actor == null) return;
        online.remove(actor);
        quits.merge(actor, 1, Integer::sum);
        record("quit", actor, Map.of("uuid", event.getPlayer().getUniqueId().toString()));
    }
    /**
     * Teleports fixture setup and labels it as server setup rather than client movement.
     * @param actor online actor to position
     * @param location prepared safe fixture location
     * @throws IllegalStateException if Paper rejects the teleport
     */
    public void setupPosition(String actor, Location location) {
        record("server-setup-position", actor, Map.of("x", location.getX(), "y", location.getY(), "z", location.getZ()));
        if (!player(actor).teleport(location)) throw new IllegalStateException("Fixture teleport rejected");
    }
    /**
     * Sets a fixture inventory slot and records the setup operation explicitly.
     * @param actor online actor
     * @param slot Bukkit player-inventory slot, not a raw container slot
     * @param stack setup item whose identity is checked by the consuming scenario
     */
    public void setupItem(String actor, int slot, ItemStack stack) {
        record("server-setup-item", actor, Map.of("slot", slot, "material", stack.getType().name(), "amount", stack.getAmount()));
        player(actor).getInventory().setItem(slot, stack);
    }
    /**
     * Adds a tracked permission override for denial/grant trials.
     * @param actor online actor
     * @param permission exact production permission node
     * @param value explicit allowed or denied state
     * @return attachment that can be released early or by context cleanup
     */
    public PermissionAttachment permission(String actor, String permission, boolean value) {
        record("server-setup-permission", actor, Map.of("permission", permission, "value", value));
        PermissionAttachment attachment = player(actor).addAttachment(context.harness(), permission, value);
        permissions.add(attachment);
        return attachment;
    }
    /**
     * Releases only an attachment owned by this fixture, at most once.
     * @param attachment previously returned fixture attachment
     */
    public void removePermission(PermissionAttachment attachment) {
        if (permissions.remove(attachment)) attachment.getPermissible().removeAttachment(attachment);
    }
    /**
     * Announces a real entity UUID to all currently connected actors before targeted actions.
     * Each reconnect must receive its own binding; this is not an entity-spawn packet.
     * @param target plan target reference
     * @param entity UUID of the real server entity
     */
    public void bind(String target, UUID entity) {
        online.forEach((actor, player) -> {
            player.sendMessage(Component.text("OD_BIND:" + context.harness().runId() + ":" + hash + ":" + target + ":" + entity));
            record("bind", actor, Map.of("targetRef", target, "uuid", entity.toString()));
        });
    }
    /**
     * Sends only the next declared step of the actor's current session.
     * The run/plan/actor/session/step marker lets the client reject stale requests;
     * production event and received-message assertions must still prove the outcome.
     * @param actor online plan actor
     * @param step exact next step ID
     * @throws IllegalArgumentException for an out-of-order or extra request
     */
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
    /**
     * Polls a server-thread condition once per tick with a finite remaining budget.
     * Even an immediately true condition schedules the next stage one tick later,
     * avoiding same-dispatch recursion through event handlers.
     * @param label diagnostic description on timeout
     * @param ticks remaining server-tick budget
     * @param condition observed readiness predicate, not a fabricated completion
     * @param next stage scheduled after the condition holds
     */
    public void await(String label, int ticks, BooleanSupplier condition, ScenarioContext.Step next) {
        if (condition.getAsBoolean()) { context.later(1, next); return; }
        if (ticks <= 0) { context.fail(new IllegalStateException("Timed out awaiting " + label)); return; }
        context.later(1, () -> await(label, ticks - 1, condition, next));
    }
    /**
     * Appends a bounded primary-thread journal row and refreshes the report observation.
     * The 512-row limit fails explicitly rather than truncating replay provenance.
     */
    private void record(String kind, String actor, Map<String, Object> detail) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Player fixture requires server thread");
        if (journal.size() >= 512) throw new IllegalStateException("Player fixture journal exceeded bound");
        journal.add(Map.of("kind", kind, "actor", actor, "tick", Bukkit.getCurrentTick(), "detail", detail));
        context.observe("playerFixture", journal());
    }
}
