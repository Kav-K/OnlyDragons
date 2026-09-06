package com.kaveenk.onlydragons;

import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

class PracticeCommandTest {
    ServerMock server;OnlyDragonsPlugin plugin;PlayerMock player;
    @BeforeEach void setup(){server=MockBukkit.mock();plugin=MockBukkit.load(OnlyDragonsPlugin.class);player=server.addPlayer();drain();}
    @AfterEach void cleanup(){MockBukkit.unmock();}
    List<String> drain(){var messages=new ArrayList<String>();String message;while((message=player.nextMessage())!=null)messages.add(org.bukkit.ChatColor.stripColor(message));return messages;}
    void command(String text){server.dispatchCommand(player,"onlydragons "+text);}
    @Test void deniedKitAndInspectionCannotMutateInventoryOrCreateTarget(){
        command("dev kit ordinary");assertTrue(player.getInventory().isEmpty());assertEquals(0,plugin.combat().activeCount());
        assertTrue(drain().contains("You do not have permission to use practice tools."));
        player.addAttachment(plugin,"onlydragons.combat",false);command("combat last");
        assertEquals(List.of("You do not have permission to inspect combat."),drain());
    }
    @Test void kitRequiresTwoSlotsAtomicallyAndNeverOverwritesOtherEquipment(){
        player.setOp(true);for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        player.getInventory().setItem(0,null);command("dev kit ordinary");
        assertNull(player.getInventory().getItem(0));assertTrue(drain().contains("Two empty storage slots required; no kit granted."));
        player.getInventory().setItem(1,null);command("dev kit ordinary");
        assertEquals(Material.BOW,player.getInventory().getItem(0).getType());assertEquals(Material.ARROW,player.getInventory().getItem(1).getType());
        assertEquals(64,player.getInventory().getItem(1).getAmount());assertEquals(Material.STONE,player.getInventory().getItem(2).getType());
        command("dev kit ordinary");assertEquals(64,player.getInventory().getItem(1).getAmount());
    }
    @Test void malformedProfilesAndUnknownKitsFailBeforeCreatingEntities(){
        player.setOp(true);command("dev dummy unknown");command("dev dummy full NaN");command("dev scenario reduced 0");command("dev kit unknown");
        assertEquals(0,plugin.combat().activeCount());assertTrue(player.getInventory().isEmpty());
        assertEquals(4,drain().stream().filter(m->m.startsWith("Invalid practice request:")).count());
        command("combat last");assertEquals(List.of("No combat hit in this session."),drain());
    }
    @Test void consolePlayerOnlyCommandsHaveClearRegisteredHandling(){
        var console=server.getConsoleSender();
        for(String text:List.of("combat last","dev kit ordinary","dev dummy full","dev scenario reduced","dev reset")){
            server.dispatchCommand(console,"onlydragons "+text);assertEquals("This command requires a player.",org.bukkit.ChatColor.stripColor(console.nextMessage()));
        }
        assertEquals(0,plugin.combat().activeCount());
    }
}
