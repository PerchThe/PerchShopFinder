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

    private static final String TEAM_NAME = "shop_highlight_orange";
    private static final String PDC_KEY = "shop_highlight";
    private static final String HIGHLIGHT_SHULKER_NAME = "PerchShopFinderHighlight";
    // Map<ChunkKey, Set<Shulker UUID>>
    private static final Map<String, Set<UUID>> chunkShulkerMap = new HashMap<>();
    private static Plugin pluginInstance;

    public static void init(Plugin plugin) {
        pluginInstance = plugin;
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ShopHighlighter(), plugin);
        cleanupInvalidTeamEntries();
    }

    public static void highlightShop(Location location, int durationTicks, Plugin plugin) {
        Set<Location> chestLocations = getChestLocations(location);

        for (Location chestLoc : chestLocations) {
            highlightBlock(chestLoc, durationTicks, plugin);
        }
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

    private static void highlightBlock(Location location, int durationTicks, Plugin plugin) {
        // Remove any existing invisible glowing shulkers at this location
        location.getWorld().getNearbyEntities(location, 0.5, 1, 0.5).stream()
                .filter(e -> {
                    if (e instanceof Shulker) {
                        Shulker shulker = (Shulker) e;
                        return shulker.isGlowing() && shulker.isInvisible();
                    }
                    return false;
                })
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
            // Tag with persistent data
            s.getPersistentDataContainer().set(getHighlightKey(), PersistentDataType.BYTE, (byte) 1);
            // Set custom name for GPFlags bypass
            s.setCustomName(HIGHLIGHT_SHULKER_NAME);
        });

        // Track this shulker by chunk
        String chunkKey = getChunkKey(shulker.getLocation().getChunk());
        chunkShulkerMap.computeIfAbsent(chunkKey, k -> new HashSet<>()).add(shulker.getUniqueId());

        // Assign the shulker to an orange team for orange glowing
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
            team.setColor(ChatColor.GOLD);
        }
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
                // Remove from tracking
                String chunkKey = getChunkKey(shulker.getLocation().getChunk());
                Set<UUID> set = chunkShulkerMap.get(chunkKey);
                if (set != null) {
                    set.remove(shulkerUUID);
                    if (set.isEmpty()) chunkShulkerMap.remove(chunkKey);
                }
            }
        }.runTaskLater(plugin, durationTicks);
    }

    private static String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();
    }

    private static NamespacedKey getHighlightKey() {
        return new NamespacedKey(pluginInstance, PDC_KEY);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        String chunkKey = getChunkKey(event.getChunk());
        Set<UUID> shulkerSet = chunkShulkerMap.remove(chunkKey);
        if (shulkerSet != null && !shulkerSet.isEmpty()) {
            for (Entity entity : event.getChunk().getEntities()) {
                if (entity instanceof Shulker && shulkerSet.contains(entity.getUniqueId())) {
                    entity.remove();
                    removeFromTeam((Shulker) entity);
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
                    removeFromTeam(shulker);
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
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) return;

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

    private static void removeFromTeam(Shulker shulker) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team != null) {
            team.removeEntry(shulker.getUniqueId().toString());
        }
    }
}