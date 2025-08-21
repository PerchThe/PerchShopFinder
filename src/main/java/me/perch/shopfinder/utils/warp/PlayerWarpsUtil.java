/**
 * QSFindItemAddOn: An Minecraft add-on plugin for the QuickShop Hikari
 * and Reremake Shop plugins for Spigot server platform.
 * Copyright (C) 2021  myzticbean
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.perch.shopfinder.utils.warp;

import com.olziedev.playerwarps.api.PlayerWarpsAPI;
import com.olziedev.playerwarps.api.events.warp.PlayerWarpTeleportEvent;
import com.olziedev.playerwarps.api.warp.Warp;
import me.perch.shopfinder.dependencies.PlayerWarpsPlugin;
import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.utils.CommonUtils;
import me.perch.shopfinder.utils.log.Logger;
import me.perch.shopfinder.utils.ExcludedWarpsUtil; // <-- Added import
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * @author myzticbean & lukemango
 */
@UtilityClass
public class PlayerWarpsUtil {

    /**
     * Finds the nearest PlayerWarp that the given player can access (not banned, and whitelisted if enabled),
     * and is within 128 blocks of the shop location.
     * @param shopLocation The location of the shop.
     * @param player The player searching/teleporting.
     * @param shopOwner The UUID of the shop owner (for ONLY_SHOW_PLAYER_OWNDED_WARPS).
     * @return The nearest accessible Warp within 128 blocks, or null if none found.
     */
    @Nullable
    public static Warp findNearestWarp(Location shopLocation, Player player, UUID shopOwner) {
        Logger.logDebugInfo("Find nearest warp for shop location " + shopLocation);
        UUID playerId = player.getUniqueId();

        // Get the set of excluded warps
        Set<String> excluded = ExcludedWarpsUtil.getExcludedWarps();

        List<Warp> playersWarps = PlayerWarpsPlugin.getAllWarps().stream()
                .filter(warp -> warp.getWarpLocation().getWorld() != null)
                .filter(warp -> warp.getWarpLocation().getWorld().equals(shopLocation.getWorld().getName()))
                // Exclude if banned
                .filter(warp -> warp.getBanned().stream().noneMatch(banned -> banned.getUUID().equals(playerId)))
                // If whitelist is enabled, only allow if whitelisted
                .filter(warp -> !warp.isWhitelistEnabled() || (warp.getWhitelisted() != null && warp.getWhitelisted().contains(playerId)))
                // Exclude warps in the excluded list
                .filter(warp -> !excluded.contains(warp.getWarpName().toLowerCase()))
                // Only include warps within 128 blocks (3D distance)
                .filter(warp -> {
                    double distance = shopLocation.distance(
                            new Location(
                                    shopLocation.getWorld(),
                                    warp.getWarpLocation().getX(),
                                    warp.getWarpLocation().getY(),
                                    warp.getWarpLocation().getZ()
                            )
                    );
                    return distance <= 128;
                })
                .toList();

        if (FindItemAddOn.getConfigProvider().ONLY_SHOW_PLAYER_OWNDED_WARPS) {
            playersWarps = playersWarps.stream()
                    .filter(warp -> warp.getWarpPlayer().getUUID().equals(shopOwner))
                    .toList();
        }
        if (!playersWarps.isEmpty()) {
            Map<Double, Warp> warpDistanceMap = new TreeMap<>();
            playersWarps.forEach(warp -> {
                var distance3D = CommonUtils.calculateDistance3D(
                        shopLocation.getX(),
                        shopLocation.getY(),
                        shopLocation.getZ(),
                        warp.getWarpLocation().getX(),
                        warp.getWarpLocation().getY(),
                        warp.getWarpLocation().getZ()
                );
                warpDistanceMap.put(distance3D, warp);
                Logger.logDebugInfo("Warp Distance: " + distance3D + " Warp Name: " + warp.getWarpName() + ", Warp World: " + warp.getWarpLocation().getWorld());
            });
            for (Map.Entry<Double, Warp> doubleWarpEntry : warpDistanceMap.entrySet()) {
                Double distance3D = doubleWarpEntry.getKey();
                Warp warp = doubleWarpEntry.getValue();
                Logger.logDebugInfo("Warp: " + warp.getWarpName() + " " + warp.isWarpLocked() + " Distance in 3D: " + distance3D);
                // Is the config set to not tp if player warp is locked, and if so, is the warp locked?
                // also check distance from shop (should not be too long)
                if (FindItemAddOn.getConfigProvider().DO_NOT_TP_IF_PLAYER_WARP_LOCKED
                        && warp.isWarpLocked()
                        && distance3D > 500) {
                    continue;
                }
                return warp;
            }
        }
        return null;
    }
    /**
     * Checks if the given player is the owner of the specified warp.
     * @param player The player to check.
     * @param warpName The name of the warp.
     * @return true if the player owns the warp, false otherwise.
     */
    public static boolean isOwner(Player player, String warpName) {
        // Use the PlayerWarps API to get the warp by name
        Warp warp = PlayerWarpsPlugin.getAllWarps().stream()
                .filter(w -> w.getWarpName().equalsIgnoreCase(warpName))
                .findFirst()
                .orElse(null);
        if (warp == null) return false;
        return warp.getWarpPlayer().getUUID().equals(player.getUniqueId());
    }

    /**
     * Issue #24 Fix: Extracted method from FoundShopsMenu class
     * @param player
     * @param warpName
     */
    public static void executeWarpPlayer(Player player, String warpName) {
        PlayerWarpsAPI.getInstance(api -> {
            Warp playerWarp = api.getPlayerWarp(warpName, player);
            if (playerWarp != null) {
                playerWarp.getWarpLocation().teleportWarp(player, PlayerWarpTeleportEvent.Cause.PLAYER_WARP_MENU);
            } else {
                Logger.logError("&e" + player.getName() + " &cis trying to teleport to a PlayerWarp that does not exist!");
            }
        });
    }
}