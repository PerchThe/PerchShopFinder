// ExcludedWarpsUtil.java
package me.perch.shopfinder.utils;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class ExcludedWarpsUtil {
    private static final File file = new File(Bukkit.getPluginManager().getPlugin("FindItemAddOn").getDataFolder(), "excluded-warps.yml");
    private static final YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

    public static Set<String> getExcludedWarps() {
        List<String> list = config.getStringList("excluded-warps");
        return new HashSet<>(list);
    }

    public static void addExcludedWarp(String warpName) {
        Set<String> warps = getExcludedWarps();
        warps.add(warpName.toLowerCase());
        config.set("excluded-warps", new ArrayList<>(warps));
        save();
    }

    public static void removeExcludedWarp(String warpName) {
        Set<String> warps = getExcludedWarps();
        warps.remove(warpName.toLowerCase());
        config.set("excluded-warps", new ArrayList<>(warps));
        save();
    }

    private static void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}