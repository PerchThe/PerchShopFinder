package me.perch.shopfinder.utils.warp;

import com.olziedev.playerwarps.api.PlayerWarpsAPI;
import com.olziedev.playerwarps.api.warp.Warp;
import me.perch.shopfinder.dependencies.PlayerWarpsPlugin;
import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.utils.ExcludedWarpsUtil;
import me.perch.shopfinder.utils.log.Logger;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@UtilityClass
public class PlayerWarpsUtil {

    @Nullable
    public static Warp findNearestWarp(Location shopLocation, Player player, UUID shopOwner) {
        if (shopLocation.getWorld() == null) return null;

        String worldName = shopLocation.getWorld().getName();
        UUID playerId = player.getUniqueId();
        Set<String> excluded = ExcludedWarpsUtil.getExcludedWarps();
        boolean onlyOwned = FindItemAddOn.getConfigProvider().ONLY_SHOW_PLAYER_OWNDED_WARPS;
        boolean checkLocked = FindItemAddOn.getConfigProvider().DO_NOT_TP_IF_PLAYER_WARP_LOCKED;

        double minDistanceSquared = 16384.0;
        Warp nearest = null;

        List<Warp> allWarps = PlayerWarpsPlugin.getAllWarps();

        double shopX = shopLocation.getX();
        double shopY = shopLocation.getY();
        double shopZ = shopLocation.getZ();

        for (Warp warp : allWarps) {
            if (warp.getWarpLocation().getWorld() == null || !warp.getWarpLocation().getWorld().equals(worldName)) {
                continue;
            }

            if (excluded.contains(warp.getWarpName().toLowerCase())) {
                continue;
            }

            if (onlyOwned && !warp.getWarpPlayer().getUUID().equals(shopOwner)) {
                continue;
            }

            if (warp.getBanned().stream().anyMatch(b -> b.getUUID().equals(playerId))) {
                continue;
            }
            if (warp.isWhitelistEnabled() && (warp.getWhitelisted() == null || !warp.getWhitelisted().contains(playerId))) {
                continue;
            }

            double dx = shopX - warp.getWarpLocation().getX();
            if (Math.abs(dx) > 128) continue; // Square root of 16384 is 128

            double dz = shopZ - warp.getWarpLocation().getZ();
            if (Math.abs(dz) > 128) continue;

            double dy = shopY - warp.getWarpLocation().getY();

            double distSq = (dx * dx) + (dy * dy) + (dz * dz);

            if (distSq <= minDistanceSquared) {
                if (checkLocked && warp.isWarpLocked() && distSq > (500 * 500)) {
                    continue;
                }
                minDistanceSquared = distSq;
                nearest = warp;
            }
        }

        return nearest;
    }

    public static boolean isOwner(Player player, String warpName) {
        Warp warp = PlayerWarpsPlugin.getAllWarps().stream()
                .filter(w -> w.getWarpName().equalsIgnoreCase(warpName))
                .findFirst()
                .orElse(null);
        if (warp == null) return false;
        return warp.getWarpPlayer().getUUID().equals(player.getUniqueId());
    }

    public static void executeWarpPlayer(Player player, String warpName) {
        PlayerWarpsAPI.getInstance(api -> {
            Warp playerWarp = api.getPlayerWarp(warpName, player);
            if (playerWarp != null) {

                // Optional but recommended: Close the ShopFinder GUI before teleporting
                // to prevent standard Bukkit inventory/teleport visual glitches.
                player.closeInventory();

                // Dispatches the native command, forcing the PlayerWarps plugin to handle
                // ALL of its own internal tracking, including the visit counter.
                player.performCommand("pw " + playerWarp.getWarpName());

            } else {
                Logger.logError("&e" + player.getName() + " &cis trying to teleport to a PlayerWarp that does not exist!");
            }
        });
    }
}