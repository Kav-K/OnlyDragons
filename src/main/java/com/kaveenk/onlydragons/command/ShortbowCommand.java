package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.application.PresentationFormatter;
import com.kaveenk.onlydragons.domain.item.ShortbowLoadouts;
import com.kaveenk.onlydragons.domain.item.WeaponDefinition;
import com.kaveenk.onlydragons.domain.projectile.FiringRules;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import com.kaveenk.onlydragons.paper.item.equipment.ShortbowKitService;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Discoverable training-kit commands; existing dragon controls own setup and spawn.
 * Server-thread execution reports declared calibration cadence rather than sampling
 * live shot timing, and delegates all inventory writes to {@link ShortbowKitService}.
 */
public final class ShortbowCommand {
    private final ShortbowKitService kits;
    /**
     * Binds the command to its existing authority; does not register listeners or tasks.
     * @param kits empty-slot-only kit grant service
     */
    public ShortbowCommand(ShortbowKitService kits) { this.kits = kits; }

    /**
     * Tests routing only; this does not authorize execution.
     * @param args root arguments without a leading slash
     * @return true when the namespace belongs to this delegate
     */
    public boolean handles(String[] args) {
        return args.length >= 2 && args[0].equalsIgnoreCase("dev") && args[1].equalsIgnoreCase("shortbow");
    }

    /**
     * Handles help/list/kit after calibration permission checks. Only kit requires a
     * player; insufficient empty storage leaves the inventory untouched.
     * @param sender requester and feedback recipient
     * @param args root arguments beginning with dev shortbow; omitted action shows help
     */
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("onlydragons.calibration")) {
            say(sender, "You do not have permission to use calibration tools."); return;
        }
        if (args.length > 3) { help(sender); return; }
        String action = args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "kit" -> {
                if (!(sender instanceof Player player)) { say(sender, "This command requires a player."); return; }
                if (!kits.grant(player)) { say(sender, "Kit needs 15 empty storage slots; nothing granted."); return; }
                say(sender, "Granted seven training bows and 512 arrows. Duplex V and Fatal Tempo V are on separate bows.");
                say(sender, "Use /onlydragons dev shortbow list, then spawn training orbit before entering the arena.");
            }
            case "list" -> list(sender);
            default -> help(sender);
        }
    }

    /**
     * Displays declared loadout defaults and tick-based cadence; development bonuses
     * and later book edits may change the actual held snapshot.
     * @param sender authorized requester receiving the catalog explanation
     */
    private void list(CommandSender sender) {
        sender.sendMessage(PresentationFormatter.heading("Training bows · calibration"));
        for (var definition : ShortbowLoadouts.definitions()) {
            var weapon = definition.weapon();
            double speed = weapon.statModifiers().stream().filter(m -> m.key() == StatKey.ATTACK_SPEED).mapToDouble(m -> m.amount()).sum();
            double ferocity = weapon.statModifiers().stream().filter(m -> m.key() == StatKey.FEROCITY).mapToDouble(m -> m.amount()).sum();
            String mode = weapon.firingMode() == WeaponDefinition.FiringMode.DRAWN_BOW ? "draw and release"
                    : "instant; hold right-click; AS " + PresentationFormatter.number(speed) + "; " + FiringRules.cooldown(speed)
                    + " ticks/trigger (" + PresentationFormatter.number(20.0 / FiringRules.cooldown(speed)) + "/s at 20 TPS)";
            say(sender, weapon.id() + " · " + definition.displayName() + " · " + mode + "; F " + PresentationFormatter.number(ferocity));
            if (!weapon.enchantments().isEmpty()) say(sender, "  " + String.join(", ", weapon.enchantments().stream()
                    .map(e -> PresentationFormatter.label(e.id()) + " " + PresentationFormatter.roman(e.level())).toList()));
        }
        say(sender, "Defaults: damage 100, crit chance 0%, crit damage 50%. One primary/trigger; Duplex adds one child next tick at 20% damage, no extra ammo.");
        say(sender, "Combined bows: aimed Tracer V (v3: 20-block acquisition, 30-degree launch half-angle), Quiver X (50% ammo saving), Flame II. Tempo starts at 25 Ferocity and builds on hits.");
        say(sender, "25 Ferocity = 25% extra hit; 100 Ferocity = one guaranteed extra hit before buffs. No Vicious bonus in these defaults.");
        say(sender, "Grant one: /onlydragons dev loadout <id>. Inspect equipped values with /onlydragons stats explain; bonuses and book edits may change defaults.");
    }

    /**
     * Explains the operator sequence without automatically configuring an arena,
     * teleporting a player or spawning a target.
     * @param sender authorized requester receiving the setup instructions
     */
    private void help(CommandSender sender) {
        say(sender, "Usage: /onlydragons dev shortbow [list|kit|help]");
        say(sender, "1. /onlydragons dev dragon status · setup only if unconfigured: /onlydragons dev dragon setup minecraft:overworld 0 100 0 24 test_dragon");
        say(sender, "2. /onlydragons dev shortbow kit · needs 15 empty storage slots.");
        say(sender, "3. /onlydragons dev dragon spawn training orbit · then /onlydragons dev dragon status (100,000 HP).");
        say(sender, "4. Enter only after the operator confirms a safe standing pad inside that configured cube. See dev/shortbow-play.md for positioning commands.");
    }

    /**
     * Offers help/list/kit only to a sender allowed to use calibration tools.
     * @param sender requester used for permission filtering
     * @param args nonempty root arguments including the partial token
     * @return non-null candidate list, possibly empty; candidates are not prefix-filtered here
     */
    public List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("onlydragons.calibration") || !args[0].equalsIgnoreCase("dev")) return List.of();
        if (args.length == 2) return List.of("shortbow");
        return handles(args) && args.length == 3 ? (sender instanceof Player ? List.of("list", "kit", "help") : List.of("list", "help")) : List.of();
    }
    /**
     * Sends semantic command text through the shared Adventure formatter.
     * @param sender command recipient
     * @param text plain semantic content; formatting does not change command state
     */
    private static void say(CommandSender sender, String text) { sender.sendMessage(PresentationFormatter.message(text)); }
}
