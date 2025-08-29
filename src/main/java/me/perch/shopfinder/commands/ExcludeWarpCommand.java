package me.perch.shopfinder.commands;

import me.perch.shopfinder.dependencies.PlayerWarpsPlugin;
import me.perch.shopfinder.utils.ExcludedWarpsUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ExcludeWarpCommand implements CommandExecutor, TabCompleter {

    private static String norm(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage("Usage: /" + command.getName() + " <warpname>");
            return true;
        }

        String rawInputName = String.join(" ", args);
        String inputKey = norm(rawInputName);

        List<String> playerWarps = PlayerWarpsPlugin.getPlayerWarpNames(player);
        String ownedDisplayName = playerWarps.stream()
                .filter(w -> norm(w).equals(inputKey))
                .findFirst()
                .orElse(null);

        if (ownedDisplayName == null) {
            player.sendMessage("You do not own this warp.");
            return true;
        }

        Set<String> excluded = ExcludedWarpsUtil.getExcludedWarps();
        if (excluded == null) {
            player.sendMessage("Internal error: Excluded warps not initialized. Please contact an admin.");
            return true;
        }

        String cmd = command.getName().toLowerCase(Locale.ROOT); // use command, not label
        String ownedKey = norm(ownedDisplayName);

        if (cmd.equals("excludewarp")) {
            if (excluded.contains(ownedKey)) {
                player.sendMessage("Warp '" + ownedDisplayName + "' is already excluded from all searches.");
            } else {
                ExcludedWarpsUtil.addExcludedWarp(ownedKey); // store normalized key
                player.sendMessage("Warp '" + ownedDisplayName + "' is now excluded from all searches.");
            }
        } else if (cmd.equals("includewarp")) {
            if (!excluded.contains(ownedKey)) {
                player.sendMessage("Warp '" + ownedDisplayName + "' is not excluded.");
            } else {
                ExcludedWarpsUtil.removeExcludedWarp(ownedKey);
                player.sendMessage("Warp '" + ownedDisplayName + "' is now included in all searches.");
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
            String partial = args[0];
            String partialKey = norm(partial);
            List<String> warps = PlayerWarpsPlugin.getPlayerWarpNames(player);
            return warps.stream()
                    .filter(w -> norm(w).startsWith(partialKey))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
