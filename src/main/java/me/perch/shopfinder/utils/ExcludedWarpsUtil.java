package me.perch.shopfinder.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class ExcludedWarpsUtil {
    private static File file;
    private static YamlConfiguration config;

    // Call this ONCE in your plugin's onEnable()
    public static void init(Plugin plugin) {
        file = new File(plugin.getDataFolder(), "excluded-warps.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        // Ensure the list exists
        if (!config.contains("excluded-warps")) {
            config.set("excluded-warps", new ArrayList<String>());
            save();
        }
    }

    public static Set<String> getExcludedWarps() {
        if (config == null) return new HashSet<>(); // Not initialized yet
        List<String> list = config.getStringList("excluded-warps");
        return new HashSet<>(list);
    }

    public static void addExcludedWarp(String warpName) {
        if (config == null) return; // Not initialized yet
        Set<String> warps = getExcludedWarps();
        warps.add(warpName.toLowerCase());
        config.set("excluded-warps", new ArrayList<>(warps));
        save();
    }

    public static void removeExcludedWarp(String warpName) {
        if (config == null) return; // Not initialized yet
        Set<String> warps = getExcludedWarps();
        warps.remove(warpName.toLowerCase());
        config.set("excluded-warps", new ArrayList<>(warps));
        save();
    }

    private static void save() {
        if (config == null || file == null) return;
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}