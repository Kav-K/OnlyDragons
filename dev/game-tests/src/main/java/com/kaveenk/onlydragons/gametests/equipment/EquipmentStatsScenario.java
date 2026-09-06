package com.kaveenk.onlydragons.gametests.equipment;

import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.stats.*;
import com.kaveenk.onlydragons.paper.item.codec.*;
import com.kaveenk.onlydragons.paper.item.equipment.EquipmentStatsService;
import com.kaveenk.onlydragons.gametests.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static com.kaveenk.onlydragons.domain.stats.StatKey.*;

/** Real item serialization and production service with synthetic UUIDs and a capturing command sender. */
public final class EquipmentStatsScenario implements Scenario {
    @Override public void start(ScenarioContext context) {
        context.mechanicRevision("equipment-stats-v2");
        var stats = context.production().equipment();
        var registry = CalibrationLoadouts.registry(); var codec = new WeaponItemCodec(registry);
        UUID actor = UUID.randomUUID();
        try {
            context.check("server_thread", true, Bukkit.isPrimaryThread());
            context.check("production_service", true, stats != null && EquipmentStatsService.class.getClassLoader()
                    == context.production().getClass().getClassLoader());
            var expectedTotals = Map.of(
                    "ordinary", List.of(100.0, 50.0, 0.0, 0.0),
                    "crit", List.of(100.0, 50.0, 100.0, 0.0),
                    "ferocity_25", List.of(100.0, 50.0, 0.0, 25.0),
                    "ferocity_100", List.of(100.0, 50.0, 0.0, 100.0),
                    "ferocity_500", List.of(100.0, 50.0, 0.0, 500.0),
                    "tracer", List.of(100.0, 50.0, 0.0, 0.0),
                    "duplex", List.of(100.0, 50.0, 0.0, 0.0),
                    "fatal_tempo", List.of(100.0, 50.0, 0.0, 25.0),
                    "shortbow_v1", List.of(100.0, 50.0, 0.0, 0.0),
                    "tracer_return_v2", List.of(100.0, 50.0, 0.0, 0.0));
            var expectedIds = new TreeSet<>(expectedTotals.keySet());
            var actualIds = new TreeSet<>(stats.loadouts());
            context.check("all_loadout_ids", List.copyOf(expectedIds), List.copyOf(actualIds));
            if (!expectedIds.equals(actualIds)) {
                throw new IllegalStateException("Calibration loadout oracle mismatch: expected "
                        + expectedIds + ", actual " + actualIds);
            }
            var observed = new TreeMap<String,List<Double>>();
            for (String id : stats.loadouts()) {
                var stack = ItemStack.deserializeBytes(stats.createLoadout(id).serializeAsBytes());
                var result = stats.refresh(actor, stack, stats.createLoadout("ferocity_500"));
                var snapshot = result.stats().snapshot();
                observed.put(id, List.of(snapshot.raw(WEAPON_DAMAGE),snapshot.raw(CRIT_DAMAGE),snapshot.raw(CRIT_CHANCE),snapshot.raw(FEROCITY)));
            }
            context.check("all_loadout_totals", expectedTotals, observed);
            var original = registry.create("ordinary"); var bow = codec.encode(original);
            var old = stats.refresh(actor, bow, null);
            context.check("repeat_refresh_cached", true, old == stats.refresh(actor, bow.clone(), null));
            var edited = codec.encode(registry.edit(original,Map.of("vicious",3),List.of()));
            var next = stats.refresh(actor, edited, null);
            context.check("same_uuid_edit_refresh", List.of(3.0,0.0),List.of(next.stats().snapshot().raw(FEROCITY),old.stats().snapshot().raw(FEROCITY)));
            context.check("changed_revision", true, !old.stats().snapshot().revision().equals(next.stats().snapshot().revision()));
            context.check("same_uuid_retained", original.identity().instanceId().toString(), ((ItemReadResult.Valid)next.fingerprint().mainHand()).item().instance().identity().instanceId().toString());
            var offOnly = stats.refresh(actor, null, edited);
            context.check("unequip_offhand_inactive",List.of(0.0,0.0,50.0),List.of(offOnly.stats().snapshot().raw(WEAPON_DAMAGE),offOnly.stats().snapshot().raw(FEROCITY),offOnly.stats().snapshot().raw(CRIT_DAMAGE)));
            edited.setAmount(2);
            var invalid = stats.refresh(actor, edited, null);
            context.check("invalid_weapon_profile",true,invalid.fingerprint().mainHand() instanceof ItemReadResult.Invalid && invalid.stats().snapshot().raw(WEAPON_DAMAGE)==0);
            var base = registry.definitions().get("ordinary");
            var rollRegistry = new ItemRegistry("roll-v1", List.of(new ItemDefinition(base.weapon(), base.displayName(),
                    base.material(), Set.of("damage"))), List.of(), Map.of("damage",
                    List.of(new StatModifier("roll:damage", WEAPON_DAMAGE, ModifierOperation.FLAT, 2.5, 0))));
            var rollService = new EquipmentStatsService(rollRegistry, StatProfile.calibration());
            var rollCodec = new WeaponItemCodec(rollRegistry);
            var rollInstance = rollRegistry.create("ordinary");
            var beforeRoll = rollService.refresh(actor, rollCodec.encode(rollInstance), null);
            var rolled = rollCodec.encode(rollRegistry.edit(rollInstance, Map.of(), List.of("damage")));
            var afterRoll = rollService.refresh(actor, ItemStack.deserializeBytes(rolled.serializeAsBytes()), null);
            context.check("same_uuid_roll_refresh", List.of(100.0,102.5), List.of(beforeRoll.stats().snapshot().raw(WEAPON_DAMAGE),afterRoll.stats().snapshot().raw(WEAPON_DAMAGE)));
            context.check("roll_fingerprint_changed",true,!beforeRoll.fingerprint().equals(afterRoll.fingerprint()));
            rollService.clear();
            stats.forget(actor);
            context.check("session_cleanup",true,stats.cached(actor)==null);
            var rejoined = stats.refresh(actor,bow,null);
            context.check("new_session_revision",true,!rejoined.stats().snapshot().revision().equals(old.stats().snapshot().revision()));
            var messages = new ArrayList<String>();
            var sender = sender(messages,true);
            var command = context.production().getCommand("onlydragons");
            command.execute(sender,"onlydragons",new String[]{"stats","explain"});
            context.check("console_stats_rejected",List.of("This command requires a player."),List.copyOf(messages)); messages.clear();
            command.execute(sender,"onlydragons",new String[]{"dev","loadout","ordinary"});
            context.check("console_grant_rejected",List.of("This command requires a player."),List.copyOf(messages)); messages.clear();
            command.execute(sender(messages,false),"onlydragons",new String[]{"dev","loadout","ordinary"});
            context.check("permission_denied",List.of("You do not have permission to use calibration tools."),List.copyOf(messages)); messages.clear();
            command.execute(sender,"onlydragons",new String[]{"status"});
            context.check("status_preserved",true,messages.size()==1 && messages.getFirst().startsWith("OnlyDragons ready | version ")); messages.clear();
            command.execute(sender,"onlydragons",new String[]{"reload"});
            context.check("reload_preserved",List.of("OnlyDragons configuration reloaded."),List.copyOf(messages));
            context.check("reload_keeps_snapshot",true,rejoined==stats.refresh(actor,bow,null));
            context.observe("actor_scope","Synthetic UUID and capturing CommandSender proxy; real production commands/service/codec and native item bytes. No logged-in player, input, authentication or visuals.");
        } finally { stats.forget(actor); }
        context.finish();
    }
    // Public API interface test double only; no reflection into server internals.
    private CommandSender sender(List<String> messages, boolean permission) {
        return (CommandSender)java.lang.reflect.Proxy.newProxyInstance(CommandSender.class.getClassLoader(),new Class<?>[]{CommandSender.class},(proxy,method,args)-> {
            return switch(method.getName()) {
                case "hasPermission" -> args[0].equals("onlydragons.use") || permission;
                case "sendMessage" -> {
                    for (Object arg:args) {
                        if(arg instanceof Component component) messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                        else if(arg instanceof String text) messages.add(text);
                        else if(arg instanceof String[] texts) messages.addAll(List.of(texts));
                    }
                    yield null;
                }
                case "getServer" -> Bukkit.getServer();
                case "getName" -> "SyntheticEquipmentConsole";
                case "isOp" -> permission;
                case "toString" -> "SyntheticEquipmentConsole";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
    }
}
