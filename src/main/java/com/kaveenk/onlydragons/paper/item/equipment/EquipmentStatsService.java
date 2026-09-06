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

/** Server-thread session cache. Every refresh revalidates item data before reusing resolved stats. */
public final class EquipmentStatsService {
    public record Fingerprint(ItemReadResult mainHand, ItemReadResult offHand,
                              String registryRevision, StatProfile profile, Map<String, List<StatModifier>> externalSources) {}
    public record Inspection(Fingerprint fingerprint, ExplainedStatSnapshot stats, String notice) {}
    private final ItemRegistry registry;
    private final WeaponItemCodec codec;
    private final StatProfile profile;
    private final Map<UUID, Inspection> sessions = new HashMap<>();
    private final Map<UUID, ModifierSources> overrides = new HashMap<>();
    private final String generation = UUID.randomUUID().toString();
    private long revision;

    public EquipmentStatsService(ItemRegistry registry, StatProfile profile) {
        this.registry = Objects.requireNonNull(registry);
        this.profile = Objects.requireNonNull(profile);
        codec = new WeaponItemCodec(registry);
    }

    public Inspection refresh(Player player) {
        requireThread();
        return refresh(player.getUniqueId(), player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand());
    }

    /** Also usable by integration scenarios with synthetic inventory contents; does not imply a live player. */
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

    /** Replaces one session-only flat bonus; validates the entire candidate before adopting it. */
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

    public void clearBonuses(UUID id) { requireThread(); overrides.remove(id); sessions.remove(id); }
    public void forget(UUID id) { clearBonuses(id); }
    public void clear() { requireThread(); sessions.clear(); overrides.clear(); }
    public int sessionCount() { requireThread(); return sessions.size(); }
    public Inspection cached(UUID id) { requireThread(); return sessions.get(id); }
    public List<String> loadouts() { return registry.definitions().keySet().stream().sorted().toList(); }
    public ItemStack createLoadout(String id) { requireThread(); return codec.encode(registry.create(id)); }

    private Fingerprint fingerprint(ItemStack main, ItemStack off, ModifierSources external) {
        return new Fingerprint(codec.decode(main), codec.decode(off), registry.revision(), profile, external.sources());
    }

    private Inspection resolve(Fingerprint fingerprint) {
        String id = generation + ":" + ++revision;
        var sources = ModifierSources.empty();
        for (var entry : fingerprint.externalSources().entrySet()) sources = sources.replace(entry.getKey(), entry.getValue());
        var stats = fingerprint.mainHand() instanceof ItemReadResult.Valid valid
                ? new StatSnapshotFactory(profile).create(id, valid.item().resolvedWeapon(), sources)
                : new StatResolver(profile).resolve(id, Map.of(), sources);
        return new Inspection(fingerprint, stats, "");
    }

    private static void requireThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Equipment state requires the server thread");
    }
}
