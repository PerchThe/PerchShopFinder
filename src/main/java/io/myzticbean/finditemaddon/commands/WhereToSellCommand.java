package io.myzticbean.finditemaddon.commands;

import io.myzticbean.finditemaddon.FindItemAddOn;
import io.myzticbean.finditemaddon.handlers.command.CmdExecutorHandler;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WhereToSellCommand implements CommandExecutor {

    private final CmdExecutorHandler cmdExecutor;
    private final String sellCommand;

    public WhereToSellCommand() {
        this.cmdExecutor = new CmdExecutorHandler();
        // Match the same logic as the original command
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE)
                || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE, " ")) {
            this.sellCommand = "TO_SELL";
        } else {
            this.sellCommand = FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cUsage: /wheretosell <item|hand>"));
            return true;
        }

        String itemName = args[0];
        if (itemName.equalsIgnoreCase("hand")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use /wheretosell hand!");
                return true;
            }
            Player player = (Player) sender;
            Material handMat = player.getInventory().getItemInMainHand().getType();
            if (handMat == Material.AIR) {
                sender.sendMessage(ColorTranslator.translateColorCodes(
                        FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cYou are not holding any item!"));
                return true;
            }
            itemName = handMat.name();
        }

        cmdExecutor.handleShopSearch(sellCommand, sender, itemName);
        return true;
    }
}
