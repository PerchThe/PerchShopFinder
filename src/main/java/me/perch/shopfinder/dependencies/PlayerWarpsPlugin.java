package me.perch.shopfinder.dependencies;

import com.olziedev.playerwarps.api.PlayerWarpsAPI;
import com.olziedev.playerwarps.api.warp.Warp;
import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.utils.enums.NearestWarpModeEnum;
import me.perch.shopfinder.utils.log.Logger;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * @author myzticbean
 */
@UtilityClass
public class PlayerWarpsPlugin {

    private static boolean isEnabled = false;
    private static List<Warp> allWarpsList = null;
    private static final String ALL_WARPS_LIST_CLASSPATH = PlayerWarpsPlugin.class.getCanonicalName() + ".allWarpsList";

    public static void setup() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlayerWarps")) {
            Logger.logInfo("Found PlayerWarps");
            PlayerWarpsAPI.getInstance(api -> isEnabled = (api != null));
            updateAllWarpsFromAPI();
        }
    }

    public static boolean getIsEnabled() {
        return isEnabled;
    }

    /**
     * Deprecated: Do not use cached API instance.
     */
    @Deprecated
    public static PlayerWarpsAPI getAPI() {
        final PlayerWarpsAPI[] apiHolder = new PlayerWarpsAPI[1];
        PlayerWarpsAPI.getInstance(api -> apiHolder[0] = api);
        return apiHolder[0];
    }

    /**
     * Returns the cached list of warps, or fetches live if cache is empty.
     */
    public static List<Warp> getAllWarps() {
        if (!isEnabled) {
            return Collections.emptyList();
        }
        if (allWarpsList != null && !allWarpsList.isEmpty()) {
            return allWarpsList;
        }
        // Fallback: fetch live if cache is empty
        final List<Warp>[] result = new List[]{Collections.emptyList()};
        PlayerWarpsAPI.getInstance(api -> {
            if (api != null) result[0] = api.getPlayerWarps(false);
        });
        return result[0];
    }

    /**
     * Refreshes the cached list of warps from the API.
     */
    public static void updateAllWarpsFromAPI() {
        if (isEnabled) {
            long start = System.currentTimeMillis();
            PlayerWarpsAPI.getInstance(api -> {
                if (api != null) {
                    allWarpsList = api.getPlayerWarps(false);
                    Logger.logDebugInfo("Update complete for PlayerWarps list! Found " + allWarpsList.size() + " warps. Time took: " + (System.currentTimeMillis() - start) + "ms.");
                } else {
                    Logger.logError("PlayerWarpsAPI is null during updateAllWarpsFromAPI!");
                }
            });
        }
    }

    public static void updateWarpsOnEventCall(Warp warp, boolean isRemoved) {
        Logger.logDebugInfo("Got a PlayerWarps event call... checking nearest-warp-mode");
        if (FindItemAddOn.getConfigProvider().NEAREST_WARP_MODE == NearestWarpModeEnum.PLAYER_WARPS.value()) {
            Logger.logDebugInfo("'nearest-warp-mode' found set to 2");
            if (getIsEnabled()) {
                Logger.logDebugInfo("PlayerWarps plugin is enabled");
                tryUpdateWarps(warp, isRemoved, 1);
            }
        } else {
            Logger.logDebugInfo("No update required to '" + ALL_WARPS_LIST_CLASSPATH + "' as PlayerWarps integration is disabled.");
        }
    }

    private static void tryUpdateWarps(Warp warp, boolean isRemoved, int updateTrialSequence) {
        if (allWarpsList != null) {
            if (isRemoved) {
                if (allWarpsList.contains(warp)) {
                    allWarpsList.remove(warp);
                    Logger.logDebugInfo("Warp removed from allWarpsList: " + warp.getWarpName());
                } else {
                    Logger.logError("Error occurred while updating '" + ALL_WARPS_LIST_CLASSPATH + "'. Warp name: '" + warp.getWarpName() + "' does not exist!");
                }
            } else {
                allWarpsList.add(warp);
                Logger.logDebugInfo("New warp added to allWarpsList: " + warp.getWarpName());
            }
        } else {
            if (updateTrialSequence == 1) {
                updateAllWarpsFromAPI();
                tryUpdateWarps(warp, isRemoved, 2);
            } else {
                StringBuilder errorMsg = new StringBuilder();
                errorMsg.append("Error occurred while updating '").append(ALL_WARPS_LIST_CLASSPATH).append("' as it is null! ")
                        .append("Please install PlayerWarps by Olzie-12 if you would like to use 'nearest-warp-mode' as 2. ")
                        .append("If PlayerWarps plugin is installed and issue persists, please contact the developer!");
                Logger.logError(errorMsg.toString());
            }
        }
    }

    public static boolean isWarpLocked(Player player, String warpName) {
        final boolean[] locked = {false};
        PlayerWarpsAPI.getInstance(api -> {
            if (api != null) {
                Warp warp = api.getPlayerWarp(warpName, player);
                locked[0] = warp != null && warp.isWarpLocked();
            }
        }); 
        return locked[0];
    }

    /**
     * Returns the average rating for a given warp name, or -1 if not available.
     */
    public static double getWarpAverageRating(String warpName) {
        if (!isEnabled || warpName == null || warpName.isEmpty()) return -1;
        final double[] avg = {-1};
        PlayerWarpsAPI.getInstance(api -> {
            if (api != null) {
                Warp warp = api.getPlayerWarp(warpName, null);
                if (warp != null && warp.getWarpRate() != null) {
                    try {
                        avg[0] = warp.getWarpRate().getRateAverage();
                    } catch (Exception ignored) {}
                }
            }
        });
        return avg[0];
    }

    /**
     * Returns the total number of ratings for a given warp name, or -1 if not available.
     */
    public static int getWarpTotalRatings(String warpName) {
        if (!isEnabled || warpName == null || warpName.isEmpty()) return -1;
        final int[] total = {-1};
        PlayerWarpsAPI.getInstance(api -> {
            if (api != null) {
                Warp warp = api.getPlayerWarp(warpName, null);
                if (warp != null && warp.getWarpRate() != null) {
                    try {
                        total[0] = warp.getWarpRate().getPlayersRatedAmount();
                    } catch (Exception ignored) {}
                }
            }
        });
        return total[0];
    }
}