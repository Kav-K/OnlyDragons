package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.application.PresentationFormatter;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.anvil.EnchantBook;
import com.kaveenk.onlydragons.paper.item.anvil.EnchantBookCodec;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Development-only book grants through {@link EnchantBookCodec}; ordinary anvil
 * access does not depend on the grant permission. Invoke on the server thread.
 * Books are placed only in an empty slot; malformed requests and full inventories
 * produce feedback rather than dropping or replacing an item.
 */
public final class BookCommand {
    private final ItemRegistry catalog;
    private final EnchantBookCodec codec;
    /**
     * Binds the command to its existing authority; does not register listeners or tasks.
     * @param catalog trusted current book catalog; identity and level validation use this exact revision
     */
    public BookCommand(ItemRegistry catalog) { this.catalog = catalog; codec = new EnchantBookCodec(catalog); }
    /**
     * Tests routing only; this does not authorize execution.
     * @param args root arguments without a leading slash
     * @return true when the namespace belongs to this delegate
     */
    public boolean handles(String[] args) { return args.length >= 2 && args[0].equalsIgnoreCase("dev") && args[1].equalsIgnoreCase("book"); }
    /**
     * Parses a four-argument dev-book grant after permission and live-player checks.
     * Integer/recipe validation failures are translated into an invalid-request reply.
     * @param sender requester and feedback recipient
     * @param args Complete root arguments; exactly dev, book, enchant ID and level for a grant
     */
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("onlydragons.calibration")) { say(sender, "You do not have permission to grant enchant books."); return; }
        if (!(sender instanceof Player player)) { say(sender, "This command requires a player."); return; }
        if (args.length != 4) { say(sender, "Usage: /onlydragons dev book <enchant> <level>"); return; }
        try {
            var book = new EnchantBook(catalog.revision(), args[2].toLowerCase(Locale.ROOT), Integer.parseInt(args[3]));
            var item = codec.encode(book);
            int slot = player.getInventory().firstEmpty();
            if (slot < 0) { say(sender, "Inventory full; no book granted."); return; }
            player.getInventory().setItem(slot, item);
            say(sender, "Granted " + catalog.enchant(book.enchantId()).displayName() + " " + PresentationFormatter.roman(book.level())
                    + ". Apply it to a managed bow at an anvil.");
        } catch (IllegalArgumentException invalid) { say(sender, "Invalid book request: " + invalid.getMessage()); }
    }
    /**
     * Returns catalog enchant IDs or legal levels; the root router performs prefix filtering.
     * @param sender requester used for permission and player-only filtering
     * @param args nonempty root arguments including the partial token
     * @return non-null candidate list, possibly empty; candidates are not prefix-filtered here
     */
    public List<String> complete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (!sender.hasPermission("onlydragons.calibration") || args.length < 2 || !args[0].equalsIgnoreCase("dev")) return List.of();
        if (args.length == 2) return List.of("book");
        if (!handles(args)) return List.of();
        if (args.length == 3) return catalog.enchantments().keySet().stream().sorted().toList();
        if (args.length == 4) {
            var enchant = catalog.enchantments().get(args[2].toLowerCase(Locale.ROOT));
            if (enchant != null) return enchant.levelModifiers().keySet().stream().sorted().map(Object::toString).toList();
        }
        return List.of();
    }
    /**
     * Sends semantic command text through the shared Adventure formatter.
     * @param sender command recipient
     * @param text plain semantic content; formatting does not change command state
     */
    private static void say(CommandSender sender, String text) { sender.sendMessage(PresentationFormatter.message(text)); }
}
