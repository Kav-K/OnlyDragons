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

/**
 * Server-thread native-anvil offer/session guard with no second debit authority.
 * Prepare creates a clone and repairs pinned-Paper cost resets only after checking
 * the final event result. LOWEST click identity and HIGHEST exact-value checks bind
 * extraction to that offer. A next-tick observer counts native XP/input/output
 * completion; it never retries collection or manufactures a refund. Close/quit/view
 * replacement invalidate callbacks, and service close cancels all owned tasks.
 * @see CustomAnvilListener
 * @see AnvilRecipeService
 */
public final class CustomAnvilService implements AutoCloseable {
    /**
     * One exact native view identity; revision invalidates deferred preview repair.
     * Collecting prevents reentrant preparation/extraction while native completion settles.
     */
    private static final class Session {
        final AnvilView view;
        long revision;
        Snapshot preview;
        boolean collecting;
        /**
         * Captures a native view by object identity.
         * @param view exact player anvil view, not merely an equal inventory
         */
        Session(AnvilView view) { this.view = view; }
    }
    /**
     * Private frozen offer inputs; stacks are clones made by prepare and never mutated here.
     * @param left copied left item, null when empty
     * @param right copied right item, null when empty
     * @param name exact native rename text, possibly null
     * @param recipe isolated candidate and exact level/input costs
     */
    private record Snapshot(ItemStack left, ItemStack right, String name, AnvilRecipeService.Preview recipe) {
        /**
         * Revalidates serialized input bytes and rename text against the live view.
         * @param view original native anvil view
         * @return true only when neither input nor rename changed
         */
        boolean inputs(AnvilView view) {
            return exact(left, view.getTopInventory().getItem(0)) && exact(right, view.getTopInventory().getItem(1))
                    && Objects.equals(name, view.getRenameText());
        }
    }
    /**
     * Immutable diagnostics, not an alternative transaction ledger.
     * @param sessions current guarded player views
     * @param callbacks currently scheduled owned next-tick tasks
     * @param committed cumulative observed exact native completions
     * @param rejected cumulative guarded click rejections
     */
    public record Metrics(int sessions, int callbacks, long committed, long rejected) {}
    private final Plugin plugin;
    private final AnvilRecipeService recipes;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<InventoryClickEvent, Snapshot> entering = new java.util.IdentityHashMap<>();
    private final Set<BukkitTask> callbacks = new HashSet<>();
    private boolean closed;
    private long committed, rejected;

