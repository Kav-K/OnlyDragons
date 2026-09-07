package com.kaveenk.onlydragons.gametests.projectile;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.PlayerFixture;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.projectile.*;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import com.kaveenk.onlydragons.paper.projectile.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
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
                release("edit",this::callbackStop);
            });
        });
    }
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
                    &&emissions.stream().allMatch(p->p.tracerProfile().revision().equals("tracer-return/v2")));
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
}
