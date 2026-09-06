package com.kaveenk.onlydragons.gametests.fixtures;

import com.kaveenk.onlydragons.gametests.Scenario;
import com.kaveenk.onlydragons.gametests.ScenarioContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitTask;

/** Intended abort with two connected players and live, reversibly owned resources. */
public final class PlayerCleanupAbortScenario implements Scenario, Listener {
    private static final String PERMISSION = "onlydragons.fixture.cleanup";
    private final Map<UUID, Integer> commands = new HashMap<>();
    private ScenarioContext context;
    private PlayerFixture players;
    private int lastCommandTick;

    @Override public void start(ScenarioContext context) throws Exception {
        this.context = context;
        context.mechanicRevision("headless-cleanup-v1");
        context.check("server_thread", true, Bukkit.isPrimaryThread());
        context.check("production_enabled", true, context.production().isEnabled());
        context.observe("scope", "Deliberate abort after real command packets from two offline players. "
                + "Permission, block and entity changes are server setup; runner cleanup must disconnect both incomplete plans.");
        players = new PlayerFixture(context);
        context.listen(this);
        players.await("two cleanup actors online", 300, players::allOnline, this::setup);
        context.harness().getLogger().info("OD_PLAYER_READY " + context.harness().runId());
    }

    private void setup() {
        List<Player> actors = List.of(players.player("alpha"), players.player("beta"));
        context.check("two_distinct_real_players", true,
                !actors.getFirst().getUniqueId().equals(actors.getLast().getUniqueId())
                        && Bukkit.getOnlinePlayers().size() == 2);
        context.check("loopback_nonop_players", true, actors.stream().allMatch(player -> !player.isOp()
                && player.getAddress() != null && player.getAddress().getAddress().isLoopbackAddress()));
        context.check("cleanup_permissions_initially_absent", true,
                actors.stream().noneMatch(player -> player.hasPermission(PERMISSION)));
        players.permission("alpha", PERMISSION, true);
        players.permission("beta", PERMISSION, true);
        context.check("cleanup_permissions_created", true,
                actors.stream().allMatch(player -> player.hasPermission(PERMISSION)));

        var world = actors.getFirst().getWorld();
        Block first = world.getBlockAt(4, 100, 4);
        Block second = world.getBlockAt(5, 100, 4);
        var firstBefore = first.getBlockData().clone();
        var secondBefore = second.getBlockData().clone();
        context.cleanup("abort-blocks", () -> {
            first.setBlockData(firstBefore, false);
            second.setBlockData(secondBefore, false);
            context.check("abort_blocks_restored", true, first.getBlockData().equals(firstBefore)
                    && second.getBlockData().equals(secondBefore));
        });
        first.setType(first.getType() == Material.STONE ? Material.DIRT : Material.STONE, false);
        second.setType(second.getType() == Material.STONE ? Material.DIRT : Material.STONE, false);
        context.tickChunk(first.getChunk());
        context.check("cleanup_blocks_changed", true, !first.getBlockData().equals(firstBefore)
                && !second.getBlockData().equals(secondBefore));
        Cow target = context.own(world.spawn(first.getLocation().add(0.5, 2, 0.5), Cow.class));
        target.setAI(false);
        target.setGravity(false);
        context.check("cleanup_entity_created", true, target.isValid());

        Set<Integer> beforeTasks = ownedTaskIds();
        context.later(1000, () -> { throw new AssertionError("Abort cleanup failed to cancel the delayed callback"); });
        Set<Integer> delayedTasks = new HashSet<>(ownedTaskIds());
        delayedTasks.removeAll(beforeTasks);
        context.check("cleanup_delayed_task_created", 1, delayedTasks.size());
        context.cleanup("abort-resource-verification", () -> {
            context.check("abort_permissions_removed", true,
                    actors.stream().noneMatch(player -> player.hasPermission(PERMISSION)));
            context.check("abort_delayed_task_cancelled", true,
                    delayedTasks.stream().noneMatch(Bukkit.getScheduler()::isQueued));
        });
        context.observe("cleanupSetup", Map.of("entity", target.getUniqueId().toString(),
                "firstBlock", first.getLocation().toVector().toString(),
                "secondBlock", second.getLocation().toVector().toString(),
                "firstOriginal", firstBefore.getAsString(), "secondOriginal", secondBefore.getAsString(),
                "delayedTaskIds", delayedTasks.stream().sorted().toList()));

        players.request("alpha", "status");
        players.request("beta", "status");
        players.await("both real uncancelled status commands", 100,
                () -> commands.containsKey(players.identity("alpha")) && commands.containsKey(players.identity("beta")),
                () -> context.later(2, () -> {
                    context.check("alpha_real_uncancelled_command", 1, commands.get(players.identity("alpha")));
                    context.check("beta_real_uncancelled_command", 1, commands.get(players.identity("beta")));
                    context.check("command_dispatch_settled_before_abort", true, Bukkit.getCurrentTick() - lastCommandTick >= 2);
                    context.check("both_actors_online_at_abort", true, players.allOnline()
                            && actors.stream().allMatch(Player::isOnline)
                            && players.quits("alpha") == 0 && players.quits("beta") == 0);
                    context.check("resources_live_at_abort", true, target.isValid()
                            && !first.getBlockData().equals(firstBefore) && !second.getBlockData().equals(secondBefore)
                            && actors.stream().allMatch(player -> player.hasPermission(PERMISSION))
                            && delayedTasks.stream().allMatch(Bukkit.getScheduler()::isQueued));
                    context.abort();
                }));
    }

    private Set<Integer> ownedTaskIds() {
        return Bukkit.getScheduler().getPendingTasks().stream()
                .filter(task -> task.getOwner() == context.harness()).map(BukkitTask::getTaskId).collect(Collectors.toSet());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        if (!event.getMessage().equals("/onlydragons status")
                || !Set.of(players.identity("alpha"), players.identity("beta")).contains(player)) return;
        commands.merge(player, 1, Integer::sum);
        lastCommandTick = Bukkit.getCurrentTick();
    }
}
