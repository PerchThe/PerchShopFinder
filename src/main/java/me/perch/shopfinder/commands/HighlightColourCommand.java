package me.perch.shopfinder.commands;

import me.perch.shopfinder.utils.HighlightColourManager;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.stream.Collectors;

public class HighlightColourCommand implements CommandExecutor {

    // All valid Minecraft team colors + RAINBOW
    private static final Set<String> VALID_COLOURS = Set.of(
            "BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED", "DARK_PURPLE",
            "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA", "RED", "LIGHT_PURPLE",
            "YELLOW", "WHITE", "RAINBOW"
    );
    private static final String COLOUR_LIST = VALID_COLOURS.stream().collect(Collectors.joining(", "));

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorTranslator.translateColorCodes("&cOnly players can use this command!"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ColorTranslator.translateColorCodes("&8» &eUsage: &f/highlightcolour <colour>"));
            player.sendMessage(ColorTranslator.translateColorCodes("&7Available: &f" + COLOUR_LIST));
            return true;
        }

        String colour = args[0].toUpperCase();

        if (!VALID_COLOURS.contains(colour)) {
            player.sendMessage(ColorTranslator.translateColorCodes("&8» &cInvalid colour! Try: &e" + COLOUR_LIST));
            return true;
        }

        HighlightColourManager.setColour(player.getUniqueId(), colour);

        // Get the ChatColor for the selected colour
        ChatColor chatColor = getChatColor(colour);

        player.sendMessage(ColorTranslator.translateColorCodes("&8» &fYour highlight colour is now "
                + chatColor + colour.replace('_', ' ') + ChatColor.RESET));
        return true;
    }

    // Helper to map string to ChatColor
    private ChatColor getChatColor(String colour) {
        return switch (colour.toUpperCase()) {
            case "BLACK" -> ChatColor.BLACK;
            case "DARK_BLUE" -> ChatColor.DARK_BLUE;
            case "DARK_GREEN" -> ChatColor.DARK_GREEN;
            case "DARK_AQUA" -> ChatColor.DARK_AQUA;
            case "DARK_RED" -> ChatColor.DARK_RED;
            case "DARK_PURPLE" -> ChatColor.DARK_PURPLE;
            case "GOLD" -> ChatColor.GOLD;
            case "GRAY" -> ChatColor.GRAY;
            case "DARK_GRAY" -> ChatColor.DARK_GRAY;
            case "BLUE" -> ChatColor.BLUE;
            case "GREEN" -> ChatColor.GREEN;
            case "AQUA" -> ChatColor.AQUA;
            case "RED" -> ChatColor.RED;
            case "LIGHT_PURPLE" -> ChatColor.LIGHT_PURPLE;
            case "YELLOW" -> ChatColor.YELLOW;
            case "WHITE" -> ChatColor.WHITE;
            case "RAINBOW" -> ChatColor.LIGHT_PURPLE; // Pick your favorite for "rainbow"
            default -> ChatColor.YELLOW;
        };
    }
}