    /**
     * Creates an inactive guard; listener registration remains caller-owned.
     * @param plugin non-null scheduler owner
     * @param registry exact trusted weapon/book catalog router
     * @throws NullPointerException if plugin is null
     */
    public CustomAnvilService(Plugin plugin, ItemRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin); recipes = new AnvilRecipeService(registry);
    }

    /**
     * Captures inputs/name/output/cost for a managed preview; ordinary recipes pass through.
     * Defers one guarded cost repair because native early-return paths may reset cost
     * after this event. A later plugin's changed result prevents that repair.
     * @param event actual prepare event; its result is replaced only for managed inputs
     * @throws IllegalStateException if called off the server thread
     */
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

    /**
     * Validates a native result click and schedules observation, never manual extraction.
     * Requires the same LOWEST offer, current view/session, serialized inputs/output,
     * rename, costs, supported left/right/shift click, empty cursor, storage capacity
     * and sufficient levels outside creative mode. Double-click collection is rejected
     * for managed sessions. A later veto with unchanged inputs/XP retains the offer for
     * retry; any changed native transaction retires it after observation.
     * @param event same synchronous click previously captured by entering
     * @throws IllegalStateException if called off the server thread
     */
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

    /**
     * Invalidates a view by UUID; queued tasks self-discard through current checks.
     * @param player closed/disconnected owner
     * @throws IllegalStateException if called off the server thread
     */
    public void leave(UUID player) { requireThread(); sessions.remove(player); }
    /**
     * Captures the result-slot snapshot at LOWEST using event object identity.
     * This preserves managed-to-ordinary replacement detection later in the same event.
     * @param event actual click whose later collect callback removes the capture
     * @throws IllegalStateException if called off the server thread
     */
    public void entering(InventoryClickEvent event) {
        requireThread();
        if (event.getRawSlot() == 2 && event.getView() instanceof AnvilView) {
            var session = sessions.get(event.getWhoClicked().getUniqueId());
            if (session != null && session.preview != null) entering.put(event, session.preview);
        }
    }
    /**
     * Snapshots current resources and lifetime observed completion/rejection counts.
     * @return immutable diagnostics
     * @throws IllegalStateException if called off the server thread
     */
    public Metrics metrics() { requireThread(); return new Metrics(sessions.size(), callbacks.size(), committed, rejected); }

    /**
     * Rejects stale callbacks using service, player, session and native-view identity.
     * @param player captured online owner
     * @param session exact session object captured by the callback
     * @return true only while all ownership boundaries still match
     */
    private boolean current(Player player, Session session) {
        return !closed && player.isOnline() && sessions.get(player.getUniqueId()) == session && player.getOpenInventory() == session.view;
    }
    /**
     * Owns one synchronous next-tick callback and removes its handle before dispatch.
     * @param action guarded preview repair or completion observer; never a second debit
     */
    private void later(Runnable action) {
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = Bukkit.getScheduler().runTask(plugin, () -> { callbacks.remove(handle[0]); if (!closed) action.run(); });
        callbacks.add(handle[0]);
    }
    /**
     * Updates only native level and right-item count costs, never maximum cost policy.
     * @param view current native view
     * @param recipe validated candidate or explicit negative-cost rejection
     */
    private static void costs(AnvilView view, AnvilRecipeService.Preview recipe) {
        view.setRepairCost(recipe.cost()); view.setRepairItemCountCost(recipe.rightCount());
    }
    /**
     * Detaches an offer input/result from mutable native inventory state.
     * @param stack nullable source
     * @return independent clone, or null for any empty representation
     */
    private static ItemStack copy(ItemStack stack) { return AnvilRecipeService.empty(stack) ? null : stack.clone(); }
    /**
     * Counts equivalent output amounts across storage for native shift extraction.
     * An output may merge into an existing stack; a new slot is not required as proof.
     * @param player destination owner
     * @param expected complete result metadata to match
     * @return total matching storage amount, excluding cursor/armor/offhand
     */
    private static int count(Player player, ItemStack expected) {
        int count = 0;
        for (var item : player.getInventory().getStorageContents())
            if (item != null && item.isSimilar(expected)) count += item.getAmount();
        return count;
    }
    /**
     * Compares full native serialized values, normalizing empty slots.
     * @param one first nullable stack
     * @param two second nullable stack
     * @return true only for equal bytes or two empty representations
     */
    private static boolean exact(ItemStack one, ItemStack two) {
        if (AnvilRecipeService.empty(one) || AnvilRecipeService.empty(two)) return AnvilRecipeService.empty(one) && AnvilRecipeService.empty(two);
        return Arrays.equals(one.serializeAsBytes(), two.serializeAsBytes());
    }
    /**
     * Rejects anvil/view/task state access outside the classic Paper server thread.
     */
    private static void requireThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Anvils belong to the server thread"); }
    /**
     * Permanently closes admission, cancels owned callbacks and removes guarded previews.
     * Inputs and XP stay under native ownership; clearing the preview does not consume
     * or refund either input. Invoke on the server thread during plugin shutdown.
     * @throws IllegalStateException if called off the server thread
     */
    @Override public void close() {
        requireThread(); closed = true; callbacks.forEach(BukkitTask::cancel); callbacks.clear();
        for (var session : sessions.values()) if (session.preview != null) session.view.getTopInventory().setItem(2, null);
        sessions.clear(); entering.clear();
    }
}
