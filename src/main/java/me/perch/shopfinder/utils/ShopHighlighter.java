package me.perch.shopfinder.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class ShopHighlighter implements Listener {

    private static final String PDC_KEY = "shop_highlight";
    private static final String HIGHLIGHT_SHULKER_NAME = "PerchShopFinderHighlight";
    private static final String TEAM_PREFIX = "shop_highlight_";

    // Track active shulkers by UUID to clean up teams efficiently
    // Key: Shulker UUID, Value: Team Name
    private static final Map<UUID, String> activeShulkerTeams = new HashMap<>();

    // Map Chunk Key -> Set of Shulker UUIDs for O(1) chunk unloading
    private static final Map<Long, Set<UUID>> chunkShulkerMap = new HashMap<>();

    private static Plugin pluginInstance;
    private static NamespacedKey namespacedKey;

    private static final Set<String> SUPPORTED_COLOURS = Set.of(
            "BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED", "DARK_PURPLE",
            "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA", "RED", "LIGHT_PURPLE",
            "YELLOW", "WHITE", "RAINBOW"
    );

    public static void init(Plugin plugin) {
        pluginInstance = plugin;
        namespacedKey = new NamespacedKey(pluginInstance, PDC_KEY);
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ShopHighlighter(), plugin);

        // Run simple cleanup once on startup (clears scoreboard teams, doesn't scan world)
        cleanupOldTeams();
    }

    /**
     * Clears any leftover teams from a previous server session.
     * Does NOT scan the world for entities (which causes lag).
     */
    public static void cleanupOldTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }

    public static void highlightShop(Location location, int durationTicks, Plugin plugin, String colour) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<Location> chestLocations = getChestLocations(location);
                for (Location chestLoc : chestLocations) {
                    spawnHighlight(chestLoc, durationTicks, colour);
                }
            }
        }.runTask(plugin);
    }

    public static void highlightShop(Location location, int durationTicks, Plugin plugin) {
        highlightShop(location, durationTicks, plugin, "YELLOW");
    }

    private static Set<Location> getChestLocations(Location location) {
        Set<Location> locations = new HashSet<>(2);
        Block block = location.getBlock();

        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            locations.add(location);
            return locations;
        }

        BlockState state = block.getState();
        if (state instanceof Chest chest) {
            if (chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
                if (doubleChest.getLeftSide() instanceof Chest left) locations.add(left.getLocation());
                if (doubleChest.getRightSide() instanceof Chest right) locations.add(right.getLocation());
            } else {
                locations.add(block.getLocation());
            }
        } else {
            locations.add(location);
        }
        return locations;
    }

    private static void spawnHighlight(Location location, int durationTicks, String colour) {
        if (location.getWorld() == null) return;

        // Clean existing highlights at this specific block before spawning new one
        location.getWorld().getNearbyEntities(location, 0.5, 0.5, 0.5).stream()
                .filter(e -> e instanceof Shulker)
                .filter(e -> e.getPersistentDataContainer().has(namespacedKey, PersistentDataType.BYTE))
                .forEach(Entity::remove);

        Shulker shulker = location.getWorld().spawn(location.clone().add(0.5, 0, 0.5), Shulker.class, s -> {
            s.setAI(false);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setInvisible(true);
            s.setGlowing(true);
            s.setGravity(false);
            s.setCustomNameVisible(false);
            s.getPersistentDataContainer().set(namespacedKey, PersistentDataType.BYTE, (byte) 1);
            s.setCustomName(HIGHLIGHT_SHULKER_NAME);
        });

        long chunkKey = getChunkKey(shulker.getLocation().getChunk());
        chunkShulkerMap.computeIfAbsent(chunkKey, k -> new HashSet<>()).add(shulker.getUniqueId());

        if ("RAINBOW".equalsIgnoreCase(colour)) {
            handleRainbow(shulker, durationTicks, chunkKey);
        } else {
            handleSingleColor(shulker, colour, durationTicks, chunkKey);
        }
    }

    private static void handleSingleColor(Shulker shulker, String colour, int duration, long chunkKey) {
        String teamColor = getSupportedColour(colour);
        String teamName = TEAM_PREFIX + teamColor.toLowerCase();

        Team team = getOrCreateTeam(teamName, getChatColor(teamColor));
        String entry = shulker.getUniqueId().toString();
        team.addEntry(entry);

        activeShulkerTeams.put(shulker.getUniqueId(), teamName);

        new BukkitRunnable() {
            @Override
            public void run() {
                removeShulkerSafe(shulker, chunkKey);
            }
        }.runTaskLater(pluginInstance, duration);
    }

    private static void handleRainbow(Shulker shulker, int duration, long chunkKey) {
        List<String> rainbowColors = List.of("RED", "GOLD", "YELLOW", "GREEN", "AQUA", "BLUE", "LIGHT_PURPLE");
        final int[] index = {0};
        final UUID uuid = shulker.getUniqueId();
        final String entry = uuid.toString();

        BukkitRunnable rainbowTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shulker.isDead() || !shulker.isValid()) {
                    this.cancel();
                    return;
                }

                if (index[0] > 0) {
                    Team prev = getOrCreateTeam(TEAM_PREFIX + rainbowColors.get(index[0] - 1).toLowerCase(), null);
                    prev.removeEntry(entry);
                } else {
                    Team prev = getOrCreateTeam(TEAM_PREFIX + rainbowColors.get(rainbowColors.size() - 1).toLowerCase(), null);
                    prev.removeEntry(entry);
                }

                String color = rainbowColors.get(index[0]);
                Team t = getOrCreateTeam(TEAM_PREFIX + color.toLowerCase(), getChatColor(color));
                t.addEntry(entry);
                activeShulkerTeams.put(uuid, t.getName());

                index[0] = (index[0] + 1) % rainbowColors.size();
            }
        };
        rainbowTask.runTaskTimer(pluginInstance, 0, 10);

        new BukkitRunnable() {
            @Override
            public void run() {
                rainbowTask.cancel();
                removeShulkerSafe(shulker, chunkKey);
            }
        }.runTaskLater(pluginInstance, duration);
    }

    private static void removeShulkerSafe(Shulker shulker, long chunkKey) {
        if (shulker != null && !shulker.isDead()) {
            shulker.remove();
        }

        UUID uuid = shulker.getUniqueId();
        Set<UUID> chunkSet = chunkShulkerMap.get(chunkKey);
        if (chunkSet != null) {
            chunkSet.remove(uuid);
            if (chunkSet.isEmpty()) chunkShulkerMap.remove(chunkKey);
        }

        String teamName = activeShulkerTeams.remove(uuid);
        if (teamName != null) {
            Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
            if (t != null) t.removeEntry(uuid.toString());
        }
    }

    private static Team getOrCreateTeam(String name, ChatColor color) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(name);
        if (t == null) {
            t = sb.registerNewTeam(name);
        }
        if (color != null && t.getColor() != color) {
            t.setColor(color);
        }
        return t;
    }

    private static long getChunkKey(Chunk chunk) {
        return (long) chunk.getX() << 32 | (chunk.getZ() & 0xFFFFFFFFL);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        long key = getChunkKey(event.getChunk());
        Set<UUID> shulkersInChunk = chunkShulkerMap.remove(key);

        if (shulkersInChunk != null) {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            for (UUID uuid : shulkersInChunk) {
                String teamName = activeShulkerTeams.remove(uuid);
                if (teamName != null) {
                    Team t = sb.getTeam(teamName);
                    if (t != null) t.removeEntry(uuid.toString());
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Automatically clean up any persistent highlight shulkers that shouldn't be there
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Shulker) {
                if (entity.getPersistentDataContainer().has(namespacedKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    private static String getSupportedColour(String colour) {
        String upper = colour == null ? "YELLOW" : colour.toUpperCase();
        return SUPPORTED_COLOURS.contains(upper) ? upper : "YELLOW";
    }

    private static ChatColor getChatColor(String colour) {
        try {
            return ChatColor.valueOf(colour.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatColor.YELLOW;
        }
    }
}