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

/** Single real use packet and observed native hold. The deployed service has a bounded test capacity/random port. */
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

    public void start(ScenarioContext context) throws Exception {
        c = context; c.mechanicRevision("held-shortbows-v1"); players = new PlayerFixture(c);
        // No stock arena is admitted. This is the actual production class, with existing test ports only.
        bows = new OwnedBowService(c.production(), c.production().equipment(), 12, () -> .75);
        c.cleanup("held-service", () -> { finished = true; bows.close(); });
        listener = new OwnedBowListener(bows); c.listen(listener); c.listen(this); bows.start();
        players.await("held actor", 300, players::allOnline, this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY " + c.harness().runId());
    }
    private Player player() { return players.player("alpha"); }
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
    private void reset(String id, int ammo) {
        if (arena != null) bows.endEncounter(arena);
        player().clearActiveItem(); player().closeInventory(); player().getInventory().clear(); player().getInventory().setHeldItemSlot(0);
        players.setupPosition("alpha",new Location(world,.5,100,.5,0,-70));
        players.setupItem("alpha",0,c.production().equipment().createLoadout(id));
        if (ammo > 0) players.setupItem("alpha",9,new ItemStack(Material.ARROW,ammo));
        arena = UUID.randomUUID(); bows.openEncounter(arena, world, new BoundingBox(-80,50,-80,80,300,80),new MechanicRevision("held-test","v1"));
        emissions.clear();
    }
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
    /** Cross-feature check: actual screen input and native extraction on a newly trusted tier. */
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
    private void resyncAnvil(Runnable next) { player().updateInventory(); c.later(5,next::run); }
    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir(); }
    private void callbackStop() {
        reset("swift_shortbow_v4",64); openDuringLaunch=true;
        hold("callback",()->c.later(12,()->{
            c.check("launch_callback_stop_not_overwritten",List.of(1,63),List.of(emissions.size(),ammo()));
            player().closeInventory();release("callback",this::capacity);
        }));
    }
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
    private void hold(String id,Runnable next) {
        players.request("alpha",id+"-use");
        players.await("one native use "+id,80,()->player().isHandRaised()&&!emissions.isEmpty(),next::run);
    }
    private void release(String id,Runnable next) {
        players.request("alpha",id+"-release");
        players.await("native stopped "+id,80,()->!player().isHandRaised(),()->c.later(3,next::run));
    }
    private void click(String id,Runnable next) {
        int old=interactions;players.request("alpha",id);
        players.await("real input "+id,80,()->interactions>old,()->c.later(4,next::run));
    }
    private void command(String id,Runnable next) {
        int old=commands;players.request("alpha",id);
        players.await("real command "+id,80,()->commands>old,()->c.later(2,next::run));
    }
    private void laterAt(long tick,Runnable next) { c.later(Math.max(1,tick-Integer.toUnsignedLong(Bukkit.getCurrentTick())),next::run); }
    private List<OwnedProjectile> primaries() { return emissions.stream().filter(p->p.shot().ordinal()==0).toList(); }
    private boolean exactCadence(int ticks) {
        var rows=primaries();for(int i=1;i<rows.size();i++)if(rows.get(i).shot().launchTick()-rows.get(i-1).shot().launchTick()!=ticks)return false;
        return true;
    }
    private int ammo() { return player().getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum(); }
    private void journal(String id) { trials.add(Map.of("id",id,"ticks",primaries().stream().map(p->p.shot().launchTick()).toList(),"ammo",ammo(),"projectiles",emissions.stream().map(p->p.shot().projectileId().toString()).toList(),"stops",stops)); }
    @EventHandler(priority=EventPriority.MONITOR) public void launch(ProjectileLaunchEvent e) {
        if(finished)return;
        bows.projectile(e.getEntity().getUniqueId()).ifPresent(owned->{if(veto)e.setCancelled(true);else {
            emissions.add(owned);
            if(openDuringLaunch) { openDuringLaunch=false; player().openInventory(Bukkit.createInventory(null,9)); }
        }});
    }
    @EventHandler(priority=EventPriority.MONITOR) public void input(PlayerInteractEvent e) { if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))interactions++; }
    @EventHandler(priority=EventPriority.MONITOR) public void stop(io.papermc.paper.event.player.PlayerStopUsingItemEvent e) { if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))stops++; }
    @EventHandler(priority=EventPriority.MONITOR) public void command(PlayerCommandPreprocessEvent e) { if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))commands++; }
    @EventHandler(priority=EventPriority.MONITOR) public void slot(PlayerItemHeldEvent e) { if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))slots++; }
    @EventHandler(priority=EventPriority.MONITOR) public void death(PlayerDeathEvent e) { if(e.getPlayer().getUniqueId().equals(players.identity("alpha")))deaths++; }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void anvilOpen(InventoryOpenEvent e) {
        if(e.getPlayer().getUniqueId().equals(players.identity("alpha")) && e.getView() instanceof AnvilView) {
            anvilOpens++; anvilOpenedAt = Integer.toUnsignedLong(Bukkit.getCurrentTick());
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void anvilClose(InventoryCloseEvent e) {
        if(e.getPlayer().getUniqueId().equals(players.identity("alpha")) && e.getView() instanceof AnvilView) {
            anvilCloses++; anvilClosedAt = Integer.toUnsignedLong(Bukkit.getCurrentTick());
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void anvilClick(InventoryClickEvent e) {
        if(e.getWhoClicked().getUniqueId().equals(players.identity("alpha")) && e.getView() instanceof AnvilView) anvilClicks++;
    }
}
