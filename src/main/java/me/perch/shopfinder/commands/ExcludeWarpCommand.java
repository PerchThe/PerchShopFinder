package me.perch.shopfinder.commands;

import me.perch.shopfinder.utils.ExcludedWarpsUtil;
import me.perch.shopfinder.utils.warp.PlayerWarpsUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ExcludeWarpCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("Usage: /" + label + " <warpname>");
            return true;
        }
        String warpName = args[0];
        if (!PlayerWarpsUtil.isOwner(player, warpName)) {
            player.sendMessage("You do not own this warp.");
            return true;
        }
        if (label.equalsIgnoreCase("excludewarp")) {
            ExcludedWarpsUtil.addExcludedWarp(warpName);
            player.sendMessage("Warp '" + warpName + "' is now excluded from all searches.");
        } else if (label.equalsIgnoreCase("includewarp")) {
            ExcludedWarpsUtil.removeExcludedWarp(warpName);
            player.sendMessage("Warp '" + warpName + "' is now included in all searches.");
        }
        return true;
    }
}