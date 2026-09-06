package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.paper.encounter.DummyBackend;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/** Bounded practice commands; all grants use the existing production equipment/codec service. */
public final class PracticeCommand {
    private final OnlyDragonsPlugin plugin;
    public PracticeCommand(OnlyDragonsPlugin plugin) { this.plugin = plugin; }
    public boolean handles(String[] args) {
        return args[0].equalsIgnoreCase("combat") || (args[0].equalsIgnoreCase("dev") && args.length > 1
                && List.of("kit", "dummy", "scenario", "reset").contains(args[1].toLowerCase(Locale.ROOT)));
    }
    public void execute(CommandSender sender, String[] args) {
        boolean inspect = args[0].equalsIgnoreCase("combat");
        if (!sender.hasPermission(inspect ? "onlydragons.combat" : "onlydragons.practice")) {
            say(sender, "You do not have permission to " + (inspect ? "inspect combat." : "use practice tools.")); return;
        }
        if (!(sender instanceof Player p)) { say(sender, "This command requires a player."); return; }
        if (inspect) {
            if (args.length != 2 || !args[1].equalsIgnoreCase("last")) { say(sender, "Usage: /onlydragons combat last"); return; }
            var last = plugin.combat().last(p.getUniqueId());
            if (last.isEmpty()) { say(sender, "No combat hit in this session."); return; }
            var e = last.get(); say(sender, e.summary());
            var s = e.shot();
            say(sender, "Captured weapon=" + s.weapon().definitionId() + " stats=" + s.stats().revision()
                    + " draw=" + s.drawScale() + " projectile=" + s.projectileScale() + " ferocity=" + e.damage().effectiveFerocity());
            say(sender, "Profile=" + e.damage().mechanic() + " collision tick=" + e.collisionTick() + " commit tick=" + e.damage().tick()
                    + " procs=" + e.admission() + " modifiers=" + e.damage().modifierBreakdown());
            return;
        }
        try {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "kit" -> {
                    if (args.length != 3) { usage(sender); return; }
                    var bow = plugin.equipment().createLoadout(args[2].toLowerCase(Locale.ROOT));
                    var empty = new ArrayList<Integer>();
                    var inventory = p.getInventory();
                    for (int i = 0; i < inventory.getStorageContents().length; i++) {
                        var item = inventory.getItem(i); if (item == null || item.getType().isAir()) empty.add(i);
                    }
                    if (empty.size() < 2) { say(sender, "Two empty storage slots required; no kit granted."); return; }
                    inventory.setItem(empty.get(0), bow); inventory.setItem(empty.get(1), new ItemStack(Material.ARROW, 64));
                    say(sender, "Practice kit " + args[2] + " granted with 64 arrows; equip the bow and use /onlydragons stats explain.");
                }
                case "reset" -> {
                    if (args.length != 2) { usage(sender); return; }
                    plugin.combat().reset(p.getUniqueId()); say(sender, "Your practice target reset.");
                }
                case "dummy", "scenario" -> {
                    if (args.length < 3 || args.length > 4) { usage(sender); return; }
                    if (plugin.combat().owned(p.getUniqueId()).isPresent()) throw new IllegalArgumentException("Use dev reset first.");
                    String mode = args[2].toLowerCase(Locale.ROOT);
                    double coefficient = switch (mode) { case "full" -> 1; case "reduced" -> .25; case "score-only" -> 0;
                        default -> throw new IllegalArgumentException("Profile must be full, reduced or score-only."); };
                    double hp = args.length == 4 ? Double.parseDouble(args[3]) : 1000;
                    if (!Double.isFinite(hp) || hp < 1 || hp > 1_000_000) throw new IllegalArgumentException("HP must be 1–1000000.");
                    boolean controlled = args[1].equalsIgnoreCase("scenario");
                    var profile = new CombatProfile(new MechanicRevision("practice-" + mode + (controlled ? "-cycle" : ""), "v1"),
                            CombatProfile.Mitigation.NONNEGATIVE_DEFENSE, CombatProfile.Cap.NONE, coefficient);
                    var location = p.getLocation();
                    var direction = location.getDirection().setY(0);
                    if (direction.lengthSquared() < 1e-8) direction.setZ(1);
                    var target = location.clone().add(direction.normalize().multiply(8));
                    if (!target.getBlock().isPassable() || !target.clone().add(0, 1, 0).getBlock().isPassable())
                        throw new IllegalArgumentException("Target position obstructed; face open space.");
                    var backend = new DummyBackend(target);
                    int[] sample = {0}; RandomSource random = controlled ? () -> (sample[0]++ % 4) / 4.0 : Math::random;
                    UUID id;
                    try {
                        id = plugin.combat().open(p.getUniqueId(), backend, BoundingBox.of(location, 24, 24, 24), hp, 0,
                                "practice_dummy", profile, Optional.empty(), random);
                    } catch (RuntimeException failure) { backend.close(); throw failure; }
                    say(sender, "Practice " + mode + " ready | HP=" + hp + " ferocity HP fraction=" + coefficient
                            + " | " + (controlled ? "controlled fractional cycle 0/0.25/0.5/0.75" : "random ferocity") + " | " + id);
                    say(sender, "Calibration only; full draws: ordinary=100, crit=150. Inspect /onlydragons combat last; repeat with dev reset.");
                }
                default -> usage(sender);
            }
        } catch (IllegalArgumentException invalid) { say(sender, "Invalid practice request: " + invalid.getMessage()); }
    }
    public List<String> complete(CommandSender sender, String[] args) {
        if (args[0].equalsIgnoreCase("combat")) return sender.hasPermission("onlydragons.combat") && args.length == 2 ? List.of("last") : List.of();
        if (!args[0].equalsIgnoreCase("dev") || !sender.hasPermission("onlydragons.practice")) return List.of();
        if (args.length == 2) return List.of("kit", "dummy", "scenario", "reset");
        if (args.length == 3 && args[1].equalsIgnoreCase("kit")) return plugin.equipment().loadouts();
        if (args.length == 3 && (args[1].equalsIgnoreCase("dummy") || args[1].equalsIgnoreCase("scenario"))) return List.of("full", "reduced", "score-only");
        return List.of();
    }
    private static void usage(CommandSender s) { say(s, "Usage: /onlydragons dev kit <loadout> | dummy <full|reduced|score-only> [hp] | scenario <profile> [hp] | reset"); }
    private static void say(CommandSender s, String message) { s.sendMessage(Component.text(message)); }
}
