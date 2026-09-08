package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.OnlyDragonsPlugin;
import com.kaveenk.onlydragons.application.RandomSource;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.paper.encounter.DummyBackend;
import java.util.*;
import net.kyori.adventure.text.Component;
import com.kaveenk.onlydragons.application.PresentationFormatter;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * Player-scoped practice controls and captured-hit explanations on the server thread.
 * Grants use the production equipment codec. Dummy/calibration modes call the same
 * combat pipeline as dragons; fixed random samples are explicitly a development aid.
 * @see com.kaveenk.onlydragons.paper.encounter.ManagedCombatService
 */
public final class PracticeCommand {
    private final OnlyDragonsPlugin plugin;
    /**
     * Binds the command to its existing authority; does not register listeners or tasks.
     * @param plugin constructed plugin graph supplying equipment and the single combat owner
     */
    public PracticeCommand(OnlyDragonsPlugin plugin) { this.plugin = plugin; }
    /**
     * Tests routing only; this does not authorize execution.
     * @param args nonempty root arguments without a leading slash
     * @return true when the namespace belongs to this delegate
     */
    public boolean handles(String[] args) {
        return args[0].equalsIgnoreCase("combat") || (args[0].equalsIgnoreCase("dev") && args.length > 1
                && List.of("kit", "dummy", "scenario", "reset").contains(args[1].toLowerCase(Locale.ROOT)));
    }
    /**
     * Enforces inspect/practice permissions, then grants a kit, opens/resets the caller
     * practice target or displays its captured last hit. Failed combat admission closes
     * the newly created dummy; invalid calibration requests become chat feedback.
     * @param sender requester and feedback recipient
     * @param args nonempty root arguments previously recognized by handles; HP is finite in [1, 1000000]
     */
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
            var e = last.get();
            sender.sendMessage(PresentationFormatter.heading("Last hit · " + PresentationFormatter.label(e.damage().kind().name())));
            sender.sendMessage(PresentationFormatter.value("Health removed", e.damage().amounts().actualHealthDamage())
                    .append(Component.text("  |  ")).append(PresentationFormatter.value("Credited damage", e.damage().amounts().contributionDamage())));
            say(sender, e.summary());
            var s = e.shot();
            say(sender, "Captured weapon=" + s.weapon().definitionId() + " stats=" + s.stats().revision()
                    + " draw=" + s.drawScale() + " projectile=" + s.projectileScale() + " ferocity=" + e.damage().effectiveFerocity());
            say(sender, "Captured damage=" + s.stats().effective(com.kaveenk.onlydragons.domain.stats.StatKey.WEAPON_DAMAGE)
                    + " crit chance=" + s.stats().effective(com.kaveenk.onlydragons.domain.stats.StatKey.CRIT_CHANCE)
                    + " crit damage=" + s.stats().effective(com.kaveenk.onlydragons.domain.stats.StatKey.CRIT_DAMAGE)
                    + " base ferocity=" + s.stats().effective(com.kaveenk.onlydragons.domain.stats.StatKey.FEROCITY));
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
                    sender.sendMessage(PresentationFormatter.heading("Practice kit · " + PresentationFormatter.label(args[2])));
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
    /**
     * Suggests existing loadouts and explicit calibration modes without applying them.
     * @param sender requester used for permission and player-only filtering
     * @param args nonempty root arguments including the partial token
     * @return non-null candidate list, possibly empty; candidates are not prefix-filtered here
     */
    public List<String> complete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args[0].equalsIgnoreCase("combat")) return sender.hasPermission("onlydragons.combat") && args.length == 2 ? List.of("last") : List.of();
        if (!args[0].equalsIgnoreCase("dev") || !sender.hasPermission("onlydragons.practice")) return List.of();
        if (args.length == 2) return List.of("kit", "dummy", "scenario", "reset");
        if (args.length == 3 && args[1].equalsIgnoreCase("kit")) return plugin.equipment().loadouts();
        if (args.length == 3 && (args[1].equalsIgnoreCase("dummy") || args[1].equalsIgnoreCase("scenario"))) return List.of("full", "reduced", "score-only");
        return List.of();
    }
    /**
     * Shows the registered practice profile/kit/reset syntax without creating a target.
     * @param s command recipient
     */
    private static void usage(CommandSender s) { say(s, "Usage: /onlydragons dev kit <loadout> | dummy <full|reduced|score-only> [hp] | scenario <profile> [hp] | reset"); }
    /**
     * Sends practice diagnostics through the shared Adventure formatter.
     * @param s command recipient
     * @param message plain semantic content
     */
    private static void say(CommandSender s, String message) { s.sendMessage(PresentationFormatter.message(message)); }
}
