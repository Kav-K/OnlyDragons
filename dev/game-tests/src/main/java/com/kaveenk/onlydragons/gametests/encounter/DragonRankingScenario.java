package com.kaveenk.onlydragons.gametests.encounter;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.domain.encounter.*;
import com.kaveenk.onlydragons.domain.projectile.SettledHit;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Two actual protocol identities, real bow releases and received production leaderboard text. */
public final class DragonRankingScenario implements Scenario, Listener {
    ScenarioContext c; PlayerFixture players; DamageObservationProbe probe; DevelopmentDragonService dragons;
    EnderDragon dragon; UUID generation; Player originalAlpha; boolean veto, cancelDeath;
    int releases, deaths; final List<SettledHit> settled=new ArrayList<>();
    final List<DevelopmentDragonService.Completion> completions=new ArrayList<>();
    final Map<UUID,UUID> physicalOwners=new LinkedHashMap<>();
    RankedEncounterResult firstRanking;

    public void start(ScenarioContext context)throws Exception {
        c=context;c.mechanicRevision("dragon-ranking-v1");dragons=c.production().dragons();
        players=new PlayerFixture(c);probe=new DamageObservationProbe(c);c.listen(this);
        var observer=c.production().combat().observeSettled(settled::add);
        c.cleanup("ranking-dragon",()->{
            if(dragons.generation().isPresent()&&c.production().combat().ownsEntity(dragons.view().orElseThrow().entityId()))dragons.reset(dragons.generation().orElseThrow());
            observer.close();
        });
        players.await("two distinct actors",300,players::allOnline,this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    void setup()throws Exception {
        for(String actor:List.of("alpha","beta"))prepare(actor);
        originalAlpha=players.player("alpha");
        var world=originalAlpha.getWorld();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)c.tickChunk(world.getChunkAt(x,z));
        c.check("two_distinct_real_players",true,!players.identity("alpha").equals(players.identity("beta"))&&Bukkit.getOnlinePlayers().size()==2);
        dragons.setup(new DevelopmentArena(world.getKey().toString(),0,100,0,32,"test_dragon"));
        spawn(()->{
            veto=true;draw("beta","veto","ordinary",0,()->{
                veto=false;c.check("rejected_participant_excluded",true,view().acceptedImpacts()==0&&view().contributions().isEmpty());
                draw("alpha","first","ordinary",500,()->{
                    c.check("first_owned_damage_exact",true,view().target().currentHealth()==400&&view().contributions().get(players.identity("alpha")).contributionDamage()==600&&dragon.getHealth()==80);
                    players.request("alpha","reconnect");
                    players.await("same UUID reconnected",150,()->players.joins("alpha")==2&&players.allOnline(),()->{
                        prepare("alpha");c.check("reconnect_preserves_uuid_participant",true,players.player("alpha")!=originalAlpha&&players.player("alpha").getUniqueId().equals(originalAlpha.getUniqueId())&&view().contributions().size()==1);
                        draw("beta","lethal","ordinary",500,this::firstComplete);
                    });
                });
            });
        });
    }
    void firstComplete(){
        players.await("ordinary frozen board",80,()->dragons.ranking().isPresent(),()->{
            firstRanking=dragons.ranking().orElseThrow();var result=view().completion().orElseThrow();
            c.check("same_frozen_result_and_selection",true,firstRanking.result()==result&&result.selection().orElseThrow().equals(dragons.selection().orElseThrow()));
            var rows=firstRanking.placements();
            c.check("tied_credit_unique_acceptance_order",true,rows.size()==2&&rows.get(0).playerId().equals(players.identity("alpha"))&&rows.get(0).place()==1&&rows.get(1).playerId().equals(players.identity("beta"))&&rows.get(1).place()==2&&rows.stream().allMatch(r->r.contribution().contributionDamage()==600));
            c.check("lethal_overkill_hp_separate",true,rows.get(0).contribution().actualHealthDamage()==600&&rows.get(1).contribution().actualHealthDamage()==400&&result.completedOrdinal()==2&&view().target().currentHealth()==0);
            c.check("real_native_owned_arrow_death",true,deaths==1&&dragon.getHealth()==0&&completions.size()==1&&physicalOwners.values().containsAll(List.of(players.identity("alpha"),players.identity("beta")))&&probe.events().stream().anyMatch(e->e.cancelled()&&e.initialDamage()>0&&e.settledDamage()==0&&e.finalDamage()==0&&e.target().equals(dragon.getUniqueId())&&settled.stream().anyMatch(hit->hit.rejection().isEmpty()&&hit.projectile().shot().projectileId().equals(e.directDamager())&&hit.projectile().shot().ownerId().equals(e.player()))));
            c.check("completion_releases_subscription_and_retains_projection",true,dragons.ranking().orElseThrow()==firstRanking&&dragons.subscriberCount()==0);
            players.setupItem("alpha",0,c.production().equipment().createLoadout("crit"));
            draw("beta","late","ordinary",900,()->{
                c.check("post_death_release_and_equipment_cannot_reorder",true,dragons.ranking().orElseThrow()==firstRanking&&view().completion().orElseThrow()==result&&view().acceptedImpacts()==2);
                c.later(8,()->{dragons.reset(generation);secondFight();});
            },false);
        });
    }
    void secondFight(){
        spawn(()->{
            c.check("next_fight_starts_without_old_board",true,dragons.ranking().isEmpty());
            draw("beta","second","ordinary",0,()->draw("alpha","proc","ferocity_100",500,()->
                players.await("proc lethal board",100,()->dragons.ranking().isPresent(),()->{
                    var ranked=dragons.ranking().orElseThrow();var a=ranked.placement(players.identity("alpha")).orElseThrow();var b=ranked.placement(players.identity("beta")).orElseThrow();
                    c.check("proc_only_lethal_delivers_new_generation",true,completions.size()==2&&ranked.result()==view().completion().orElseThrow()&&a.place()==1&&a.contribution().contributionDamage()==1200&&a.contribution().actualHealthDamage()==900&&b.place()==2&&b.contribution().contributionDamage()==100&&b.contribution().actualHealthDamage()==100&&ranked.result().completedOrdinal()==3&&deaths==2);
                    c.check("prior_frozen_board_unchanged",true,firstRanking.placements().getFirst().contribution().contributionDamage()==600&&firstRanking.placements().getLast().contribution().actualHealthDamage()==400);
                    c.later(8,()->{dragons.reset(generation);diagnosticFight();});
                })));
        });
    }
    void diagnosticFight(){
        spawn(()->{
            cancelDeath=true;draw("alpha","cancelled","ordinary",900,()->{
                cancelDeath=false;
                c.check("cancelled_native_death_has_no_board",true,view().completion().isPresent()&&dragon.getHealth()>0&&dragons.ranking().isEmpty()&&completions.size()==2&&dragons.subscriberCount()==0);
                dragon.setHealth(0); // Administrative follow-up is diagnostic, never an ordinary defeat.
                c.later(3,()->{
                    c.check("admin_followup_cannot_announce",true,dragons.ranking().isEmpty()&&completions.size()==2);
                    dragons.reset(generation);spawn(()->{
                        dragons.reset(generation);
                        c.check("abort_reset_has_no_board",true,view().completion().isEmpty()&&dragons.ranking().isEmpty()&&completions.size()==2);
                        c.check("ranking_resources_released",true,dragons.subscriberCount()==0&&c.production().combat().activeCount()==0&&c.production().bows().continuity().tickets().reservedCount()==0&&dragons.leaderboardDeliveryFailures()==0);
                        for(String actor:List.of("alpha","beta"))c.check("no_rewards_"+actor,true,players.player(actor).getTotalExperience()==0&&players.player(actor).getInventory().all(Material.DIAMOND).isEmpty());
                        c.observe("rankingResults",completions.stream().map(event->Map.of("completion",event.result().completionId().toString(),"generation",event.generation().toString(),"participants",event.result().participants().toString())).toList());
                        c.observe("physicalOwners",physicalOwners.toString());c.observe("playerActions",players.journal());
                        c.later(10,()->{players.request("alpha","quit");players.request("beta","quit");players.await("both actual quits",100,()->players.quits("alpha")==2&&players.quits("beta")==1,c::finish);});
                    });
                });
            });
        });
    }
    void spawn(Runnable next){
        generation=dragons.spawn();dragon=(EnderDragon)Bukkit.getEntity(view().entityId());probe.watch(dragon);dragons.subscribe(generation,completions::add);
        players.await("native parts initialized",100,()->dragon.getParts().stream().allMatch(part->part.getLocation().getY()>75),next::run);
    }
    void prepare(String actor){
        var p=players.player(actor);p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);p.getInventory().clear();p.getInventory().setHeldItemSlot(0);p.setTotalExperience(0);p.setLevel(0);p.setExp(0);
    }
    void draw(String actor,String step,String loadout,double bonus,Runnable next){draw(actor,step,loadout,bonus,next,true);}
    void draw(String actor,String step,String loadout,double bonus,Runnable next,boolean expectSettled){
        String other=actor.equals("alpha")?"beta":"alpha";
        players.setupPosition(other,new Location(players.player(other).getWorld(),20,100,20));
        var p=players.player(actor);var aim=dragon.getParts().stream().max(Comparator.comparingDouble(part->part.getBoundingBox().getVolume())).orElseThrow().getBoundingBox().getCenter();var origin=aim.clone().add(new Vector(0,0,-12));
        players.setupPosition(actor,new Location(p.getWorld(),origin.getX(),origin.getY()-p.getEyeHeight(),origin.getZ(),0,0));p.setVelocity(new Vector());
        players.setupItem(actor,0,c.production().equipment().createLoadout(loadout));players.setupItem(actor,9,new ItemStack(Material.ARROW,64));c.production().equipment().bonus(p,StatKey.WEAPON_DAMAGE,bonus);
        int before=releases,impacts=settled.size();players.request(actor,step+"-use");
        players.await("draw "+actor+step,60,p::isHandRaised,()->c.later(22,()->{
            players.request(actor,step+"-release");players.await("release "+actor+step,60,()->releases>before,()->{
                if(expectSettled)players.await("production settled impact "+step,100,()->settled.size()>impacts,()->c.later(2,next::run));
                else c.later(12,next::run);
            });
        }));
    }
    ManagedCombatService.View view(){return c.production().combat().view(generation).orElseThrow();}
    @EventHandler(priority=EventPriority.MONITOR)public void release(EntityShootBowEvent e){if(e.getEntity()instanceof Player)releases++;}
    @EventHandler(priority=EventPriority.MONITOR)public void launch(ProjectileLaunchEvent e){
        // Labeled native-positive pressure: the existing production guard must suppress it.
        if(e.getEntity() instanceof Arrow arrow)c.later(1,()->{if(arrow.isValid())arrow.setDamage(2);});
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void hit(ProjectileHitEvent e){if(e.getHitEntity()instanceof EnderDragonPart part&&part.getParent()==dragon&&e.getEntity()instanceof Arrow a&&a.getShooter()instanceof Player p){physicalOwners.put(a.getUniqueId(),p.getUniqueId());if(veto)e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.HIGHEST)public void cancel(EntityDeathEvent e){if(cancelDeath&&e.getEntity()==dragon)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR)public void death(EntityDeathEvent e){if(e.getEntity()==dragon&&!e.isCancelled())deaths++;}
}
