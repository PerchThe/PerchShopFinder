package me.perch.shopfinder.commands;

import me.perch.shopfinder.dependencies.PlayerWarpsPlugin;
import me.perch.shopfinder.utils.ExcludedWarpsUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ExcludeWarpCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage("Usage: /" + label + " <warpname>");
            return true;
        }

        String warpName = args[0];
        List<String> playerWarps = PlayerWarpsPlugin.getPlayerWarpNames(player);
        boolean ownsWarp = playerWarps.stream().anyMatch(w -> w.equalsIgnoreCase(warpName));

        if (!ownsWarp) {
            player.sendMessage("You do not own this warp.");
            return true;
        }

        // Defensive: check if ExcludedWarpsUtil is initialized
        Set<String> excluded = ExcludedWarpsUtil.getExcludedWarps();
        if (excluded == null) {
            player.sendMessage("Internal error: Excluded warps not initialized. Please contact an admin.");
            return true;
        }

        if (label.equalsIgnoreCase("excludewarp")) {
            if (excluded.contains(warpName.toLowerCase())) {
                player.sendMessage("Warp '" + warpName + "' is already excluded from all searches.");
            } else {
                ExcludedWarpsUtil.addExcludedWarp(warpName);
                player.sendMessage("Warp '" + warpName + "' is now excluded from all searches.");
            }
        } else if (label.equalsIgnoreCase("includewarp")) {
            if (!excluded.contains(warpName.toLowerCase())) {
                player.sendMessage("Warp '" + warpName + "' is not excluded.");
            } else {
                ExcludedWarpsUtil.removeExcludedWarp(warpName);
                player.sendMessage("Warp '" + warpName + "' is now included in all searches.");
            }
        } else {
            player.sendMessage("Unknown command. Use /excludewarp or /includewarp.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> warps = PlayerWarpsPlugin.getPlayerWarpNames(player);
            List<String> matches = new ArrayList<>();
            for (String warp : warps) {
                if (warp.toLowerCase().startsWith(partial)) {
                    matches.add(warp);
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }
}