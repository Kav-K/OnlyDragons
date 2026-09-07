package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.paper.encounter.*;
import java.io.IOException;
import java.util.*;
import com.kaveenk.onlydragons.application.PresentationFormatter;
import org.bukkit.command.CommandSender;

/** Production operator commands; no companion or player-position bootstrap. */
public final class DragonCommand {
    private final DevelopmentDragonService dragons;
    public DragonCommand(DevelopmentDragonService dragons) { this.dragons = dragons; }
    public boolean handles(String[] args) { return args.length >= 2 && args[0].equalsIgnoreCase("dev") && args[1].equalsIgnoreCase("dragon"); }
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
    private static DragonFlight.Mode motion(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "orbit" -> DragonFlight.Mode.ORBIT;
            case "stationary" -> DragonFlight.Mode.STATIONARY;
            default -> throw new IllegalArgumentException("Motion must be orbit or stationary.");
        };
    }
    private void usage(CommandSender sender) { say(sender, "Usage: /onlydragons dev dragon setup <world-key> <x> <y> <z> <radius 16-48> <test_dragon> | spawn [standard|training|calibration] [orbit|stationary] | status | reset [generation] | result [generation]"); }
    private void sayStatus(CommandSender sender, String text) {
        sender.sendMessage(PresentationFormatter.heading("Dragon · " + dragons.view().map(v -> PresentationFormatter.label(v.state().name())).orElse("Idle")));
        dragons.view().ifPresent(view -> sender.sendMessage(PresentationFormatter.healthTitle(
                dragons.selection().orElseThrow().displayName(), view.target().currentHealth(), view.target().maxHealth())));
        say(sender, text);
    }
    private static void say(CommandSender sender, String text) { sender.sendMessage(PresentationFormatter.message(text)); }
}
