package com.kaveenk.onlydragons.gametests.projectile;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.PlayerFixture;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.item.anvil.EnchantBook;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.paper.item.anvil.EnchantBookCodec;
import com.kaveenk.onlydragons.paper.item.codec.ItemReadResult;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import com.kaveenk.onlydragons.paper.projectile.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.util.BoundingBox;

/**
 * Real held-input cadence, stop-boundary and anvil integration fixture.
 * One client use packet starts native raised-hand state; the production bow service
 * then owns its bounded firing loop. Survival inventory, exact emission ticks and
 * native events distinguish admission from requests. The anvil stage explicitly
 * sets up inputs but uses real open, rename, extraction, storage and close packets.
 * The service instance is fixture-owned; its capacity/random ports make saturation
 * and refund oracles deterministic without changing deployed policy.
 */
public final class HeldShortbowScenario implements Scenario, Listener {
    private ScenarioContext c; private PlayerFixture players; private OwnedBowService bows;
    private UUID arena; private World world; private int interactions, stops, commands, slots, deaths;
    private OwnedBowListener listener;
    private boolean openDuringLaunch;
    private boolean veto; private boolean finished;
    private final List<OwnedProjectile> emissions = new ArrayList<>();
    private final List<Map<String,Object>> trials = new ArrayList<>();
    private final ItemRegistry catalog = CalibrationLoadouts.compatibleRegistry();
    private final WeaponItemCodec codec = new WeaponItemCodec(catalog);
    private AnvilView anvilView;
    private ItemStack anvilOriginal, anvilExpected;
    private int anvilOpens, anvilCloses, anvilClicks, anvilShots, anvilAmmo;
    private long anvilCommits, anvilOpenedAt, anvilClosedAt;

