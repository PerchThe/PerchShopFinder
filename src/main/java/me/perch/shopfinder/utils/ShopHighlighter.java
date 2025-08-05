package me.perch.shopfinder.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;

public class ShopHighlighter implements Listener {

    private static final String PDC_KEY = "shop_highlight";
    private static final String HIGHLIGHT_SHULKER_NAME = "PerchShopFinderHighlight";
    private static final Map<String, Set<UUID>> chunkShulkerMap = new HashMap<>();
    private static Plugin pluginInstance;

    // All valid Minecraft team colors + RAINBOW
    private static final Set<String> SUPPORTED_COLOURS = Set.of(
            "BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED", "DARK_PURPLE",
            "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA", "RED", "LIGHT_PURPLE",
            "YELLOW", "WHITE", "RAINBOW"
    );

    public static void init(Plugin plugin) {
        pluginInstance = plugin;
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ShopHighlighter(), plugin);
        cleanupInvalidTeamEntries();
    }

    public static void highlightShop(Location location, int durationTicks, Plugin plugin, String colour) {
        Set<Location> chestLocations = getChestLocations(location);
        for (Location chestLoc : chestLocations) {
            highlightBlock(chestLoc, durationTicks, plugin, colour);
        }
    }

    public static void highlightShop(Location location, int durationTicks, Plugin plugin) {
        highlightShop(location, durationTicks, plugin, "YELLOW");
    }

    private static Set<Location> getChestLocations(Location location) {
        Set<Location> locations = new HashSet<>();
        Block block = location.getBlock();
        if (block.getType() == Material.CHEST) {
            BlockState state = block.getState();
            if (state instanceof Chest chest) {
                if (chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
                    if (doubleChest.getLeftSide() instanceof Chest leftChest) {
                        locations.add(leftChest.getLocation());
                    }
                    if (doubleChest.getRightSide() instanceof Chest rightChest) {
                        locations.add(rightChest.getLocation());
                    }
                } else {
                    locations.add(block.getLocation());
                }
            }
        } else {
            locations.add(location);
        }
        return locations;
    }

    private static void highlightBlock(Location location, int durationTicks, Plugin plugin, String colour) {
        // Remove any existing invisible glowing shulkers at this location
        location.getWorld().getNearbyEntities(location, 0.5, 1, 0.5).stream()
                .filter(e -> e instanceof Shulker shulker && shulker.isGlowing() && shulker.isInvisible())
                .forEach(Entity::remove);

        // Spawn a new invisible glowing shulker
        Shulker shulker = location.getWorld().spawn(location.clone().add(0.5, 0, 0.5), Shulker.class, s -> {
            s.setAI(false);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setInvisible(true);
            s.setGlowing(true);
            s.setGravity(false);
            s.setCustomNameVisible(false);
            s.getPersistentDataContainer().set(getHighlightKey(), PersistentDataType.BYTE, (byte) 1);
            s.setCustomName(HIGHLIGHT_SHULKER_NAME);
        });

        // Track this shulker by chunk
        String chunkKey = getChunkKey(shulker.getLocation().getChunk());
        chunkShulkerMap.computeIfAbsent(chunkKey, k -> new HashSet<>()).add(shulker.getUniqueId());

        // Rainbow logic
        if ("RAINBOW".equalsIgnoreCase(colour)) {
            List<String> rainbowColors = List.of(
                    "RED", "GOLD", "YELLOW", "GREEN", "AQUA", "BLUE", "DARK_PURPLE", "LIGHT_PURPLE", "WHITE"
            );
            final int[] colorIndex = {0};
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

            // Assign to first team
            String teamColor = rainbowColors.get(colorIndex[0]);
            String teamName = "shop_highlight_" + teamColor.toLowerCase();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            team.setColor(getChatColor(teamColor)); // Always set the color!
            team.addEntry(shulker.getUniqueId().toString());

            // Start a repeating task to cycle colors
            BukkitRunnable rainbowTask = new BukkitRunnable() {
                @Override
                public void run() {
                    // Remove from all highlight teams
                    for (Team t : scoreboard.getTeams()) {
                        if (t.getName().startsWith("shop_highlight_")) {
                            t.removeEntry(shulker.getUniqueId().toString());
                        }
                    }
                    // Add to next color team
                    colorIndex[0] = (colorIndex[0] + 1) % rainbowColors.size();
                    String nextColor = rainbowColors.get(colorIndex[0]);
                    String nextTeamName = "shop_highlight_" + nextColor.toLowerCase();
                    Team nextTeam = scoreboard.getTeam(nextTeamName);
                    if (nextTeam == null) {
                        nextTeam = scoreboard.registerNewTeam(nextTeamName);
                    }
                    nextTeam.setColor(getChatColor(nextColor)); // Always set the color!
                    nextTeam.addEntry(shulker.getUniqueId().toString());
                }
            };
            // Run every 10 ticks (0.5s)
            rainbowTask.runTaskTimer(plugin, 10, 10);

            // Remove the shulker after the specified duration and clean up the team entry and task
            new BukkitRunnable() {
                @Override
                public void run() {
                    rainbowTask.cancel();
                    if (!shulker.isDead()) {
                        shulker.remove();
                    }
                    for (Team t : scoreboard.getTeams()) {
                        t.removeEntry(shulker.getUniqueId().toString());
                    }
                    Set<UUID> set = chunkShulkerMap.get(chunkKey);
                    if (set != null) {
                        set.remove(shulker.getUniqueId());
                        if (set.isEmpty()) chunkShulkerMap.remove(chunkKey);
                    }
                }
            }.runTaskLater(plugin, durationTicks);

        } else {
            // Normal color logic
            String teamColor = getSupportedColour(colour);
            String teamName = "shop_highlight_" + teamColor.toLowerCase();
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            team.setColor(getChatColor(teamColor)); // Always set the color!
            final Team finalTeam = team;
            final UUID shulkerUUID = shulker.getUniqueId();

            finalTeam.addEntry(shulkerUUID.toString());

            // Remove the shulker after the specified duration and clean up the team entry
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!shulker.isDead()) {
                        shulker.remove();
                    }
                    finalTeam.removeEntry(shulkerUUID.toString());
                    Set<UUID> set = chunkShulkerMap.get(chunkKey);
                    if (set != null) {
                        set.remove(shulkerUUID);
                        if (set.isEmpty()) chunkShulkerMap.remove(chunkKey);
                    }
                }
            }.runTaskLater(plugin, durationTicks);
        }
    }

    // Only allow supported colors, default to YELLOW
    private static String getSupportedColour(String colour) {
        String upper = colour == null ? "YELLOW" : colour.toUpperCase();
        return SUPPORTED_COLOURS.contains(upper) ? upper : "YELLOW";
    }

    private static ChatColor getChatColor(String colour) {
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
            default -> ChatColor.YELLOW;
        };
    }

    private static String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();
    }

    private static NamespacedKey getHighlightKey() {
        return new NamespacedKey(pluginInstance, PDC_KEY);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            if (team.getName().startsWith("shop_highlight_")) {
                String chunkKey = getChunkKey(event.getChunk());
                Set<UUID> shulkerSet = chunkShulkerMap.remove(chunkKey);
                if (shulkerSet != null && !shulkerSet.isEmpty()) {
                    for (Entity entity : event.getChunk().getEntities()) {
                        if (entity instanceof Shulker && shulkerSet.contains(entity.getUniqueId())) {
                            entity.remove();
                            team.removeEntry(entity.getUniqueId ().toString());
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        NamespacedKey key = getHighlightKey();
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Shulker shulker) {
                if (shulker.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                    shulker.remove();
                    removeFromAllTeams(shulker);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        cleanupInvalidTeamEntries();
    }

    public static void cleanupInvalidTeamEntries() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : scoreboard.getTeams()) {
            if (!team.getName().startsWith("shop_highlight_")) continue;
            Set<String> toRemove = new HashSet<>();
            for (String entry : team.getEntries()) {
                try {
                    UUID uuid = UUID.fromString(entry);
                    boolean found = false;
                    for (World world : Bukkit.getWorlds()) {
                        for (Entity entity : world.getEntities()) {
                            if (entity.getUniqueId().equals(uuid) && entity instanceof Shulker) {
                                found = true;
                                break;
                            }
                        }
                        if (found) break;
                    }
                    if (!found) {
                        toRemove.add(entry);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Not a UUID, probably a player name, ignore
                }
            }
            for (String entry : toRemove) {
                team.removeEntry(entry);
            }
        }
    }

    private static void removeFromAllTeams(Shulker shulker) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith("shop_highlight_")) {
                team.removeEntry(shulker.getUniqueId().toString());
            }
        }
    }
}