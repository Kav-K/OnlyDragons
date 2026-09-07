package com.kaveenk.onlydragons.gametests.enchant;

import com.kaveenk.onlydragons.gametests.*;
import com.kaveenk.onlydragons.gametests.fixtures.PlayerFixture;
import com.kaveenk.onlydragons.domain.item.*;
import com.kaveenk.onlydragons.domain.item.anvil.EnchantBook;
import com.kaveenk.onlydragons.paper.item.codec.*;
import com.kaveenk.onlydragons.paper.item.anvil.EnchantBookCodec;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;

/**
 * Real-anvil boundary/lifecycle trials with independent input, cursor and XP conservation.
 * The two fixed plans separate catalog compatibility from cancellation, stale offers,
 * capacity, retry, reconnect and service shutdown. Creative explicitly exempts native
 * XP debit; vanilla repair must remain outside custom commit metrics. Inputs are
 * server setup, while rename/extraction/open/close/reconnect use actual packets.
 */
public final class AnvilBoundaryScenario implements Scenario, Listener {
    private final boolean lifecycle;
    private final List<String> CASES;
    /**
     * Selects catalog, book-combination and malformed-input compatibility trials.
     */
    public AnvilBoundaryScenario() { this(false); }
    /**
     * Selects one fixed plan with matching catalog assertion identities.
     * @param lifecycle use the retry/stale/close/reconnect/disable plan instead of compatibility
     */
    public AnvilBoundaryScenario(boolean lifecycle) {
        this.lifecycle=lifecycle;
        CASES=lifecycle ? List.of("insufficient","full","drop","hotbar","cancel","retry","replay","stale","stale-ordinary","prepare-veto","rename-only","creative","vanilla")
                : List.of("combine","stack-right","legacy-common","legacy-v2-denied","legacy-v3-denied","ultimate-conflict","max-level","lower-noop","wrong-book","spoof-book","stack-left");
    }
    private final ItemRegistry registry=CalibrationLoadouts.compatibleRegistry();
    private final WeaponItemCodec weapons=new WeaponItemCodec(registry);private final EnchantBookCodec books=new EnchantBookCodec(registry);
    private ScenarioContext c;private PlayerFixture players;private AnvilView view;private int index,clicks,opens;
    private ItemStack left,right,expected;private int xp,cost;private long commits;private boolean cancel,stale,prepareVeto;
    private ItemStack savedLeft,savedRight;private final List<Map<String,Object>> rows=new ArrayList<>();
    /**
     * Registers the owned view cleanup and begins the real actor's native anvil flow.
     * @param context server-thread report/resource owner
     * @throws Exception if actor-plan admission fails
     */
    public void start(ScenarioContext context)throws Exception{
        c=context;c.mechanicRevision(lifecycle?"anvil-lifecycle-v1":"anvil-boundaries-v1");players=new PlayerFixture(c);c.listen(this);
        c.cleanup("boundary-view",()->{if(players.allOnline())players.player("alpha").closeInventory();});
        players.await("boundary actor",300,players::allOnline,this::setup);c.harness().getLogger().info("OD_PLAYER_READY "+c.harness().runId());
    }
    /**
     * Resolves the current actor connection, including after reconnect.
     */
    private Player p(){return players.player("alpha");}
    /**
     * Creates a validated current-catalog book as explicit trial setup.
     */
    private ItemStack book(String id,int level){return books.encode(new EnchantBook("calibration-items-v4",id,level));}
    /**
     * Creates a validated bow in its own historical/current registry identity.
     */
    private ItemStack bow(String definition,Map<String,Integer> enchants){var i=registry.create(definition);return weapons.encode(registry.edit(i,enchants,i.rolledModifierIds()));}
    /**
     * Builds a reversible placed anvil and waits for the actor's native open before trials.
     */
    private void setup(){
        p().setGameMode(GameMode.SURVIVAL);p().setAllowFlight(true);p().setFlying(true);p().setInvulnerable(true);p().getInventory().clear();p().setItemOnCursor(null);
        var world=p().getWorld();var base=world.getBlockAt(0,99,0);var anvil=world.getBlockAt(0,100,0);var oldBase=base.getBlockData();var oldAnvil=anvil.getBlockData();
        c.cleanup("boundary-anvil",()->{base.setBlockData(oldBase);anvil.setBlockData(oldAnvil);});base.setType(Material.STONE);anvil.setType(Material.ANVIL);
        players.setupPosition("alpha",new Location(world,.5,100,-2,0,0));players.request("alpha","open");
        players.await("boundary native anvil",100,()->p().getOpenInventory() instanceof AnvilView,()->{view=(AnvilView)p().getOpenInventory();trial();});
    }
    /**
     * Selects a literal trial setup/cost and uses real rename input before testing output collection.
     * Retry reuses the cancelled offer; replay deliberately attempts an already consumed
     * output so conservation cannot be satisfied by a second grant.
     */
    private void trial(){
        if(index==CASES.size()){if(lifecycle)closeRecovery();else finishCompatibility();return;}
        String id=CASES.get(index);cancel=false;stale=false;prepareVeto=false;
        if(id.equals("replay")) {
            var held=p().getItemOnCursor().clone();int level=p().getLevel();long committed=c.production().anvils().metrics().committed();
            sync(()->{int before=clicks;players.request("alpha","collect-replay");players.await("repeat consumed output",100,()->clicks>before,()->c.later(3,()->{
                c.check("replay_consumed_output_conserves_items_xp",true,empty(view.getTopInventory().getItem(0))&&empty(view.getTopInventory().getItem(1))&&held.equals(p().getItemOnCursor())&&p().getLevel()==level&&p().getExp()==.375f&&c.production().anvils().metrics().committed()==committed);
                rows.add(Map.of("step","collect-replay","success",false,"cost",0,"beforeLevel",level,"afterLevel",p().getLevel(),"fraction",.375));index++;sync(this::trial);
            }));});return;
        }
        if(id.equals("retry")){expected=view.getTopInventory().getItem(2).clone();collect(id,true);return;}
        p().getWorld().getBlockAt(0,100,0).setType(Material.ANVIL);p().setGameMode(GameMode.SURVIVAL);p().getInventory().clear();p().setItemOnCursor(null);view.getTopInventory().clear();
        left=bow("ordinary_v4",Map.of());right=book("power",1);cost=2;xp=30;
        switch(id){
            case "combine"->{left=book("power",2);right=book("power",2);cost=6;}
            case "stack-right"->{right.editMeta(m->m.setMaxStackSize(16));right.setAmount(3);}
            case "legacy-common"->left=bow("ordinary",Map.of());
            case "legacy-v2-denied"->{left=bow("ordinary",Map.of());right=book("flame",1);cost=-1;}
            case "legacy-v3-denied"->{left=bow("ordinary_v3",Map.of());right=book("flame",1);cost=-1;}
            case "ultimate-conflict"->{left=bow("ordinary_v4",Map.of("duplex",1));right=book("fatal_tempo",1);cost=-1;}
            case "max-level"->{left=bow("ordinary_v4",Map.of("power",7));right=book("power",7);cost=-1;}
            case "lower-noop"->{left=bow("ordinary_v4",Map.of("power",2));cost=-1;}
            case "wrong-book"->{left=book("power",1);right=book("snipe",1);cost=-1;}
            case "spoof-book"->{var real=right;right=new ItemStack(Material.BOOK);right.editMeta(m->{m.displayName(real.getItemMeta().displayName());m.lore(real.getItemMeta().lore());});cost=-1;}
            case "stack-left"->{left.editMeta(m->m.setMaxStackSize(16));left.setAmount(2);cost=-1;}
            case "insufficient"->xp=1;
            case "full"->{for(int slot=0;slot<36;slot++)p().getInventory().setItem(slot,new ItemStack(Material.STONE,64));}
            case "cancel"->cancel=true;
            case "stale", "stale-ordinary"->stale=true;
            case "prepare-veto"->prepareVeto=true;
            case "rename-only"->{right=null;cost=1;}
            case "creative"->{p().setGameMode(GameMode.CREATIVE);xp=0;}
            case "vanilla"->{left=new ItemStack(Material.IRON_SWORD);left.editMeta(m->((Damageable)m).setDamage(50));right=new ItemStack(Material.IRON_INGOT);cost=2;}
        }
        if(!id.equals("vanilla"))left.editMeta(m->{m.displayName(Component.text("Boundary item"));((Repairable)m).setRepairCost(7);m.getPersistentDataContainer().set(new NamespacedKey("fixture","keep"),PersistentDataType.STRING,"retained");});
        view.getTopInventory().setItem(0,left.clone());view.getTopInventory().setItem(1,right==null?null:right.clone());p().setLevel(xp);p().setExp(.375f);commits=c.production().anvils().metrics().committed();
        sync(()->{players.request("alpha","name-"+id);players.await("boundary rename "+id,100,()->Objects.equals(view.getRenameText(),id.equals("rename-only")?"Renamed item":id.equals("vanilla")?"Vanilla blade":"Boundary item"),()->c.later(3,()->{
            boolean success=Set.of("combine","stack-right","legacy-common","rename-only","creative","vanilla").contains(id);
            if(prepareVeto)c.check("later_prepare_veto_not_resurrected",true,empty(view.getTopInventory().getItem(2))&&left.equals(view.getTopInventory().getItem(0))&&right.equals(view.getTopInventory().getItem(1))&&p().getLevel()==xp);
            else c.check(id+"_preview_cost",cost,view.getRepairCost());
            expected=copy(view.getTopInventory().getItem(2));collect(id,success);
        }));});
    }
    /**
     * Checks exact success/rejection conservation after the real output click settles.
     * Custom commit counts distinguish native vanilla repair and prevent duplicate grant credit.
     */
    private void collect(String id,boolean success){
        sync(()->{int count=clicks;players.request("alpha","collect-"+id);players.await("boundary click "+id,100,()->clicks>count,()->c.later(3,()->{
            if(success){
                int remaining=right==null?0:right.getAmount()-1;
                c.check(id+"_conserves_native_transaction",true,empty(view.getTopInventory().getItem(0))&&amount(view.getTopInventory().getItem(1))==remaining
                        &&Objects.equals(expected,p().getItemOnCursor())&&p().getLevel()==xp-(p().getGameMode()==GameMode.CREATIVE?0:cost)&&p().getExp()==.375f);
                if(id.equals("combine"))c.check("combine_book_metadata_and_level",true,((EnchantBookCodec.Read.Valid)books.decode(p().getItemOnCursor())).book().level()==3&&((Repairable)p().getItemOnCursor().getItemMeta()).getRepairCost()==7);
                if(id.equals("legacy-common"))c.check("legacy_identity_unchanged",((ItemReadResult.Valid)weapons.decode(left)).item().instance().identity().toString(),((ItemReadResult.Valid)weapons.decode(p().getItemOnCursor())).item().instance().identity().toString());
                if(id.equals("vanilla"))c.check("vanilla_native_repair_not_custom_commit",true,((Damageable)p().getItemOnCursor().getItemMeta()).getDamage()==0&&c.production().anvils().metrics().committed()==commits);
                else c.check(id+"_one_observed_commit",commits+1,c.production().anvils().metrics().committed());
            }else c.check(id+"_rejection_conserves_inputs_xp",true,Objects.equals(left,view.getTopInventory().getItem(0))&&Objects.equals(right,view.getTopInventory().getItem(1))&&empty(p().getItemOnCursor())&&p().getLevel()==xp&&p().getExp()==.375f&&c.production().anvils().metrics().committed()==commits);
            rows.add(Map.of("step","collect-"+id,"success",success,"cost",cost,"beforeLevel",xp,"afterLevel",p().getLevel(),"fraction",.375));
            cancel=false;stale=false;prepareVeto=false;index++;sync(this::trial);
        }));});
    }
    /**
     * Requests native close and quit, checking the production offer session is gone.
     */
    private void finishCompatibility() {
        p().setItemOnCursor(null);
        sync(()->{players.request("alpha","end-close");players.await("compatibility close",100,()->!(p().getOpenInventory() instanceof AnvilView),()->{
            c.check("compatibility_sessions_clean",0,c.production().anvils().metrics().sessions());c.observe("anvilBoundaryTransactions",rows);
            players.request("alpha","quit");players.await("compatibility quit",100,()->players.quits("alpha")==1,c::finish);
        });});
    }
    /**
     * Keeps inputs live through close/reopen/reconnect and service close, checking each returns them once.
     */
    private void closeRecovery(){
        p().setItemOnCursor(null);p().getInventory().clear();view.getTopInventory().clear();savedLeft=bow("ordinary_v4",Map.of());savedRight=book("power",1);
        view.getTopInventory().setItem(0,savedLeft.clone());view.getTopInventory().setItem(1,savedRight.clone());p().setLevel(30);p().setExp(.375f);
        sync(()->{players.request("alpha","close-inputs");players.await("close returns inputs",100,()->!(p().getOpenInventory() instanceof AnvilView),()->{
            c.check("close_returns_inputs_no_preview_grant",true,count(savedLeft)==1&&count(savedRight)==1&&p().getLevel()==30&&c.production().anvils().metrics().sessions()==0);
            players.request("alpha","reopen");players.await("new native anvil session",100,()->p().getOpenInventory() instanceof AnvilView,()->{
                var old=view;view=(AnvilView)p().getOpenInventory();c.check("reopen_has_new_view",true,view!=old);p().getInventory().clear();view.getTopInventory().setItem(0,savedLeft.clone());view.getTopInventory().setItem(1,savedRight.clone());
                sync(()->{players.request("alpha","reconnect");players.await("new connected player session",200,()->players.joins("alpha")==2&&players.allOnline(),()->{
                    c.check("disconnect_returns_inputs_once",true,count(savedLeft)==1&&count(savedRight)==1&&p().getLevel()==30&&p().getExp()==.375f&&c.production().anvils().metrics().sessions()==0);
                    players.request("alpha","open-service-close");players.await("service-close anvil open",100,()->p().getOpenInventory() instanceof AnvilView,()->{
                        view=(AnvilView)p().getOpenInventory();p().getInventory().clear();view.getTopInventory().setItem(0,savedLeft.clone());view.getTopInventory().setItem(1,savedRight.clone());
                        c.check("service_close_has_pending_callback",true,c.production().anvils().metrics().callbacks()>0);
                        {c.production().anvils().close();c.check("service_close_clears_offer_callbacks_sessions",true,empty(view.getTopInventory().getItem(2))&&c.production().anvils().metrics().callbacks()==0&&c.production().anvils().metrics().sessions()==0);
                            p().closeInventory();c.check("service_close_preserves_inputs",true,count(savedLeft)==1&&count(savedRight)==1&&p().getLevel()==30);
                            c.observe("anvilBoundaryTransactions",rows);players.request("alpha","quit");players.await("boundary quit",100,()->players.quits("alpha")==2,c::finish);
                        }
                    });
                });});
            });
        });});
    }
    /**
     * Requests authoritative full inventory state and yields before the next protocol operation.
     */
    private void sync(Runnable next){p().updateInventory();c.later(5,next::run);}
    /**
     * Counts similar stored items so returned input conservation is independent of slot ordering.
     */
    private int count(ItemStack sample){return Arrays.stream(p().getInventory().getStorageContents()).filter(i->i!=null&&i.isSimilar(sample)).mapToInt(ItemStack::getAmount).sum();}
    /**
     * Clones a nonempty expected output without retaining a mutable live inventory reference.
     */
    private static ItemStack copy(ItemStack s){return empty(s)?null:s.clone();}
    /**
     * Treats null and AIR as the same empty inventory state.
     */
    private static boolean empty(ItemStack s){return s==null||s.getType().isAir();}
    /**
     * Returns zero for empty slots and the real stack count otherwise.
     */
    private static int amount(ItemStack s){return empty(s)?0:s.getAmount();}
    /**
     * Injects the later prepare-veto control; production must not resurrect its cleared result.
     * @param event real native prepare event
     */
    @EventHandler(priority=EventPriority.HIGHEST)public void prepare(PrepareAnvilEvent event){if(prepareVeto)event.setResult(null);}
    /**
     * Changes input identity before output processing to exercise stale managed and ordinary offers.
     * @param event native output-click event
     */
    @EventHandler(priority=EventPriority.LOWEST)public void stale(InventoryClickEvent event){if(stale&&event.getRawSlot()==2){if(CASES.get(index).equals("stale-ordinary")){left=new ItemStack(Material.IRON_SWORD);left.editMeta(m->((Damageable)m).setDamage(50));right=new ItemStack(Material.IRON_INGOT);view.getTopInventory().setItem(1,right.clone());}else{left=left.clone();left.editMeta(m->m.getPersistentDataContainer().set(new NamespacedKey("fixture","changed"),PersistentDataType.INTEGER,1));}view.getTopInventory().setItem(0,left.clone());}}
    /**
     * Injects only the declared output cancellation trial.
     * @param event native click event
     */
    @EventHandler(priority=EventPriority.HIGHEST)public void veto(InventoryClickEvent event){if(cancel&&event.getRawSlot()==2)event.setCancelled(true);}
    /**
     * Counts actual actor click dispatches, including intentionally rejected transactions.
     * @param event native click event
     */
    @EventHandler(priority=EventPriority.MONITOR)public void clicked(InventoryClickEvent event){if(event.getWhoClicked().getUniqueId().equals(players.identity("alpha")))clicks++;}
}
