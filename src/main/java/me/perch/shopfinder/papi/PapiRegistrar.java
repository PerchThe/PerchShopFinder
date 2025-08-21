package me.perch.shopfinder.papi;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PapiRegistrar implements Listener {

    private final JavaPlugin plugin;
    private ShopFinderExpansion expansion;

    public PapiRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Register the expansion if PlaceholderAPI is present. Safe to call multiple times. */
    public void tryRegister() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        if (expansion != null) return; // already registered
        expansion = new ShopFinderExpansion(plugin);
        expansion.register();
    }

    /** Unregister the expansion if we had registered it. */
    public void unregister() {
        if (expansion != null) {
            try { expansion.unregister(); } catch (Throwable ignored) {}
            expansion = null;
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        if (e.getPlugin().getName().equalsIgnoreCase("PlaceholderAPI")) {
            tryRegister();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) {
        if (e.getPlugin().getName().equalsIgnoreCase("PlaceholderAPI")) {
            unregister();
        }
    }
}
