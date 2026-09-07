package com.kaveenk.onlydragons;

import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MockBukkit command-registration and inventory-preflight regressions. Literal messages, unchanged inventory and active-target counts are the oracles; these do not prove physical arrows or native dragon behavior.
 */
class PracticeCommandTest {
    ServerMock server;OnlyDragonsPlugin plugin;PlayerMock player;
    /**
     * Creates a fresh isolated MockBukkit boundary and the collaborators used by this class's oracles.
     */
    @BeforeEach void setup(){server=MockBukkit.mock();plugin=MockBukkit.load(OnlyDragonsPlugin.class);player=server.addPlayer();drain();}
    /**
     * Releases the mock server/plugin lifecycle after each test so scheduler and static Bukkit state cannot leak between cases.
     */
    @AfterEach void cleanup(){MockBukkit.unmock();}
    /**
     * Consumes pending mock chat and strips legacy colors for literal semantic comparisons.
     * @return messages in delivery order
     */
    List<String> drain(){var messages=new ArrayList<String>();String message;while((message=player.nextMessage())!=null)messages.add(org.bukkit.ChatColor.stripColor(message));return messages;}
    /**
     * Routes a relative command through the registered root handler.
     * @param text arguments after onlydragons
     */
    void command(String text){server.dispatchCommand(player,"onlydragons "+text);}
    /**
     * Permission denial must produce feedback while leaving inventory and target admission empty.
     */
    @Test void deniedKitAndInspectionCannotMutateInventoryOrCreateTarget(){
        command("dev kit ordinary");assertTrue(player.getInventory().isEmpty());assertEquals(0,plugin.combat().activeCount());
        assertTrue(drain().contains("You do not have permission to use practice tools."));
        player.addAttachment(plugin,"onlydragons.combat",false);command("combat last");
        assertEquals(List.of("You do not have permission to inspect combat."),drain());
    }
    /**
     * One empty slot rejects the whole kit; two empty slots receive exactly one bow and 64 arrows without overwriting stone.
     */
    @Test void kitRequiresTwoSlotsAtomicallyAndNeverOverwritesOtherEquipment(){
        player.setOp(true);for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        player.getInventory().setItem(0,null);command("dev kit ordinary");
        assertNull(player.getInventory().getItem(0));assertTrue(drain().contains("Two empty storage slots required; no kit granted."));
        player.getInventory().setItem(1,null);command("dev kit ordinary");
        assertEquals(Material.BOW,player.getInventory().getItem(0).getType());assertEquals(Material.ARROW,player.getInventory().getItem(1).getType());
        assertEquals(64,player.getInventory().getItem(1).getAmount());assertEquals(Material.STONE,player.getInventory().getItem(2).getType());
        command("dev kit ordinary");assertEquals(64,player.getInventory().getItem(1).getAmount());
    }
    /**
     * Invalid profile, HP and loadout requests leave no target/item side effects and preserve the empty-hit diagnostic.
     */
    @Test void malformedProfilesAndUnknownKitsFailBeforeCreatingEntities(){
        player.setOp(true);command("dev dummy unknown");command("dev dummy full NaN");command("dev scenario reduced 0");command("dev kit unknown");
        assertEquals(0,plugin.combat().activeCount());assertTrue(player.getInventory().isEmpty());
        assertEquals(4,drain().stream().filter(m->m.startsWith("Invalid practice request:")).count());
        command("combat last");assertEquals(List.of("No combat hit in this session."),drain());
    }
    /**
     * Exercises every registered player-only practice branch with the console and asserts its exact diagnostic.
     */
    @Test void consolePlayerOnlyCommandsHaveClearRegisteredHandling(){
        var console=server.getConsoleSender();
        for(String text:List.of("combat last","dev kit ordinary","dev dummy full","dev scenario reduced","dev reset")){
            server.dispatchCommand(console,"onlydragons "+text);assertEquals("This command requires a player.",org.bukkit.ChatColor.stripColor(console.nextMessage()));
        }
        assertEquals(0,plugin.combat().activeCount());
    }
}
