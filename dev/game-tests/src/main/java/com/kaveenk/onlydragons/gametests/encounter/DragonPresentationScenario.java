package com.kaveenk.onlydragons.gametests.encounter;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.paper.encounter.*;
import com.kaveenk.onlydragons.paper.encounter.presentation.DragonHealthPresenter;
import com.kaveenk.onlydragons.domain.encounter.definition.*;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.domain.MechanicRevision;
import com.kaveenk.onlydragons.domain.item.CalibrationLoadouts;
import com.kaveenk.onlydragons.domain.stats.StatKey;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Actual two-actor packet snapshots, native physical damage, and production UI lifecycle. */
public final class DragonPresentationScenario implements Scenario, Listener {
    private ScenarioContext c; private PlayerFixture players; private DevelopmentDragonService dragons;
    private EnderDragon dragon; private UUID generation; private World arenaWorld, outside;
    private int releases, commands, hits, deaths; private boolean veto;
    private DragonHealthPresenter ghostUi; private DevelopmentDragonService ghostDragons;
    private final List<Map<String,Object>> checks=new ArrayList<>();
    private final List<Map<String,Object>> nativeHits=new ArrayList<>();
    public void start(ScenarioContext context) throws Exception {
        c=context;c.mechanicRevision("dragon-presentation-v1");players=new PlayerFixture(c);dragons=c.production().dragons();c.listen(this);
        c.cleanup("presentation-dragon",()->{
            if(ghostUi!=null)ghostUi.close();if(ghostDragons!=null)ghostDragons.close();
            var main=c.production().dragons();
            if(main.view().isPresent()&&c.production().combat().ownsEntity(main.view().orElseThrow().entityId()))main.reset(main.generation().orElseThrow());
            if(outside!=null){for(var p:List.copyOf(outside.getPlayers()))p.teleport(new Location(arenaWorld,20,100,20));Bukkit.unloadWorld(outside,false);}
        });
        players.await("two presentation actors",300,players::allOnline,this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    private void setup() throws Exception {
        arenaWorld=players.player("alpha").getWorld();
        outside=Bukkit.createWorld(new WorldCreator("ui-outside-"+c.harness().runId()).type(WorldType.FLAT).generateStructures(false));
        for(String actor:List.of("alpha","beta")){
            prepare(actor);players.permission(actor,"onlydragons.practice",true);players.permission(actor,"onlydragons.calibration",true);
            players.setupPosition(actor,new Location(arenaWorld,20,100,20,180,70));
        }
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)c.tickChunk(arenaWorld.getChunkAt(x,z));
        c.check("distinct_connected_viewers",true,!players.identity("alpha").equals(players.identity("beta")));
        command("alpha","kit",()->command("alpha","stats",()->command("alpha","setup",()->command("alpha","spawn",()->{
            capture();awaitUi(2,()->{
                sampleBoth("full",1000,"main");
                c.check("world_viewers_ignore_aim",true,c.production().dragonHealth().viewerCount()==2);
                veto=true;draw("alpha","veto","ordinary",0,()->{
                    veto=false;c.check("veto_no_hp_or_credit",true,view().target().currentHealth()==1000&&view().contributions().isEmpty());sampleBoth("veto",1000,"main");
                    draw("alpha","hit","ordinary",0,()->{
                        c.check("physical_hp_and_credit",true,view().target().currentHealth()==900&&view().contributions().get(players.identity("alpha")).contributionDamage()==100&&dragon.getHealth()==180&&hits>=2);
                        sampleBoth("damaged",900,"main");command("alpha","last",this::reconnect);
                    });
                });
            });
        }))));
    }
    private void reconnect(){
        players.request("beta","reconnect");
        players.await("beta quit before reconnect",100,()->players.quits("beta")==1,()->{
            c.check("quit_viewer_released",true,c.production().dragonHealth().viewerCount()<=1);
            players.await("beta reconnected",150,()->players.joins("beta")==2&&players.allOnline(),()->{
                prepare("beta");players.setupPosition("beta",new Location(arenaWorld,20,100,20));awaitUi(2,()->{
                    sampleBoth("reconnected",900,"main");
                    players.setupPosition("alpha",new Location(outside,0,100,0));
                    awaitUi(1,()->{
                        sample("alpha","outside",0,"");sample("beta","outside",900,"main");
                        players.setupPosition("alpha",new Location(arenaWorld,20,100,20));
                        awaitUi(2,()->{sampleBoth("returned",900,"main");draw("beta","lethal","ordinary",900,this::death);});
                    });
                });
            });
        });
    }
    private void death(){
        c.check("lethal_domain_and_native",true,view().target().currentHealth()==0&&view().completion().isPresent()&&dragon.getHealth()==0&&deaths==1);
        c.later(8,()->{
            c.check("bar_retained_in_native_animation",true,c.production().combat().ownsEntity(dragon.getUniqueId())&&c.production().dragonHealth().viewerCount()==2&&dragon.getDeathAnimationTicks()>0);
            sampleBoth("animation",0,"main");
            players.await("actual native retirement",300,()->!c.production().combat().ownsEntity(dragon.getUniqueId()),()->c.later(2,()->{
                sampleBoth("retired",0,"");c.check("actual_retirement_clears_bar",0,c.production().dragonHealth().viewerCount());
                command("alpha","again",()->{capture();awaitUi(2,()->{
                    sampleBoth("new-generation",1000,"reset");command("alpha","reset",()->c.later(3,()->{
                        sampleBoth("reset",0,"");c.check("reset_clears_bar",true,c.production().dragonHealth().generation().isEmpty());ghost();
                    }));
                });});
            }));
        });
    }
    private void ghost() throws Exception {
        // Explicit fixture-only catalog: same deployed combat/UI adapters, no second damage authority.
        var normal=CombatProfile.calibration();var profile=new CombatProfile(new MechanicRevision("ui-score-only","v1"),normal.mitigation(),normal.cap(),0);
        var loader=new DragonCatalogLoader(CalibrationLoadouts.registry(),List.of(normal,profile),List.of(new PhaseProfile(new MechanicRevision("test-dragon-phase-contract","v1"),Set.of(normal.mechanic(),profile.mechanic()))));
        var registry=new DragonDefinitionRegistry(loader);
        try(var input=DragonCatalogLoader.class.getResourceAsStream("/encounters/test-dragon-v1.properties");var tables=DragonCatalogLoader.class.getResourceAsStream("/encounters/sample-tables-v1.properties")){
            String source=new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("combat.id=combat-calibration","combat.id=ui-score-only");
            registry.replace(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),tables);
        }
        var fixtureConfig=c.production().getDataFolder().toPath().resolve("ui-fixture.yml");
        c.cleanup("ui-fixture-config",()->{try{java.nio.file.Files.deleteIfExists(fixtureConfig);}catch(IOException failure){throw new UncheckedIOException(failure);}});
        java.nio.file.Files.writeString(fixtureConfig,"{}\n");
        ghostDragons=new DevelopmentDragonService(c.production().combat(),registry,new ArenaConfiguration(fixtureConfig,registry),c.production().bows().continuity().tickets());
        ghostDragons.setup(new DevelopmentArena(arenaWorld.getKey().toString(),0,100,0,32,"test_dragon"));
        dragons=ghostDragons;ghostUi=new DragonHealthPresenter(c.production(),dragons,c.production().combat());ghostUi.start();
        generation=dragons.spawn();capture();
        players.await("ghost bar attached",100,()->ghostUi.viewerCount()==2,()->{
            sampleBoth("ghost-full",1000,"ghost");
            draw("alpha","ghost","ferocity_100",0,()->{
                c.check("ghost_credit_without_hp",true,view().acceptedImpacts()==2&&view().target().currentHealth()==900&&view().contributions().get(players.identity("alpha")).contributionDamage()==200&&view().contributions().get(players.identity("alpha")).actualHealthDamage()==100&&view().impacts().getLast().amounts().actualHealthDamage()==0&&view().impacts().getLast().amounts().contributionDamage()==100);
                sampleBoth("ghost-credit",900,"ghost");
                c.later(12,()->{
                    sampleBoth("ghost-stable",900,"ghost");
                    ghostUi.close();c.later(2,()->{
                        sampleBoth("closed",0,"");c.check("ui_close_releases_viewers",true,ghostUi.viewerCount()==0&&ghostUi.generation().isEmpty());
                        ghostDragons.close();finish();
                    });
                });
            });
        });
    }
    private void finish(){
        c.check("no_rewards_or_native_loot",true,deaths==1&&arenaWorld.getEntitiesByClass(ExperienceOrb.class).isEmpty()&&arenaWorld.getEntitiesByClass(Item.class).isEmpty()&&List.of("alpha","beta").stream().allMatch(a->players.player(a).getTotalExperience()==0));
        c.check("native_physical_observations",true,nativeHits.stream().anyMatch(h->h.get("owner").equals(players.identity("alpha").toString()))&&nativeHits.stream().anyMatch(h->h.get("owner").equals(players.identity("beta").toString())));
        c.observe("nativeHits",nativeHits);c.observe("bossBarChecks",checks);
        c.later(8,()->{players.request("alpha","quit");players.request("beta","quit");players.await("both UI actor quits",100,()->players.quits("alpha")==1&&players.quits("beta")==2,c::finish);});
    }
    private void sampleBoth(String marker,double hp,String identity){sample("alpha",marker,hp,identity);sample("beta",marker,hp,identity);}
    private void sample(String actor,String marker,double hp,String identity){
        String title=identity.isEmpty()?"":"Test Dragon (Calibration)  |  "+(hp==1000?"1,000":Integer.toString((int)hp))+" / 1,000 HP  ("+(int)(hp/10)+"%)";
        checks.add(Map.of("actor",actor,"session",actor.equals("beta")&&players.joins("beta")==2?"s2":"s1","marker",marker,"generation",identity,"title",title,"percent",hp/1000));
        players.player(actor).sendMessage(Component.text("OD_UI_CHECK:"+c.harness().runId()+":"+marker));
    }
    private void awaitUi(int count,Runnable next){players.await("UI viewer reconciliation",100,()->c.production().dragonHealth().viewerCount()==count,()->c.later(3,next::run));}
    private void prepare(String actor){var p=players.player(actor);p.setGameMode(GameMode.SURVIVAL);p.setAllowFlight(true);p.setFlying(true);p.setInvulnerable(true);p.getInventory().clear();p.getInventory().setHeldItemSlot(0);p.setTotalExperience(0);p.setLevel(0);p.setExp(0);}
    private void capture(){generation=dragons.generation().orElseThrow();dragon=(EnderDragon)Bukkit.getEntity(view().entityId());}
    private ManagedCombatService.View view(){return dragons.view().orElseThrow();}
    private void command(String actor,String step,ScenarioContext.Step next){int before=commands;players.request(actor,step);players.await("command "+step,80,()->commands>before,()->c.later(3,next));}
    private void draw(String actor,String step,String loadout,double bonus,Runnable next){
        players.await("parts initialized",100,()->dragon.getParts().stream().allMatch(p->p.getLocation().getY()>75),()->{
            String other=actor.equals("alpha")?"beta":"alpha";players.setupPosition(other,new Location(arenaWorld,20,100,20));
            var p=players.player(actor);var aim=dragon.getParts().stream().max(Comparator.comparingDouble(part->part.getBoundingBox().getVolume())).orElseThrow().getBoundingBox().getCenter();var origin=aim.clone().add(new Vector(0,0,-12));
            players.setupPosition(actor,new Location(arenaWorld,origin.getX(),origin.getY()-p.getEyeHeight(),origin.getZ(),0,0));p.setVelocity(new Vector());
            players.setupItem(actor,0,c.production().equipment().createLoadout(loadout));players.setupItem(actor,9,new ItemStack(Material.ARROW,64));c.production().equipment().bonus(p,StatKey.WEAPON_DAMAGE,bonus);
            int before=releases,impacts=hits;players.request(actor,step+"-use");
            players.await("draw "+step,60,p::isHandRaised,()->c.later(22,()->{players.request(actor,step+"-release");players.await("release "+step,60,()->releases>before,()->players.await("native part collision "+step,100,()->hits>impacts,()->c.later(6,next::run)));}));
        });
    }
    @EventHandler(priority=EventPriority.MONITOR)public void command(PlayerCommandPreprocessEvent e){commands++;}
    @EventHandler(priority=EventPriority.MONITOR)public void release(EntityShootBowEvent e){if(e.getEntity() instanceof Player)releases++;}
    @EventHandler(priority=EventPriority.HIGHEST)public void hit(ProjectileHitEvent e){if(e.getHitEntity() instanceof EnderDragonPart part&&part.getParent()==dragon&&e.getEntity() instanceof Arrow a&&a.getShooter() instanceof Player p){hits++;nativeHits.add(Map.of("projectile",a.getUniqueId().toString(),"owner",p.getUniqueId().toString(),"dragon",dragon.getUniqueId().toString(),"veto",veto));if(veto)e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.MONITOR)public void death(EntityDeathEvent e){if(e.getEntity()==dragon&&!e.isCancelled())deaths++;}
}
