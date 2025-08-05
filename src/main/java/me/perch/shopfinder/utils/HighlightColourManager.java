package me.perch.shopfinder.utils;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HighlightColourManager {
    private static final Map<UUID, String> playerColours = new HashMap<>();
    private static File file;
    private static FileConfiguration config;

    public static void init(File dataFolder) {
        file = new File(dataFolder, "highlight_colours.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Bukkit.getLogger().warning("Could not create highlight_colours.yml!");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public static void setColour(UUID uuid, String colour) {
        playerColours.put(uuid, colour);
        config.set(uuid.toString(), colour);
        save();
    }

    public static String getColour(UUID uuid) {
        return playerColours.getOrDefault(uuid, "ORANGE");
    }

    public static void load() {
        playerColours.clear();
        for (String key : config.getKeys(false)) {
            playerColours.put(UUID.fromString(key), config.getString(key, "ORANGE"));
        }
    }

    public static void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().warning("Could not save highlight_colours.yml!");
        }
    }
}