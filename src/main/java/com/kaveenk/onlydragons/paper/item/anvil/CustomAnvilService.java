package com.kaveenk.onlydragons.paper.item.anvil;

import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Server-thread preview/session guard. Native extraction alone debits inputs and XP. */
public final class CustomAnvilService implements AutoCloseable {
    private static final class Session {
        final AnvilView view;
        long revision;
        Snapshot preview;
        boolean collecting;
        Session(AnvilView view) { this.view = view; }
    }
    private record Snapshot(ItemStack left, ItemStack right, String name, AnvilRecipeService.Preview recipe) {
        boolean inputs(AnvilView view) {
            return exact(left, view.getTopInventory().getItem(0)) && exact(right, view.getTopInventory().getItem(1))
                    && Objects.equals(name, view.getRenameText());
        }
    }
    public record Metrics(int sessions, int callbacks, long committed, long rejected) {}
    private final Plugin plugin;
    private final AnvilRecipeService recipes;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<InventoryClickEvent, Snapshot> entering = new java.util.IdentityHashMap<>();
    private final Set<BukkitTask> callbacks = new HashSet<>();
    private boolean closed;
    private long committed, rejected;

    public CustomAnvilService(Plugin plugin, ItemRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin); recipes = new AnvilRecipeService(registry);
    }

    public void prepare(PrepareAnvilEvent event) {
        requireThread();
        if (closed || !(event.getView().getPlayer() instanceof Player player)) return;
        var view = event.getView();
        var recipe = recipes.preview(view.getTopInventory().getItem(0), view.getTopInventory().getItem(1), view.getRenameText());
        var existing = sessions.get(player.getUniqueId());
        if (!recipe.managed()) {
            if (existing != null && !existing.collecting) sessions.remove(player.getUniqueId());
            return;
        }
        var session = existing != null && existing.view == view ? existing : new Session(view);
        sessions.put(player.getUniqueId(), session);
        if (session.collecting) { event.setResult(null); return; }
        long revision = ++session.revision;
        var snapshot = new Snapshot(copy(view.getTopInventory().getItem(0)), copy(view.getTopInventory().getItem(1)), view.getRenameText(), recipe);
        session.preview = snapshot;
        event.setResult(copy(recipe.output()));
        costs(view, recipe);
        // Some pinned native early-return paths reset cost after PrepareAnvilEvent.
        later(() -> {
            if (!current(player, session) || session.revision != revision || session.collecting || !snapshot.inputs(view)
                    || !exact(event.getResult(), recipe.output())) return;
            view.getTopInventory().setItem(2, copy(recipe.output()));
            costs(view, recipe);
            player.updateInventory();
        });
    }

    public void collect(InventoryClickEvent event) {
        requireThread();
        Snapshot entered = entering.remove(event);
        if (event.getView() instanceof AnvilView && event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR
                && sessions.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true); rejected++; return;
        }
        if (closed || event.getRawSlot() != 2 || !(event.getView() instanceof AnvilView view)
                || !(event.getWhoClicked() instanceof Player player)) return;
        var session = sessions.get(player.getUniqueId());
        var live = recipes.preview(view.getTopInventory().getItem(0), view.getTopInventory().getItem(1), view.getRenameText());
        if (!live.managed() && session == null && entered == null) return;
        boolean shift = event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT;
        boolean supported = shift || event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT;
        var snapshot = session == null ? null : session.preview;
        if (event.isCancelled() || session == null || !current(player, session) || session.collecting || snapshot == null
                || entered != snapshot || !snapshot.inputs(view) || live.output() == null || !exact(snapshot.recipe().output(), live.output())
                || !exact(live.output(), view.getTopInventory().getItem(2)) || snapshot.recipe().cost() != live.cost()
                || view.getRepairCost() != live.cost() || view.getRepairItemCountCost() != live.rightCount()
                || view.getMaximumRepairCost() <= live.cost() || !supported || !AnvilRecipeService.empty(player.getItemOnCursor())
                || player.getInventory().firstEmpty() < 0
                || player.getGameMode() != GameMode.CREATIVE && player.getLevel() < live.cost()) {
            event.setCancelled(true); rejected++; return;
        }
        int level = player.getLevel();
        float fraction = player.getExp();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        int heldBefore = count(player, live.output());
        session.collecting = true;
        session.revision++;
        // This callback observes native completion; it never retries or performs a second debit.
        later(() -> {
            if (!current(player, session)) return;
            ItemStack rightAfter = copy(snapshot.right());
            if (!AnvilRecipeService.empty(rightAfter)) rightAfter.setAmount(rightAfter.getAmount() - live.rightCount());
            boolean received = shift ? count(player, live.output()) == heldBefore + live.output().getAmount()
                    : exact(live.output(), player.getItemOnCursor());
            if (!event.isCancelled() && AnvilRecipeService.empty(view.getTopInventory().getItem(0))
                    && exact(rightAfter, view.getTopInventory().getItem(1)) && received
                    && player.getLevel() == level - (creative ? 0 : live.cost()) && Float.compare(player.getExp(), fraction) == 0) committed++;
            if (snapshot.inputs(view) && exact(snapshot.recipe().output(), view.getTopInventory().getItem(2))
                    && player.getLevel() == level && Float.compare(player.getExp(), fraction) == 0) {
                // A later listener/native destination veto is not a consumed transaction.
                // Retain the unchanged offer so a legitimate retry needs no input shuffle.
                session.collecting = false;
                session.preview = snapshot;
            } else sessions.remove(player.getUniqueId(), session);
        });
    }

    public void leave(UUID player) { requireThread(); sessions.remove(player); }
    public void entering(InventoryClickEvent event) {
        requireThread();
        if (event.getRawSlot() == 2 && event.getView() instanceof AnvilView) {
            var session = sessions.get(event.getWhoClicked().getUniqueId());
            if (session != null && session.preview != null) entering.put(event, session.preview);
        }
    }
    public Metrics metrics() { requireThread(); return new Metrics(sessions.size(), callbacks.size(), committed, rejected); }

    private boolean current(Player player, Session session) {
        return !closed && player.isOnline() && sessions.get(player.getUniqueId()) == session && player.getOpenInventory() == session.view;
    }
    private void later(Runnable action) {
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = Bukkit.getScheduler().runTask(plugin, () -> { callbacks.remove(handle[0]); if (!closed) action.run(); });
        callbacks.add(handle[0]);
    }
    private static void costs(AnvilView view, AnvilRecipeService.Preview recipe) {
        view.setRepairCost(recipe.cost()); view.setRepairItemCountCost(recipe.rightCount());
    }
    private static ItemStack copy(ItemStack stack) { return AnvilRecipeService.empty(stack) ? null : stack.clone(); }
    private static int count(Player player, ItemStack expected) {
        int count = 0;
        for (var item : player.getInventory().getStorageContents())
            if (item != null && item.isSimilar(expected)) count += item.getAmount();
        return count;
    }
    private static boolean exact(ItemStack one, ItemStack two) {
        if (AnvilRecipeService.empty(one) || AnvilRecipeService.empty(two)) return AnvilRecipeService.empty(one) && AnvilRecipeService.empty(two);
        return Arrays.equals(one.serializeAsBytes(), two.serializeAsBytes());
    }
    private static void requireThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Anvils belong to the server thread"); }
    @Override public void close() {
        requireThread(); closed = true; callbacks.forEach(BukkitTask::cancel); callbacks.clear();
        for (var session : sessions.values()) if (session.preview != null) session.view.getTopInventory().setItem(2, null);
        sessions.clear(); entering.clear();
    }
}