    /**
     * Starts the owned production service and listeners after registering cleanup.
     * The stock production service remains present but has no admitted fixture arena.
     * @param context this boot's report and lifecycle owner
     * @throws Exception if initial scenario setup fails
     */
    public void start(ScenarioContext context) throws Exception {
        c = context; c.mechanicRevision("held-shortbows-v1"); players = new PlayerFixture(c);
        // No stock arena is admitted. This is the actual production class, with existing test ports only.
        bows = new OwnedBowService(c.production(), c.production().equipment(), 12, () -> .75);
        c.cleanup("held-service", () -> { finished = true; bows.close(); });
        listener = new OwnedBowListener(bows); c.listen(listener); c.listen(this); bows.start();
        players.await("held actor", 300, players::allOnline, this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY " + c.harness().runId());
    }
    /**
     * Resolves alpha's current connection, including its later replacement session.
     * @return current online fixture player
     */
    private Player player() { return players.player("alpha"); }
    /**
     * Creates a safe Survival pose and verifies denied then permitted real kit
     * commands. Exact seven-bow identities and 512 arrows precede cadence trials.
     */
    private void setup() {
        world = player().getWorld(); player().setGameMode(GameMode.SURVIVAL); player().setAllowFlight(true);
        player().setFlying(true); player().setInvulnerable(true); player().getInventory().clear();
        players.setupPosition("alpha", new Location(world, .5,100,.5,0,-70));
        players.permission("alpha","onlydragons.calibration",false);
        command("denied", () -> {
            c.check("kit_permission_no_mutation", 0, player().getInventory().all(Material.BOW).size());
            players.permission("alpha","onlydragons.calibration",true);
            command("help", () -> command("list", () -> command("kit", () -> {
                c.check("real_kit_exact_bows_arrows", List.of(7,512), List.of(player().getInventory().all(Material.BOW).size(),ammo()));
                c.check("real_kit_all_identities", new TreeSet<>(List.of("drawn_training_v4","swift_shortbow_v4","volley_shortbow_v4","volley_ferocity25_v4","volley_ferocity100_v4","volley_duplex_v4","volley_tempo_v4")).toString(),
                        new TreeSet<>(player().getInventory().all(Material.BOW).values().stream().map(item -> ((com.kaveenk.onlydragons.paper.item.codec.ItemReadResult.Valid)codec.decode(item)).item().instance().identity().definitionId()).toList()).toString());
                cadence("swift", "swift_shortbow_v4", 5, 4, () -> cadence("volley", "volley_shortbow_v4", 2, 10, this::mixed));
            })));
        });
    }
    /**
     * Ends the previous arena, clears native hold/view state and installs a fresh
     * trusted loadout under a new encounter identity. This is labelled server setup.
     * @param id production loadout to place in the selected slot
     * @param ammo ordinary arrows to supply; zero intentionally leaves none
     */
    private void reset(String id, int ammo) {
        if (arena != null) bows.endEncounter(arena);
        player().clearActiveItem(); player().closeInventory(); player().getInventory().clear(); player().getInventory().setHeldItemSlot(0);
        players.setupPosition("alpha",new Location(world,.5,100,.5,0,-70));
        players.setupItem("alpha",0,c.production().equipment().createLoadout(id));
        if (ammo > 0) players.setupItem("alpha",9,new ItemStack(Material.ARROW,ammo));
        arena = UUID.randomUUID(); bows.openEncounter(arena, world, new BoundingBox(-80,50,-80,80,300,80),new MechanicRevision("held-test","v1"));
        emissions.clear();
    }
    /**
     * Measures a fixed 20-tick window from the first actual emission, checking literal
     * count, every inter-primary interval and inventory debit. A post-release window
     * must produce neither repeated shots nor a native duplicate.
     * @param prefix action and assertion namespace
     * @param id trusted tier under test
     * @param interval expected server ticks between primary shots
     * @param count independent expected primaries in the window
     * @param next next trial after the stop observation
     */
    private void cadence(String prefix, String id, int interval, int count, Runnable next) {
        reset(id,64);
        hold(prefix, () -> {
            long first = emissions.getFirst().shot().launchTick();
            laterAt(first+19, () -> {
                c.check(prefix+"_literal_20_tick_primary_count", count, primaries().size());
                c.check(prefix+"_literal_cadence", true, exactCadence(interval));
                c.check(prefix+"_native_hold_still_raised", true, player().isHandRaised());
                c.check(prefix+"_literal_ammo",64-count,ammo());
                release(prefix, () -> {
                    int accepted = emissions.size(), inventory = ammo();
                    c.later(12, () -> {
                        c.check(prefix+"_release_stops_and_no_native_duplicate", List.of(accepted,inventory),List.of(emissions.size(),ammo()));
                        c.check(prefix+"_one_debit_per_primary",64-emissions.size(),ammo());
                        journal(prefix); next.run();
                    });
                });
            });
        });
    }
    /**
     * Adds a real left click during a sustained press and checks both inputs share
     * the same five-tick cooldown and one debit per admitted group.
     */
    private void mixed() {
        reset("swift_shortbow_v4",64);
        hold("mixed", () -> {
            players.request("alpha","mixed-left");
            c.later(14, () -> release("mixed", () -> {
                c.check("mixed_input_shared_five_tick_cooldown",true,primaries().size()>=3&&exactCadence(5));
                c.check("mixed_input_one_ammo_per_group",64-primaries().size(),ammo());
                journal("mixed"); swap();
            }));
        });
    }
    /**
     * Uses actual slot packets to swap to a crossbow and back. The old press must
     * stop and must not resume merely because the bow becomes selected again.
     */
    private void swap() {
        reset("swift_shortbow_v4",64); players.setupItem("alpha",1,new ItemStack(Material.CROSSBOW));
        hold("swap", () -> {
            int old = slots; players.request("alpha","crossbow-slot");
            players.await("actual crossbow slot",80,()->slots>old&&player().getInventory().getHeldItemSlot()==1,()->{
                int count=emissions.size(), arrows=ammo();
                c.later(12,()->{
                    c.check("crossbow_swap_stops_hold",List.of(count,arrows),List.of(emissions.size(),ammo()));
                    players.request("alpha","bow-slot");
                    players.await("actual bow returned",80,()->player().getInventory().getHeldItemSlot()==0,()->c.later(8,()->{
                        c.check("returning_slot_does_not_resume_old_hold",count,emissions.size());
                        release("swap",this::edited);
                    }));
                });
            });
        });
    }
    /**
     * Replaces the selected stack with a same-identity enchant edit as setup while
     * holding. Future emissions stop, and previously captured unenchanted shots keep
     * their original immutable snapshot.
     */
    private void edited() {
        reset("swift_shortbow_v4",64);
        hold("edit",()->{
            var original=((com.kaveenk.onlydragons.paper.item.codec.ItemReadResult.Valid)codec.decode(player().getInventory().getItemInMainHand())).item().instance();
            players.setupItem("alpha",0,codec.encode(catalog.edit(original,Map.of("dragon_tracer",5),List.of())));
            int count=emissions.size(), arrows=ammo();
            c.later(12,()->{
                c.check("same_uuid_edit_stops_hold",List.of(count,arrows),List.of(emissions.size(),ammo()));
                c.check("old_shot_keeps_unenchanted_snapshot",true,emissions.stream().allMatch(p->p.shot().enchantments().isEmpty()));
                release("edit",this::anvilRoundTrip);
            });
        });
    }
    /**
     * Places a reversible real anvil and opens it through a block-use packet while
     * the bow is held. The native view must refer to that block; an observation window
     * checks opening stops both shots and ammo before inputs are staged.
     */
    private void anvilRoundTrip() {
        reset("swift_shortbow_v4",64);
        var support = world.getBlockAt(0,99,3);
        var block = world.getBlockAt(0,100,3);
        var oldSupport = support.getBlockData();
        var oldBlock = block.getBlockData();
        c.cleanup("held-placed-anvil", () -> {
            if (players.allOnline()) player().closeInventory();
            block.setBlockData(oldBlock); support.setBlockData(oldSupport);
        });
        support.setType(Material.STONE); block.setType(Material.ANVIL);
        hold("anvil", () -> {
            players.request("alpha","anvil-open");
            players.await("actual held-to-anvil open",100,
                    () -> anvilOpens == 1 && player().getOpenInventory() instanceof AnvilView,
                    () -> {
                        anvilView = (AnvilView) player().getOpenInventory();
                        anvilShots = emissions.size(); anvilAmmo = ammo();
                        c.check("held_anvil_real_placed_view",true,
                                anvilView.getTopInventory().getLocation() != null
                                && anvilView.getTopInventory().getLocation().getBlock().equals(block));
                        c.later(12, () -> {
                            c.check("held_anvil_open_stops_shots_and_ammo",List.of(anvilShots,anvilAmmo),
                                    List.of(emissions.size(),ammo()));
                            stageAnvilBook();
                        });
                    });
        });
    }
    /**
     * Moves the existing bow and supplies one managed book as explicit server setup.
     * Real rename and result-click packets then drive the native preview/extraction;
     * the preview is free and the Survival cost oracle is eleven levels.
     */
    private void stageAnvilBook() {
        anvilOriginal = player().getInventory().getItemInMainHand().clone();
        // Labelled server setup moves the existing bow; extraction and close use real client packets.
        player().getInventory().setItem(0,null);
        anvilView.getTopInventory().setItem(0,anvilOriginal.clone());
        anvilView.getTopInventory().setItem(1,new EnchantBookCodec(catalog)
                .encode(new EnchantBook("calibration-items-v4","dragon_tracer",5)));
        player().setLevel(30); player().setExp(.375f);
        anvilCommits = c.production().anvils().metrics().committed();
        resyncAnvil(() -> {
            players.request("alpha","anvil-name");
            players.await("new-tier native book preview",100,
                    () -> "Returning Swift".equals(anvilView.getRenameText())
                            && anvilView.getRepairCost() == 11 && !empty(anvilView.getTopInventory().getItem(2)),
                    () -> {
                        c.check("held_anvil_preview_free",true,player().getLevel() == 30
                                && player().getExp() == .375f
                                && anvilOriginal.equals(anvilView.getTopInventory().getItem(0)));
                        anvilExpected = anvilView.getTopInventory().getItem(2).clone();
                        resyncAnvil(() -> {
                            int before = anvilClicks;
                            players.request("alpha","anvil-collect");
                            players.await("actual new-tier result extraction",100,
                                    () -> anvilClicks > before && empty(anvilView.getTopInventory().getItem(0))
                                            && !empty(player().getItemOnCursor()),
                                    () -> c.later(3,this::anvilCollected));
                        });
                    });
        });
    }
    /**
     * Checks the real cursor result, consumed inputs, exact XP and one production
     * commit. Identity, catalog revision and modifiers must survive. Storage and close
     * remain real client actions, each preceded by a full inventory resynchronization.
     */
    private void anvilCollected() {
        var original = ((ItemReadResult.Valid) codec.decode(anvilOriginal)).item().instance();
        var edited = ((ItemReadResult.Valid) codec.decode(player().getItemOnCursor())).item().instance();
        c.check("held_anvil_native_cost_and_conservation",true,
                anvilExpected.equals(player().getItemOnCursor())
                && empty(anvilView.getTopInventory().getItem(0)) && empty(anvilView.getTopInventory().getItem(1))
                && player().getLevel() == 19 && player().getExp() == .375f
                && c.production().anvils().metrics().committed() == anvilCommits + 1);
        c.check("held_anvil_preserves_new_tier_identity",true,original.identity().equals(edited.identity())
                && original.registryRevision().equals(edited.registryRevision())
                && original.rolledModifierIds().equals(edited.rolledModifierIds())
                && edited.enchantLevels().equals(Map.of("dragon_tracer",5)));
        resyncAnvil(() -> {
            players.request("alpha","anvil-store");
            players.await("collected tier stored by real click",100,
                    () -> empty(player().getItemOnCursor())
                            && anvilExpected.equals(player().getInventory().getItemInMainHand()),
                    () -> resyncAnvil(() -> {
                        players.request("alpha","anvil-close");
                        players.await("actual native anvil close",100,
                                () -> anvilCloses == 1 && !(player().getOpenInventory() instanceof AnvilView),
                                () -> c.later(12,this::fireCollectedTier));
                    }));
        });
    }
    /**
     * Requires close to leave the previous press stopped, then observes a new press
     * firing the collected enchanted bow with captured aimed-v3 policy. Earlier arrows
     * must retain their pre-anvil snapshot; historical assertion names do not select
     * the Tracer version.
     */
    private void fireCollectedTier() {
        c.check("held_anvil_close_does_not_resume_old_press",List.of(anvilShots,anvilAmmo),
                List.of(emissions.size(),ammo()));
        players.request("alpha","booked-use");
        players.await("new press fires collected tier",100,
                () -> player().isHandRaised() && emissions.size() > anvilShots,
                () -> {
                    var shot = emissions.get(anvilShots);
                    var edited = ((ItemReadResult.Valid) codec.decode(anvilExpected)).item().instance();
                    c.check("held_anvil_collected_shot_keeps_enchant_and_return_profile",true,
                            shot.shot().weapon().equals(edited.identity())
                            && shot.tracerProfile().revision().equals("tracer-aimed/v3")
                            && shot.shot().enchantments().stream().anyMatch(e -> e.id().equals("dragon_tracer") && e.level() == 5));
                    c.check("held_anvil_prior_arrows_keep_original_snapshot",true,
                            emissions.subList(0,anvilShots).stream().allMatch(p -> p.shot().enchantments().isEmpty()));
                    c.observe("heldAnvil",Map.of("openedAtTick",anvilOpenedAt,"closedAtTick",anvilClosedAt,
                            "shotsAtOpen",anvilShots,"ammoAtOpen",anvilAmmo,"cost",11,
                            "collectedProjectile",shot.shot().projectileId().toString(),
                            "setup","server moves existing bow and supplies one book; protocol owns extraction/store/close"));
                    release("booked",this::callbackStop);
                });
    }
    /**
     * Publishes a full native inventory snapshot and allows receipt delivery before
     * the next state-sensitive anvil action.
     * @param next stage after the five-tick resynchronization allowance
     */
    private void resyncAnvil(Runnable next) { player().updateInventory(); c.later(5,next::run); }
    /**
     * Normalizes nullable and AIR slots for conservation checks.
     * @param item native inventory or cursor value
     * @return whether the slot contains no item
     */
    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir(); }
    /**
     * Opens another inventory synchronously during the first launch callback. The
     * service must preserve that reentrant stop instead of restoring the old hold.
     */
    private void callbackStop() {
        reset("swift_shortbow_v4",64); openDuringLaunch=true;
        hold("callback",()->c.later(12,()->{
            c.check("launch_callback_stop_not_overwritten",List.of(1,63),List.of(emissions.size(),ammo()));
            player().closeInventory();release("callback",this::capacity);
        }));
    }
    /**
     * Fills the fixture's twelve-projectile capacity with six primary/child groups.
     * Exact ammo and parent bindings prove saturation preserves settled group charges
     * and emits one captured-profile Duplex child per admitted primary.
     */
    private void capacity() {
        reset("volley_duplex_v4",64);
        hold("capacity",()->c.later(22,()->{
            c.check("capacity_six_primary_six_children",List.of(6,12,58),List.of(primaries().size(),emissions.size(),ammo()));
            c.check("duplex_one_child_and_captured_profile",true,primaries().stream().allMatch(p->emissions.stream().filter(child->child.shot().parentProjectileId().filter(p.shot().projectileId()::equals).isPresent()).count()==1)
                    &&emissions.stream().allMatch(p->p.tracerProfile().revision().equals("tracer-aimed/v3")));
            c.check("capacity_saturation_preserves_settled_ammo",true,bows.trace().stream().anyMatch(t->t.kind().equals("ammo-settled"))&&bows.capacityUsed()==12);
            release("capacity",()->{journal("capacity");rejects();});
        }));
    }
    /**
     * Exercises empty ammo, the last arrow and a native launch veto. The negative
     * admissions must neither duplicate emissions nor retain capacity, pending groups
     * or a charge for the vetoed launch.
     */
    private void rejects() {
        reset("volley_shortbow_v4",0);
        click("empty",()->{
            c.check("empty_no_admission_or_ammo",List.of(0,0),List.of(emissions.size(),ammo()));
            reset("volley_shortbow_v4",1);
            hold("last",()->c.later(10,()->release("last",()->{
                c.check("last_arrow_no_negative_or_duplicate",List.of(1,0),List.of(emissions.size(),ammo()));
                reset("volley_shortbow_v4",64);veto=true;
                click("veto",()->{
                    veto=false;c.check("launch_veto_refunds_capacity_ammo",List.of(0,0,64),List.of(bows.capacityUsed(),bows.pendingGroups(),ammo()));
                    exit();
                });
            })));
        });
    }
    /**
     * Moves only the actor outside the arena as setup and checks the native hold
     * cannot keep firing after its production session is removed.
     */
    private void exit() {
        reset("swift_shortbow_v4",64);
        hold("exit",()->{
            int count=emissions.size(); players.setupPosition("alpha",new Location(world,90,100,.5));
            c.later(12,()->{
                c.check("arena_exit_clears_hold",true,emissions.size()==count&&bows.currentSession(players.identity("alpha")).isEmpty());
                release("exit",this::targetDeath);
            });
        });
    }
    /**
     * Kills a registered native target through public setup and checks target death
     * stops the press and releases projectile/session ownership.
     */
    private void targetDeath() {
        reset("swift_shortbow_v4",64);
        Cow target=c.own(world.spawn(new Location(world,10,100,10),Cow.class));
        bows.registerTarget(arena,UUID.randomUUID(),target);
        hold("target",()->{
            target.setHealth(0);int count=emissions.size();
            c.later(12,()->{
                c.check("target_death_stops_and_cleans_hold",true,emissions.size()==count&&bows.capacityUsed()==0&&bows.currentSession(players.identity("alpha")).isEmpty());
                release("target",this::resetHeld);
            });
        });
    }
    /**
     * Ends the owning encounter during an active press and checks emissions stop
     * with no retained capacity, delayed groups or chunk reservations.
     */
    private void resetHeld() {
        reset("swift_shortbow_v4",64);
        hold("reset",()->{
            bows.endEncounter(arena);int count=emissions.size();
            c.later(12,()->{
                c.check("reset_stops_and_releases_all",true,emissions.size()==count&&bows.capacityUsed()==0&&bows.pendingGroups()==0&&bows.continuity().tickets().reservedCount()==0);
                release("reset",this::death);
            });
        });
    }
    /**
     * Uses native player death as setup, waits for its real event and checks cleanup,
     * then requests a real respawn packet before the next trial.
     */
    private void death() {
        reset("swift_shortbow_v4",64);
        hold("death",()->{
            int count=emissions.size(); player().setHealth(0);
            players.await("real player death",80,()->deaths==1,()->c.later(10,()->{
                c.check("owner_death_stops_and_cleans",true,emissions.size()==count&&bows.capacityUsed()==0&&bows.currentSession(players.identity("alpha")).isEmpty());
                players.request("alpha","respawn");players.await("real respawn",100,()->!player().isDead(),()->c.later(5,this::quit));
            }));
        });
    }
    /**
     * Requests a reconnect and observes both the actual quit and replacement join.
     * The new connection must not inherit or resume the old held-input state.
     */
    private void quit() {
        reset("swift_shortbow_v4",64);
        hold("quit",()->{
            players.request("alpha","reconnect");
            players.await("actual quit",100,()->players.quits("alpha")==1,()->{
                int count=emissions.size();
                c.check("quit_clears_session",true,bows.currentSession(players.identity("alpha")).isEmpty());
                players.await("replacement session",150,()->players.joins("alpha")==2,()->c.later(10,()->{
                    c.check("reconnect_does_not_resume_hold",count,emissions.size());disable();
                }));
            });
        });
    }
    /**
     * Unregisters and closes only the fixture-owned service during a press. Its loop
     * and resources must stop while the stock production service's task survives;
     * completion follows the actor's actual final quit.
     */
    private void disable() {
        player().setGameMode(GameMode.SURVIVAL);player().setAllowFlight(true);player().setFlying(true);player().setInvulnerable(true);
        reset("swift_shortbow_v4",64);
        hold("disable",()->{
            int count=emissions.size();HandlerList.unregisterAll(listener);bows.close();
            c.later(12,()->{
                c.check("disable_stops_only_owned_loop_and_resources",true,emissions.size()==count&&bows.taskCount()==0&&bows.capacityUsed()==0&&bows.pendingGroups()==0&&bows.continuity().tickets().reservedCount()==0&&c.production().bows().taskCount()==1);
                finished=true;c.observe("trials",trials);c.observe("playerActions",players.journal());
                players.request("alpha","end");players.await("final quit",100,()->players.quits("alpha")==2,c::finish);
            });
        });
    }
    /**
     * Requests one native use and waits for raised-hand state plus an owned emission.
     * @param id declared action prefix
     * @param next stage after the hold has demonstrably started
     */
    private void hold(String id,Runnable next) {
        players.request("alpha",id+"-use");
        players.await("one native use "+id,80,()->player().isHandRaised()&&!emissions.isEmpty(),next::run);
    }
    /**
     * Requests the matching release and waits for native hand-use termination.
     * @param id declared action prefix
     * @param next stage after the stop and settlement allowance
     */
    private void release(String id,Runnable next) {
        players.request("alpha",id+"-release");
        players.await("native stopped "+id,80,()->!player().isHandRaised(),()->c.later(3,next::run));
    }
    /**
     * Waits for a real interaction event after requesting a possibly rejected click.
     * An event receipt proves input arrival, not successful firing.
     * @param id declared client step
     * @param next stage that asserts its actual effect after four ticks
     */
    private void click(String id,Runnable next) {
        int old=interactions;players.request("alpha",id);
        players.await("real input "+id,80,()->interactions>old,()->c.later(4,next::run));
    }
    /**
     * Waits for command preprocessing after a real client command request. Separate
     * assertions and received-message checks establish the command's result.
     * @param id declared command step
     * @param next server-state check after dispatch
     */
    private void command(String id,Runnable next) {
        int old=commands;players.request("alpha",id);
        players.await("real command "+id,80,()->commands>old,()->c.later(2,next::run));
    }
    /**
     * Schedules relative to an absolute unsigned server tick without running inline.
     * @param tick target tick of the cadence window
     * @param next measurement stage
     */
    private void laterAt(long tick,Runnable next) { c.later(Math.max(1,tick-Integer.toUnsignedLong(Bukkit.getCurrentTick())),next::run); }
    /**
     * Selects ordinal-zero emissions so Duplex children cannot inflate cadence.
     * @return primary shots in observed launch order
     */
    private List<OwnedProjectile> primaries() { return emissions.stream().filter(p->p.shot().ordinal()==0).toList(); }
    /**
     * Checks every adjacent primary launch timestamp against the literal interval.
     * Callers also assert a nontrivial count to avoid an empty-list success.
     * @param ticks required interval
     * @return whether all adjacent primary gaps match
     */
    private boolean exactCadence(int ticks) {
        var rows=primaries();for(int i=1;i<rows.size();i++)if(rows.get(i).shot().launchTick()-rows.get(i-1).shot().launchTick()!=ticks)return false;
        return true;
    }
    /**
     * Counts ordinary arrows in actual player inventory.
     * @return remaining Survival ammo across all stacks
     */
    private int ammo() { return player().getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum(); }
    /**
     * Retains emission ticks, UUIDs, ammo and native stop count for raw review.
     * @param id trial whose already-observed state is recorded
     */
    private void journal(String id) { trials.add(Map.of("id",id,"ticks",primaries().stream().map(p->p.shot().launchTick()).toList(),"ammo",ammo(),"projectiles",emissions.stream().map(p->p.shot().projectileId().toString()).toList(),"stops",stops)); }
    /**
     * Observes owned native launches, or deliberately vetoes them in the rejection
     * trial. The callback-stop trial opens a view synchronously here to exercise
     * reentrancy before the firing call returns.
     * @param e real native launch
     */
    @EventHandler(priority=EventPriority.MONITOR) public void launch(ProjectileLaunchEvent e) {
        if(finished)return;
        bows.projectile(e.getEntity().getUniqueId()).ifPresent(owned->{if(veto)e.setCancelled(true);else {
            emissions.add(owned);
            if(openDuringLaunch) { openDuringLaunch=false; player().openInventory(Bukkit.createInventory(null,9)); }
        }});
    }
    /**
     * Counts alpha's actual interactions, including rejected firing inputs.
     * @param e native player interaction
     */
    @EventHandler(priority=EventPriority.MONITOR) public void input(PlayerInteractEvent e) { if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))interactions++; }
    /**
     * Counts the actor's native use-stop callbacks for lifecycle provenance.
     * @param e Paper item-use termination event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void stop(io.papermc.paper.event.player.PlayerStopUsingItemEvent e) { if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))stops++; }
    /**
     * Counts actor command arrival independently of success or permission denial.
     * @param e native command preprocessing event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void command(PlayerCommandPreprocessEvent e) { if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))commands++; }
    /**
     * Counts actual held-slot transitions for the crossbow swap observation.
     * @param e native held-slot event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void slot(PlayerItemHeldEvent e) { if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))slots++; }
    /**
     * Counts the fixture actor's actual death before requesting protocol respawn.
     * @param e native player death event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void death(PlayerDeathEvent e) { if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))deaths++; }
    /**
     * Records an accepted native anvil open for this actor and its server tick.
     * @param e inventory-open event; canceled opens do not satisfy the trial
     */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void anvilOpen(InventoryOpenEvent e) {
        if(e.getPlayer().getUniqueId().equals(players.identity("alpha")) && e.getView() instanceof AnvilView) {
            anvilOpens++; anvilOpenedAt = Integer.toUnsignedLong(Bukkit.getCurrentTick());
        }
    }
    /**
     * Records this actor leaving an actual anvil view, preserving the close tick.
     * @param e native inventory-close event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void anvilClose(InventoryCloseEvent e) {
        if(e.getPlayer().getUniqueId().equals(players.identity("alpha")) && e.getView() instanceof AnvilView) {
            anvilCloses++; anvilClosedAt = Integer.toUnsignedLong(Bukkit.getCurrentTick());
        }
    }
    /**
     * Counts anvil click arrival; cursor, input, XP and commit assertions separately
     * establish that a result was accepted and conserved.
     * @param e native inventory-click event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void anvilClick(InventoryClickEvent e) {
        if(e.getWhoClicked().getUniqueId().equals(players.identity("alpha")) && e.getView() instanceof AnvilView) anvilClicks++;
    }
}
