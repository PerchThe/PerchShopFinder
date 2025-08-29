package me.perch.shopfinder.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class ExcludedWarpsUtil {
    private static File file;
    private static YamlConfiguration config;

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]");

    private static String normalize(String s) {
        if (s == null) return "";
        return NON_ALNUM.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public static void init(Plugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("plugin cannot be null");

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder: " + dataFolder.getAbsolutePath());
        }

        file = new File(dataFolder, "excluded-warps.yml");
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    plugin.getLogger().warning("Could not create excluded-warps.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create excluded-warps.yml");
                e.printStackTrace();
            }
        }

        config = YamlConfiguration.loadConfiguration(file);

        if (!config.contains("excluded-warps")) {
            config.set("excluded-warps", new ArrayList<String>());
            save();
        }
    }

    public static Set<String> getExcludedWarps() {
        if (config == null) return new HashSet<>();
        List<String> list = config.getStringList("excluded-warps");
        Set<String> keys = new HashSet<>();
        for (String s : list) {
            keys.add(normalize(s));
        }
        return keys;
    }

    public static boolean isExcluded(String warpName) {
        if (config == null) return false;
        String key = normalize(warpName);
        return getExcludedWarps().contains(key);
    }

    public static void addExcludedWarp(String warpName) {
        if (config == null) return;
        String key = normalize(warpName);
        if (key.isEmpty()) return;

        Set<String> warps = getExcludedWarps();
        if (warps.add(key)) {
            config.set("excluded-warps", new ArrayList<>(warps));
            save();
        }
    }

    public static void removeExcludedWarp(String warpName) {
        if (config == null) return;
        String key = normalize(warpName);
        if (key.isEmpty()) return;

        Set<String> warps = getExcludedWarps();
        if (warps.remove(key)) {
            config.set("excluded-warps", new ArrayList<>(warps));
            save();
        }
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
