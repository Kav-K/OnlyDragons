package com.kaveenk.onlydragons.paper.item.equipment;

import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.stats.*;
import com.kaveenk.onlydragons.paper.item.codec.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Server-thread cache of fully revalidated equipment and session-only bonuses.
 * Every refresh decodes both hands; only valid main-hand weapon sources enter the
 * stat factory, exactly once. Returned inspections retain immutable old snapshots.
 * Use {@link #refresh(Player)} at acceptance, never {@link #cached(UUID)} as authority.
 */
public final class EquipmentStatsService {
    /**
     * Value identity for cache reuse, including same-UUID edits and external sources.
     * Service-created values contain immutable resolved inputs; this record itself does
     * not defensively copy arbitrary caller-supplied maps.
     * @param mainHand typed decoded active weapon; invalid/unmanaged uses profile defaults
     * @param offHand typed decoded offhand, inspectable but statistically inactive
     * @param registryRevision catalog/router identity used for this resolution
     * @param profile exact immutable stat profile
     * @param externalSources source-keyed session modifier lists, disjoint from weapon sources
     */
    public record Fingerprint(ItemReadResult mainHand, ItemReadResult offHand,
                              String registryRevision, StatProfile profile, Map<String, List<StatModifier>> externalSources) {}
    /**
     * Resolved equipment observation, safe to retain across later cache refreshes.
     * @param fingerprint full inputs associated with this snapshot
     * @param stats immutable snapshot and arithmetic explanation
     * @param notice empty normally, or a diagnostic explaining dropped incompatible bonuses
     */
    public record Inspection(Fingerprint fingerprint, ExplainedStatSnapshot stats, String notice) {}
    private final ItemRegistry registry;
    private final WeaponItemCodec codec;
    private final StatProfile profile;
    private final Map<UUID, Inspection> sessions = new HashMap<>();
    private final Map<UUID, ModifierSources> overrides = new HashMap<>();
    private final String generation = UUID.randomUUID().toString();
    private long revision;

    /**
     * Binds an immutable catalog and stat profile to a new session-cache generation.
     * @param registry non-null trusted definitions/router
     * @param profile non-null stat ranges, defaults and aggregation policies
     * @throws NullPointerException if either dependency is null
     */
    public EquipmentStatsService(ItemRegistry registry, StatProfile profile) {
        this.registry = Objects.requireNonNull(registry);
        this.profile = Objects.requireNonNull(profile);
        codec = new WeaponItemCodec(registry);
    }

    /**
     * Revalidates the player's actual hands before considering cache reuse.
     * @param player live inventory owner on the server thread
     * @return current complete inspection; unchanged inputs may return the previous instance
     * @throws IllegalStateException if called off the server thread
     * @throws IllegalArgumentException if equipment cannot resolve even without session bonuses
     */
    public Inspection refresh(Player player) {
        requireThread();
        return refresh(player.getUniqueId(), player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand());
    }

    /**
     * Resolves supplied inventory contents without asserting that a live player exists.
     * Same-value inputs reuse the inspection. If old bonuses become incompatible with
     * new equipment, validates the bonus-free fallback before clearing those bonuses.
     * @param playerId non-null cache/session owner
     * @param mainHand active stack, nullable; decoded again on every call
     * @param offHand inactive stack, nullable; retained for inspection only
     * @return immutable current inspection, with a notice when bonuses were cleared
     * @throws IllegalStateException if called off the server thread
     * @throws IllegalArgumentException if base equipment/profile resolution fails
     * @throws NullPointerException if playerId is null
     */
    public Inspection refresh(UUID playerId, ItemStack mainHand, ItemStack offHand) {
        requireThread();
        Objects.requireNonNull(playerId);
        var fingerprint = fingerprint(mainHand, offHand, overrides.getOrDefault(playerId, ModifierSources.empty()));
        var previous = sessions.get(playerId);
        if (previous != null && previous.fingerprint().equals(fingerprint)) return previous;
        Inspection next;
        try { next = resolve(fingerprint); }
        catch (IllegalArgumentException incompatible) {
            if (fingerprint.externalSources().isEmpty()) throw incompatible;
            // A bonus valid with yesterday's weapon may overflow with today's. Drop session
            // bonuses atomically rather than keeping stale equipment or throwing each event.
            var fallback = resolve(fingerprint(mainHand, offHand, ModifierSources.empty()));
            overrides.remove(playerId);
            next = new Inspection(fallback.fingerprint(), fallback.stats(),
                    "Session bonuses cleared because they exceed the profile with this equipment.");
        }
        sessions.put(playerId, next);
        return next;
    }

    /**
     * Atomically replaces one session-only flat source after whole-candidate validation.
     * Zero removes that source. No live player attributes or persistent item data change.
     * @param player live owner whose current hands are revalidated
     * @param key stat receiving the development source
     * @param amount finite nonnegative stat units within that profile's raw range
     * @return newly resolved inspection after adoption
     * @throws IllegalArgumentException if amount or combined inputs exceed profile constraints
     * @throws IllegalStateException if called off the server thread
     */
    public Inspection bonus(Player player, StatKey key, double amount) {
        requireThread();
        profile.definitions().get(key).validateRaw(amount);
        UUID id = player.getUniqueId();
        String source = "dev:bonus:" + key.id();
        var candidate = overrides.getOrDefault(id, ModifierSources.empty()).replace(source,
                amount == 0 ? List.of() : List.of(new StatModifier(source, key, ModifierOperation.FLAT, amount, 0)));
        var next = resolve(fingerprint(player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand(), candidate));
        overrides.put(id, candidate);
        sessions.put(id, next);
        return next;
    }

    /**
     * Drops bonuses and cached inspection so the next refresh recomputes defaults.
     * @param id session owner; absent owners are harmless
     * @throws IllegalStateException if called off the server thread
     */
    public void clearBonuses(UUID id) { requireThread(); overrides.remove(id); sessions.remove(id); }
    /**
     * Forgets all cached/bonus state for a dead or disconnected owner.
     * @param id owner to forget; does not affect already returned snapshots
     * @throws IllegalStateException if called off the server thread
     */
    public void forget(UUID id) { clearBonuses(id); }
    /**
     * Clears all sessions and bonuses on the server thread; retained snapshots stay valid.
     */
    public void clear() { requireThread(); sessions.clear(); overrides.clear(); }
    /**
     * Reports currently cached inspections, not an online-player count.
     * @return cache entry count, zero after clear
     * @throws IllegalStateException if called off the server thread
     */
    public int sessionCount() { requireThread(); return sessions.size(); }
    /**
     * Reads a possibly stale diagnostic cache entry without inspecting equipment.
     * @param id owner to look up
     * @return cached inspection, or null when absent
     * @throws IllegalStateException if called off the server thread
     */
    public Inspection cached(UUID id) { requireThread(); return sessions.get(id); }
    /**
     * Lists exposed trusted loadout IDs for development command discovery.
     * @return lexicographically sorted immutable ID list
     */
    public List<String> loadouts() { return registry.definitions().keySet().stream().sorted().toList(); }
    /**
     * Allocates and encodes a fresh managed identity; does not give it to a player.
     * @param id trusted exposed definition ID
     * @return new amount-one managed stack for caller-owned placement
     * @throws IllegalArgumentException if the definition is unknown or invalid
     * @throws IllegalStateException if called off the server thread
     */
    public ItemStack createLoadout(String id) { requireThread(); return codec.encode(registry.create(id)); }

    /**
     * Decodes full hand values and captures external sources, never just UUIDs.
     * @param main nullable active stack
     * @param off nullable inactive stack
     * @param external immutable source set
     * @return complete cache comparison value
     */
    private Fingerprint fingerprint(ItemStack main, ItemStack off, ModifierSources external) {
        return new Fingerprint(codec.decode(main), codec.decode(off), registry.revision(), profile, external.sources());
    }

    /**
     * Allocates a new revision and applies the resolved main-hand weapon exactly once.
     * Invalid/unmanaged hands fall back to stat-profile defaults; offhand never contributes.
     * @param fingerprint captured inputs whose external sources must not collide with weapon sources
     * @return new immutable inspection with no notice
     * @throws IllegalArgumentException if arithmetic/source/range validation fails
     */
    private Inspection resolve(Fingerprint fingerprint) {
        String id = generation + ":" + ++revision;
        var sources = ModifierSources.empty();
        for (var entry : fingerprint.externalSources().entrySet()) sources = sources.replace(entry.getKey(), entry.getValue());
        var stats = fingerprint.mainHand() instanceof ItemReadResult.Valid valid
                ? new StatSnapshotFactory(profile).create(id, valid.item().resolvedWeapon(), sources)
                : new StatResolver(profile).resolve(id, Map.of(), sources);
        return new Inspection(fingerprint, stats, "");
    }

    /**
     * Rejects cache and inventory access outside classic Paper server-thread ownership.
     */
    private static void requireThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Equipment state requires the server thread");
    }
}
