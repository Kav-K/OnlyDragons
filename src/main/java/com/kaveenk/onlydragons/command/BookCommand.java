package com.kaveenk.onlydragons.command;

import com.kaveenk.onlydragons.application.PresentationFormatter;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.anvil.EnchantBook;
import com.kaveenk.onlydragons.paper.item.anvil.EnchantBookCodec;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Permission-gated development grants; anvils themselves retain vanilla access. */
public final class BookCommand {
    private final ItemRegistry catalog;
    private final EnchantBookCodec codec;
    public BookCommand(ItemRegistry catalog) { this.catalog = catalog; codec = new EnchantBookCodec(catalog); }
    public boolean handles(String[] args) { return args.length >= 2 && args[0].equalsIgnoreCase("dev") && args[1].equalsIgnoreCase("book"); }
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
    public List<String> complete(CommandSender sender, String[] args) {
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
    private static void say(CommandSender sender, String text) { sender.sendMessage(PresentationFormatter.message(text)); }
}
