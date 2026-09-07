package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.paper.encounter.*;
import java.io.IOException;
import java.util.*;
import com.kaveenk.onlydragons.application.PresentationFormatter;
import org.bukkit.command.CommandSender;

/**
 * Operator-facing parser for persisted arena setup and one managed dragon generation.
 * It never infers an arena from a player position or depends on the test companion.
 * Calls belong to the server thread. Native ownership, damage and ranking stay in
 * {@link DevelopmentDragonService} and its collaborators.
 */
public final class DragonCommand {
    private final DevelopmentDragonService dragons;
    /**
     * Binds the command to its existing authority; does not register listeners or tasks.
     * @param dragons single configured development lifecycle owner
     */
    public DragonCommand(DevelopmentDragonService dragons) { this.dragons = dragons; }
    /**
     * Tests routing only; this does not authorize execution.
     * @param args root arguments without a leading slash
     * @return true when the namespace belongs to this delegate
     */
    public boolean handles(String[] args) { return args.length >= 2 && args[0].equalsIgnoreCase("dev") && args[1].equalsIgnoreCase("dragon"); }
    /**
     * Parses setup, spawn, status, reset or result, translating validation/state/I/O
     * failures into operator feedback. Human spawn defaults to STANDARD plus ORBIT;
     * explicit generation tokens cannot address another generation.
     * @param sender requester and feedback recipient
     * @param args complete root arguments starting with dev dragon; coordinates and radius are blocks
     */
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("onlydragons.practice")) { say(sender, "You do not have permission to use dragon controls."); return; }
        try {
            String action = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "status";
            switch (action) {
                case "setup" -> {
                    if (args.length != 9) { usage(sender); return; }
                    dragons.setup(new DevelopmentArena(args[3], Double.parseDouble(args[4]), Double.parseDouble(args[5]),
                            Double.parseDouble(args[6]), Double.parseDouble(args[7]), args[8]));
                    sayStatus(sender, "Dragon arena saved. " + dragons.status());
                }
                case "spawn" -> {
                    if (args.length < 3 || args.length > 5) { usage(sender); return; }
                    var mode = DevelopmentDragonService.SpawnMode.STANDARD;
                    var motion = DragonFlight.Mode.ORBIT;
                    if (args.length == 4 && (args[3].equalsIgnoreCase("orbit") || args[3].equalsIgnoreCase("stationary"))) {
                        motion = motion(args[3]);
                    } else if (args.length >= 4) {
                        mode = switch (args[3].toLowerCase(Locale.ROOT)) {
                            case "standard" -> DevelopmentDragonService.SpawnMode.STANDARD;
                            case "training" -> DevelopmentDragonService.SpawnMode.TRAINING;
                            case "calibration" -> DevelopmentDragonService.SpawnMode.CALIBRATION;
                            default -> throw new IllegalArgumentException("Mode must be standard, training or calibration.");
                        };
                        if (args.length == 5) motion = motion(args[4]);
                    }
                    dragons.spawn(mode, motion); sayStatus(sender, "Dragon spawned. " + dragons.status());
                }
                case "status" -> { if (args.length > 3) { usage(sender); return; } sayStatus(sender, dragons.status()); }
                case "reset" -> {
                    if (args.length < 3 || args.length > 4) { usage(sender); return; }
                    dragons.reset(args.length == 4 ? UUID.fromString(args[3]) : dragons.generation().orElseThrow(() -> new IllegalArgumentException("No dragon generation."))); sayStatus(sender, "Dragon reset. " + dragons.status());
                }
                case "result" -> {
                    if (args.length < 3 || args.length > 4) { usage(sender); return; }
                    var result = dragons.result(args.length == 4 ? UUID.fromString(args[3]) : dragons.generation().orElseThrow(() -> new IllegalArgumentException("No dragon generation."))).completion().orElseThrow();
                    sayStatus(sender, "Frozen dragon result=" + result.completionId() + " | " + dragons.status());
                }
                default -> usage(sender);
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            say(sender, "Dragon request rejected: " + failure.getMessage());
        } catch (IOException failure) { say(sender, "Dragon arena save failed; previous arena retained: " + failure.getMessage()); }
    }
    /**
     * Suggests supported modes and the current generation without creating or changing a fight.
     * @param sender requester used for permission filtering
     * @param args nonempty root arguments including the partial token
     * @return non-null candidate list, possibly empty; candidates are not prefix-filtered here
     */
    public List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("onlydragons.practice") || !args[0].equalsIgnoreCase("dev")) return List.of();
        if (args.length == 2) return List.of("dragon");
        if (!handles(args)) return List.of();
        if (args.length == 3) return List.of("setup", "spawn", "status", "reset", "result");
        if (args.length == 4 && args[2].equalsIgnoreCase("spawn")) return List.of("standard", "training", "calibration", "orbit", "stationary");
        if (args.length == 5 && args[2].equalsIgnoreCase("spawn") && List.of("standard", "training", "calibration").contains(args[3].toLowerCase(Locale.ROOT))) return List.of("orbit", "stationary");
        if (args.length == 4 && (args[2].equalsIgnoreCase("reset") || args[2].equalsIgnoreCase("result")))
            return dragons.generation().map(id -> List.of(id.toString())).orElse(List.of());
        return List.of();
    }
    /**
     * Converts the explicit motion axis independently from the HP/profile selection.
     * @param value orbit or stationary, case-insensitive
     * @return selected motion mode
     * @throws IllegalArgumentException for an unsupported spelling
     */
    private static DragonFlight.Mode motion(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "orbit" -> DragonFlight.Mode.ORBIT;
            case "stationary" -> DragonFlight.Mode.STATIONARY;
            default -> throw new IllegalArgumentException("Motion must be orbit or stationary.");
        };
    }
    /**
     * Shows the explicit persisted-arena and two-axis spawn syntax without mutating configuration.
     * @param sender command recipient
     */
    private void usage(CommandSender sender) { say(sender, "Usage: /onlydragons dev dragon setup <world-key> <x> <y> <z> <radius 16-48> <test_dragon> | spawn [standard|training|calibration] [orbit|stationary] | status | reset [generation] | result [generation]"); }
    /**
     * Sends a human heading and domain-health line before preserving exact diagnostic text.
     * The selection belongs to the same current view; no native HP or formatted number
     * is read back into the encounter state.
     * @param sender feedback recipient
     * @param text already constructed diagnostic status or result text
     */
    private void sayStatus(CommandSender sender, String text) {
        sender.sendMessage(PresentationFormatter.heading("Dragon · " + dragons.view().map(v -> PresentationFormatter.label(v.state().name())).orElse("Idle")));
        dragons.view().ifPresent(view -> sender.sendMessage(PresentationFormatter.healthTitle(
                dragons.selection().orElseThrow().displayName(), view.target().currentHealth(), view.target().maxHealth())));
        say(sender, text);
    }
    /**
     * Sends semantic command text through the shared Adventure formatter.
     * @param sender command recipient
     * @param text plain semantic content; formatting does not change command state
     */
    private static void say(CommandSender sender, String text) { sender.sendMessage(PresentationFormatter.message(text)); }
}
