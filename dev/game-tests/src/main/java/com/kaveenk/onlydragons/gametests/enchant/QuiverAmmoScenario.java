package com.kaveenk.onlydragons.gametests.enchant;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.PlayerFixture;
import com.kaveenk.onlydragons.paper.projectile.*;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.paper.item.codec.WeaponItemCodec;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * Real Survival inputs into the production bow-service class with its injected random port.
 * A fixture-owned instance supplies deterministic samples and admits the arena;
 * the stock service has no arena here. Inventory deltas, random draws and group
 * reservations are checked together, including native Infinity's no-debit path.
 * This is ammo/admission evidence, not a collision or damage implementation.
 */
public final class QuiverAmmoScenario implements Scenario, Listener {
    ScenarioContext c; PlayerFixture players; OwnedBowService bows; UUID generation;
    double sample; int randomDraws,releases,inputs; boolean vetoBow,vetoLaunch,retain;
    final List<Map<String,Object>> trials=new ArrayList<>();
    final ItemRegistry catalog=CalibrationLoadouts.compatibleRegistry();
    /**
     * Owns a separate production service/listener pair before requesting the two real actors.
     * @param context server-thread report/resource owner
     * @throws Exception if actor-plan admission fails
     */
    public void start(ScenarioContext context) throws Exception {
        c=context;c.mechanicRevision("quiver-ammo-v1");players=new PlayerFixture(c);
        bows=new OwnedBowService(c.production(),c.production().equipment(),100,()->{randomDraws++;return sample;});
        c.cleanup("quiver-service",bows::close);c.listen(new OwnedBowListener(bows));c.listen(this);bows.start();
        players.await("quiver actors",300,players::allOnline,this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    /**
     * Prepares independent Survival inventories and runs exact Quiver probability-boundary and refund trials.
     */
    void setup() {
        var world=players.player("alpha").getWorld();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.tickChunk(world.getChunkAt(x,z));
        for(String actor:List.of("alpha","beta")) {
            var p=players.player(actor);p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);
            p.getInventory().clear();p.getInventory().setHeldItemSlot(0);
            players.setupPosition(actor,new Location(world,actor.equals("alpha")?0:8,100,0,0,-70));
            players.permission(actor,"onlydragons.fire",true);
        }
        players.setupItem("beta",9,new ItemStack(Material.ARROW,31));players.setupItem("beta",10,new ItemStack(Material.DIAMOND,7));
        generation=UUID.randomUUID();bows.openEncounter(generation,world,new BoundingBox(-80,50,-80,80,300,80),new MechanicRevision("quiver-ammo","v1"));
        kit(false,10,20);sample=Math.nextDown(.5);
        draw("save",()->{check("draw_saved_duplex",20,2,2);resetShots();
            kit(false,10,20);sample=.5;draw("charge",()->{check("draw_threshold_debit",19,2,2);resetShots();
                kit(false,1,20);sample=Math.nextDown(.05);draw("level-one",()->{check("draw_level_one_boundary",20,2,2);resetShots();
                    kit(false,10,20);sample=0;vetoBow=true;draw("veto",()->{vetoBow=false;check("draw_veto_exact_refund",20,0,2);resetShots();
                        kit(false,10,20);sample=0;retain=true;draw("retained",()->{retain=false;check("retained_primary_save_once",20,1,2);resetShots();nativeInfinity();});
                    });
                });
            });
        });
    }
    /**
     * Uses actual vanilla Infinity to prove a non-debited native group cannot receive a duplicate refund.
     */
    void nativeInfinity() {
        kit(false,10,20);sample=0;
        players.player("alpha").getInventory().getItemInMainHand().addEnchantment(org.bukkit.enchantments.Enchantment.INFINITY,1);
        draw("native-infinity",()->{check("native_non_debited_group_no_refund",20,2,2);resetShots();
            kit(false,10,20);sample=0;vetoBow=true;
            players.player("alpha").getInventory().getItemInMainHand().addEnchantment(org.bukkit.enchantments.Enchantment.INFINITY,1);
            draw("native-infinity-veto",()->{vetoBow=false;check("native_non_debited_veto_no_refund",20,0,2);resetShots();shorts();});
        });
    }
    /**
     * Checks saved/charged shortbow groups, last/absent ammo, launch veto and permission denial.
     */
    void shorts() {
        kit(true,10,20);sample=Math.nextDown(.5);
        click("short-save",()->{check("short_saved_duplex",20,2,2);resetShots();
            kit(true,10,20);sample=.5;click("short-charge",()->{check("short_threshold_debit",19,2,2);resetShots();
                kit(true,10,1);sample=0;click("last-arrow",()->{check("last_real_arrow_saved",1,2,2);resetShots();
                    kit(true,10,0);sample=0;click("empty",()->{check("empty_even_when_save_no_admission",0,0,0);resetShots();
                        kit(true,10,20);vetoLaunch=true;click("launch-veto",()->{vetoLaunch=false;check("short_launch_veto_exact_refund",20,0,2);resetShots();
                            kit(true,10,20);var permission=players.permission("alpha","onlydragons.fire",false);
                            click("denied",()->{players.removePermission(permission);check("permission_denial_no_roll_or_debit",20,0,0);resetShots();quit();});
                        });
                    });
                });
            });
        });
    }
    /**
     * Checks real quit/session cleanup and that the unrelated actor's inventory was not altered.
     */
    void quit() {
        kit(true,10,20);sample=0;click("quit-shot",()->{
            check("quit_shot_saved",20,2,2);players.request("alpha","quit");
            players.await("actual quiver quit",100,()->players.quits("alpha")==1,()->{
                c.check("quit_session_cleared",true,bows.currentSession(players.identity("alpha")).isEmpty());
                c.check("unrelated_survival_inventory_unchanged",true,players.player("beta").getInventory().getItem(9).getAmount()==31
                        &&players.player("beta").getInventory().getItem(10).getAmount()==7);
                bows.endEncounter(generation);bows.close();
                c.check("quiver_reset_disable_cleanup",true,bows.capacityUsed()==0&&bows.pendingGroups()==0&&bows.pendingClaims()==0&&bows.taskCount()==0
                        &&bows.continuity().tickets().reservedCount()==0);
                c.observe("trials",trials);c.observe("playerActions",players.journal());
                players.request("beta","quit");players.await("beta quit",100,()->players.quits("beta")==1,c::finish);
            });
        });
    }
    /**
     * Stages one validated Quiver/Duplex bow and exact real-arrow count, resetting only the random-draw counter.
     */
    void kit(boolean shortbow,int level,int ammo) {
        var item=catalog.edit(catalog.create(shortbow?"shortbow_v4":"ordinary_v4"),Map.of("infinite_quiver",level,"duplex",5),List.of());
        players.setupItem("alpha",0,new WeaponItemCodec(catalog).encode(item));
        players.player("alpha").getInventory().remove(Material.ARROW);
        players.setupItem("alpha",9,ammo==0?new ItemStack(Material.AIR):new ItemStack(Material.ARROW,ammo));
        players.player("alpha").updateInventory();randomDraws=0;
    }
    /**
     * Ends the previous generation before opening a fresh one so each ammo trial starts without old groups.
     */
    void resetShots() {
        bows.endEncounter(generation);generation=UUID.randomUUID();
        bows.openEncounter(generation,players.player("alpha").getWorld(),new BoundingBox(-80,50,-80,80,300,80),new MechanicRevision("quiver-ammo","v1"));
    }
    /**
     * Compares literal ammo/projectile/random-draw counts after reservation settlement and records the trace.
     */
    void check(String id,int ammo,int arrows,int draws) {
        int actual=players.player("alpha").getInventory().all(Material.ARROW).values().stream().mapToInt(ItemStack::getAmount).sum();
        c.check(id,true,actual==ammo&&bows.projectiles().size()==arrows&&randomDraws==draws&&bows.pendingGroups()==0&&bows.reservedCapacity()==0);
        trials.add(Map.of("id",id,"ammo",actual,"arrows",bows.projectiles().size(),"draws",randomDraws,"sample",sample,
                "trace",bows.trace().stream().filter(t->t.kind().equals("ammo-settled")).map(Object::toString).toList()));
    }
    /**
     * Requests actual use/release after observed hand raise, then gives native debit/group settlement time to finish.
     */
    void draw(String step,Runnable next) {
        int before=releases;var p=players.player("alpha");players.request("alpha",step+"-use");
        players.await("native hand raised "+step,80,p::isHandRaised,()->c.later(22,()->{
            players.request("alpha",step+"-release");players.await("native release "+step,80,()->releases>before,()->c.later(3,next::run));
        }));
    }
    /**
     * Waits for the real legacy shortbow interaction and its delayed group settlement before checking ammo.
     */
    void click(String step,Runnable next) {
        int before=inputs;players.request("alpha",step);players.await("real shortbow input "+step,80,()->inputs>before,()->c.later(4,next::run));
    }
    /**
     * Counts the real release and injects only the deliberate final bow-veto trial.
     * @param event native bow event for alpha
     */
    @EventHandler(priority=EventPriority.MONITOR) public void release(EntityShootBowEvent event) {
        if(event.getEntity() instanceof Player p&&p.getUniqueId().equals(players.identity("alpha"))) {
            releases++;if(vetoBow)event.setCancelled(true);

        }
    }
    /**
     * Clears the captured session during native use statistics to exercise retained-primary settlement.
     * @param event native statistic callback, used only by the explicit retained trial
     */
    @EventHandler(priority=EventPriority.MONITOR) public void usedBow(PlayerStatisticIncrementEvent event) {
        if(retain&&event.getStatistic()==Statistic.USE_ITEM&&event.getMaterial()==Material.BOW
                &&event.getPlayer().getUniqueId().equals(players.identity("alpha")))
            bows.currentSession(event.getPlayer().getUniqueId()).ifPresent(token->bows.clearSession(event.getPlayer().getUniqueId(),token,false));
    }
    /**
     * Counts alpha's actual left-click inputs for the legacy instant-bow primitive.
     * @param event native interaction event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void input(PlayerInteractEvent event) {
        if(event.getPlayer().getUniqueId().equals(players.identity("alpha"))&&event.getAction().isLeftClick())inputs++;
    }
    /**
     * Vetoes only the selected actor's actual arrow launch during the launch-failure control.
     * @param event native launch event
     */
    @EventHandler(priority=EventPriority.HIGHEST) public void launch(ProjectileLaunchEvent event) {
        if(vetoLaunch&&event.getEntity() instanceof Arrow arrow&&arrow.getShooter() instanceof Player p&&p.getUniqueId().equals(players.identity("alpha")))event.setCancelled(true);
    }
}
