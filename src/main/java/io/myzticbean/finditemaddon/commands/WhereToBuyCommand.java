package io.myzticbean.finditemaddon.commands;

import io.myzticbean.finditemaddon.FindItemAddOn;
import io.myzticbean.finditemaddon.handlers.command.CmdExecutorHandler;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Material;

public class WhereToBuyCommand implements CommandExecutor {

    private final CmdExecutorHandler cmdExecutor;
    private final String buyCommand;

    public WhereToBuyCommand() {
        this.cmdExecutor = new CmdExecutorHandler();
        // Match the same logic as the original command
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE)
                || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE, " ")) {
            this.buyCommand = "TO_BUY";
        } else {
            this.buyCommand = FindItemAddOn.getConfigProvider().FIND_ITEM_TO_BUY_AUTOCOMPLETE;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(ColorTranslator.translateColorCodes(
                    FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cUsage: /wheretobuy <item|hand>"));
            return true;
        }

        String itemName = args[0];
        if (itemName.equalsIgnoreCase("hand")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use /wheretobuy hand!");
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

        cmdExecutor.handleShopSearch(buyCommand, sender, itemName);
        return true;
    }
}