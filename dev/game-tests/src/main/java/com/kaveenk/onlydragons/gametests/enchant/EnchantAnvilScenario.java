package com.kaveenk.onlydragons.gametests.enchant;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.*;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.item.anvil.EnchantBook;
import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.paper.item.codec.*;
import com.kaveenk.onlydragons.paper.item.anvil.*;
import com.kaveenk.onlydragons.paper.encounter.DummyBackend;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/**
 * Placed-anvil protocol transactions followed by a real shot from the collected managed bow.
 * All ten book grants, rename requests and output extractions are real input. Only
 * the first trial moves both inputs by packets; later inputs are labeled server setup.
 * Literal level costs/fraction preservation and copied metadata prove conservation;
 * the final 165-damage shot connects the output to production effects.
 */
public final class EnchantAnvilScenario implements Scenario, Listener {
    private static final List<String> IDS = List.of("snipe","dragon_tracer","vicious","overload","gravity","infinite_quiver","flame","duplex","fatal_tempo","power");
    private static final NamespacedKey FOREIGN = new NamespacedKey("fixture","foreign");
    private final ItemRegistry registry = CalibrationLoadouts.compatibleRegistry();
    private final WeaponItemCodec weapons = new WeaponItemCodec(registry);
    private final EnchantBookCodec books = new EnchantBookCodec(registry);
    private ScenarioContext c; private PlayerFixture players; private AnvilView view;
    private int index, opens, clicks, releases; private ItemStack left, right, collected; private int beforeLevel, cost;
    private long commits; private DummyBackend target; private UUID generation; private DamageObservationProbe probe;
    private final List<Map<String,Object>> transactions = new ArrayList<>();
    private final Set<UUID> collisions = new HashSet<>();
    /**
     * Owns the view/backend cleanup and native observers before admitting the actor.
     * @param context server-thread report/resource owner
     * @throws Exception if actor-plan admission fails
     */
    public void start(ScenarioContext context) throws Exception {
        c = context; c.mechanicRevision("enchant-anvil-v1"); players = new PlayerFixture(c); c.listen(this); probe = new DamageObservationProbe(c);
        c.cleanup("anvil-view", () -> { if (players.allOnline()) players.player("alpha").closeInventory(); });
        c.cleanup("anvil-combat", () -> { if (target != null) { c.production().combat().reset(players.identity("alpha")); target.close(); } });
        players.await("anvil actor",300,players::allOnline,this::setup);
        c.harness().getLogger().info("OD_PLAYER_READY " + c.harness().runId());
    }
    /**
     * Resolves the current actor connection for each stage rather than retaining a stale player.
     */
    private Player p() { return players.player("alpha"); }
    /**
     * Builds a reversible placed anvil and requires denied grant followed by actual native open.
     */
    private void setup() {
        var p=p(); p.setGameMode(GameMode.SURVIVAL); p.setAllowFlight(true); p.setFlying(true); p.setInvulnerable(true);
        p.getInventory().clear(); p.setItemOnCursor(null); p.getInventory().setHeldItemSlot(0);
        var world=p.getWorld(); c.tickChunk(world.getChunkAt(0,0)); c.tickChunk(world.getChunkAt(0,-1));
        var base=world.getBlockAt(0,99,0); var anvil=world.getBlockAt(0,100,0); var oldBase=base.getBlockData(); var oldAnvil=anvil.getBlockData();
        c.cleanup("placed-anvil",()->{base.setBlockData(oldBase);anvil.setBlockData(oldAnvil);}); base.setType(Material.STONE); anvil.setType(Material.ANVIL);
        players.setupPosition("alpha",new Location(world,.5,100,-2,0,0));
        players.permission("alpha","onlydragons.calibration",false); players.request("alpha","denied-book");
        c.later(8,()->{
            c.check("book_permission_denial_conserves_inventory",true,Arrays.stream(p().getInventory().getStorageContents()).allMatch(EnchantAnvilScenario::empty));
            players.permission("alpha","onlydragons.calibration",true); players.request("alpha","open");
            players.await("actual placed anvil open",100,()->p().getOpenInventory() instanceof AnvilView && opens==1,()->{
                view=(AnvilView)p().getOpenInventory();
                c.check("placed_anvil_open_event_and_location",true,view.getTopInventory().getLocation()!=null && view.getTopInventory().getLocation().getBlock().getType()==Material.ANVIL);
                trial();
            });
        });
    }
    /**
     * Stages one enchant trial with preserved foreign/durability metadata and literal XP oracle.
     */
    private void trial() {
        if(index==IDS.size()) { fire(); return; }
        String id=IDS.get(index); int level=registry.enchant(id).maxLevel();
        p().getInventory().clear(); view.getTopInventory().clear(); p().setItemOnCursor(null);
        p().getWorld().getBlockAt(0,100,0).setType(Material.ANVIL);
        var instance=registry.create(index%2==0?"shortbow_v4":"ordinary_v4");
        instance=registry.edit(instance,Map.of(),instance.rolledModifierIds()); left=weapons.encode(instance);
        left.editMeta(meta->{meta.displayName(Component.text("Kept bow"));((Damageable)meta).setDamage(37);((Repairable)meta).setRepairCost(9);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,3,false);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.POWER,1,false);
            meta.getPersistentDataContainer().set(FOREIGN,PersistentDataType.STRING,"foreign components survive");});
        players.setupItem("alpha",0,left.clone()); players.request("alpha","grant-"+id.replace('_','-'));
        players.await("real command grants "+id,100,()->books.decode(p().getInventory().getItem(1)) instanceof EnchantBookCodec.Read.Valid,()->{
            right=p().getInventory().getItem(1).clone();
            c.check(id+"_command_book_valid",new EnchantBook("calibration-items-v4",id,level).toString(),((EnchantBookCodec.Read.Valid)books.decode(right)).book().toString());
            beforeLevel=40; p().setLevel(beforeLevel); p().setExp(.375f); cost=level*(id.equals("duplex")||id.equals("fatal_tempo")?4:2)+(index==0?1:0);
            commits=c.production().anvils().metrics().committed();
            if(index==0) {
                inputBow();
            } else {
                // Labelled server setup isolates each transaction; extraction is always real protocol input.
                p().getInventory().clear();view.getTopInventory().setItem(0,left.clone());view.getTopInventory().setItem(1,right.clone());rename();
            }
        });
    }
    /**
     * Requests the first native pickup of the bow after authoritative inventory resynchronization.
     */
    private void inputBow() {
        resync(() -> requestClick("input-bow", () -> !empty(p().getItemOnCursor()), this::placeBow));
    }
    /**
     * Requests placement into the actual anvil left slot and waits for native state.
     */
    private void placeBow() {
        resync(() -> requestClick("place-bow", () -> !empty(view.getTopInventory().getItem(0)), this::inputBook));
    }
    /**
     * Requests native pickup of the command-granted book.
     */
    private void inputBook() {
        resync(() -> requestClick("input-book", () -> !empty(p().getItemOnCursor()), this::placeBook));
    }
    /**
     * Requests native placement into the right input before rename/preview checks.
     */
    private void placeBook() {
        resync(() -> requestClick("place-book", () -> !empty(view.getTopInventory().getItem(1)), this::rename));
    }
    /**
     * Waits for Paper's actual rename/cost/result while proving preview consumes neither inputs nor XP.
     */
    private void rename() {
        String id=IDS.get(index);
        resync(()->{players.request("alpha","name-"+id.replace('_','-')); players.await("received rename reaches Paper",100,
                ()->Objects.equals(view.getRenameText(),index==0?"Upgraded bow":"Kept bow") && view.getRepairCost()==cost && !empty(view.getTopInventory().getItem(2)),()->{
            c.check(id+"_preview_free",true,p().getLevel()==beforeLevel && p().getExp()==.375f && left.equals(view.getTopInventory().getItem(0)) && right.equals(view.getTopInventory().getItem(1)));
            c.check(id+"_preview_cost",cost,view.getRepairCost());
            collected=view.getTopInventory().getItem(2).clone();
            resync(()->requestClick("collect-"+id.replace('_','-'),()->empty(view.getTopInventory().getItem(0)),this::collected));
        });});
    }
    /**
     * Checks native cursor/shift destination, one commit, exact level debit and immutable item identity.
     */
    private void collected() {
        String id=IDS.get(index); boolean shift=index%3==2;
        players.await("native completed transaction",60,()->c.production().anvils().metrics().committed()==commits+1,()->{
            var received=shift?Arrays.stream(p().getInventory().getStorageContents()).filter(i->i!=null&&i.isSimilar(collected)).findFirst().orElseThrow():p().getItemOnCursor();
            c.check(id+"_native_conservation",true,received.equals(collected)&&empty(view.getTopInventory().getItem(0))&&empty(view.getTopInventory().getItem(1))
                    &&p().getLevel()==beforeLevel-cost&&p().getExp()==.375f);
            var decoded=((ItemReadResult.Valid)weapons.decode(received)).item().instance(); var original=((ItemReadResult.Valid)weapons.decode(left)).item().instance();var meta=received.getItemMeta();
            c.check(id+"_metadata_survives",true,decoded.identity().equals(original.identity())&&decoded.registryRevision().equals(original.registryRevision())
                    &&decoded.rolledModifierIds().equals(original.rolledModifierIds())&&decoded.enchantLevels().equals(Map.of(id,registry.enchant(id).maxLevel()))
                    &&((Damageable)meta).getDamage()==37&&((Repairable)meta).getRepairCost()==9&&meta.getEnchantLevel(org.bukkit.enchantments.Enchantment.POWER)==1
                    &&meta.getEnchantLevel(org.bukkit.enchantments.Enchantment.UNBREAKING)==3&&"foreign components survive".equals(meta.getPersistentDataContainer().get(FOREIGN,PersistentDataType.STRING)));
            transactions.add(Map.of("step","collect-"+id.replace('_','-'),"cost",cost,"beforeLevel",beforeLevel,"afterLevel",p().getLevel(),"fraction",.375,"shift",shift));
            if(shift){index++;trial();}else resync(()->requestClick("store-"+id.replace('_','-'),()->empty(p().getItemOnCursor()),()->{index++;trial();}));
        });
    }
    /**
     * Closes the actual anvil, stages the collected bow and requires a physical production hit with literal 165 HP/credit.
     */
    private void fire() {
        resync(()->{players.request("alpha","close");players.await("real anvil close",100,()->!(p().getOpenInventory() instanceof AnvilView),()->{
            c.check("all_ten_native_commits",10L,c.production().anvils().metrics().committed());
            players.setupItem("alpha",0,collected.clone());players.setupItem("alpha",9,new ItemStack(Material.ARROW,16));p().getInventory().setHeldItemSlot(0);
            var location=new Location(p().getWorld(),10,100,10);target=new DummyBackend(location);probe.watch(target.entity());
            generation=c.production().combat().open(players.identity("alpha"),target,BoundingBox.of(location,24,24,24),1000,0,"anvil",CombatProfile.calibration(),Optional.empty(),()->.99);
            var center=target.entity().getBoundingBox().getCenter();players.setupPosition("alpha",new Location(p().getWorld(),center.getX(),center.getY()-p().getEyeHeight(),center.getZ()-10,0,0));
            players.permission("alpha","onlydragons.fire",true);
            c.later(8,()->{players.request("alpha","use");players.await("native bow raised",100,()->p().isHandRaised(),()->c.later(22,()->{
                players.request("alpha","release");players.await("collected bow production hit",140,()->c.production().combat().view(generation).orElseThrow().acceptedImpacts()==1,()->{
                    var result=c.production().combat().view(generation).orElseThrow();
                    c.check("collected_bow_immediate_effect",true,result.target().currentHealth()==835&&result.impacts().getFirst().amounts().contributionDamage()==165
                            &&result.impacts().getFirst().amounts().actualHealthDamage()==165&&releases==1&&!collisions.isEmpty());
                    c.observe("anvilTransactions",transactions);c.observe("playerActions",players.journal());
                    players.request("alpha","quit");players.await("quit cleanup",100,()->players.quits("alpha")==1,()->{c.check("anvil_sessions_clean",0,c.production().anvils().metrics().sessions());c.finish();});
                });
            }));});
        });});
    }
    /**
     * Requests the server's full inventory state and yields five ticks before another transaction.
     */
    private void resync(Runnable next) { p().updateInventory();c.later(5,next::run); }
    /**
     * Requires a new native click plus the operation's actual state predicate before proceeding.
     */
    private void requestClick(String step,java.util.function.BooleanSupplier done,Runnable next) {int before=clicks;players.request("alpha",step);players.await(step,100,()->clicks>before&&done.getAsBoolean(),()->c.later(2,next::run));}
    /**
     * Normalizes absent and AIR inventory representations for conservation checks.
     */
    private static boolean empty(ItemStack item){return item==null||item.getType().isAir();}
    /**
     * Counts uncancelled native ANVIL openings for the declared actor.
     * @param event native inventory-open event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void open(InventoryOpenEvent event){if(event.getPlayer().getUniqueId().equals(players.identity("alpha"))&&event.getView() instanceof AnvilView&&!event.isCancelled())opens++;}
    /**
     * Counts the actor's actual native inventory dispatches, separate from transaction success.
     * @param event native click event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void click(InventoryClickEvent event){if(event.getWhoClicked().getUniqueId().equals(players.identity("alpha")))clicks++;}
    /**
     * Counts actual bow-release dispatches for the collected-item firing trial.
     * @param event native bow event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void release(EntityShootBowEvent event){if(event.getEntity().getUniqueId().equals(players.identity("alpha")))releases++;}
    /**
     * Records physical arrow UUIDs from the actor when a real entity contact occurs.
     * @param event native hit event
     */
    @EventHandler(priority=EventPriority.MONITOR) public void hit(ProjectileHitEvent event){if(event.getEntity().getShooter() instanceof Player player&&player.getUniqueId().equals(players.identity("alpha"))&&event.getHitEntity()!=null)collisions.add(event.getEntity().getUniqueId());}
}
