package me.perch.shopfinder.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.perch.shopfinder.utils.HighlightColourManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;

public class ShopFinderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;

    // Legacy colour codes
    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("BLACK", "§0"),
            Map.entry("DARK_BLUE", "§1"),
            Map.entry("DARK_GREEN", "§2"),
            Map.entry("DARK_AQUA", "§3"),
            Map.entry("DARK_RED", "§4"),
            Map.entry("DARK_PURPLE", "§5"),
            Map.entry("GOLD", "§6"),
            Map.entry("GRAY", "§7"),
            Map.entry("DARK_GRAY", "§8"),
            Map.entry("BLUE", "§9"),
            Map.entry("GREEN", "§a"),
            Map.entry("AQUA", "§b"),
            Map.entry("RED", "§c"),
            Map.entry("LIGHT_PURPLE", "§d"),
            Map.entry("YELLOW", "§e"),
            Map.entry("WHITE", "§f")
    );

    public ShopFinderExpansion(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        // %perchshopfinder_<...>%
        return "perchshopfinder";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        String stored = HighlightColourManager.getColour(player.getUniqueId());
        String norm = normalize(stored);                 // e.g. "DARK_PURPLE", "GOLD", "RAINBOW"
        String key  = params == null ? "" : params.toLowerCase(Locale.ROOT);

        switch (key) {
            // %perchshopfinder_colour_raw%
            case "colour_raw":
            case "color_raw":
                return toMiniName(norm);                 // e.g. "dark_purple", "gold", "rainbow"

            // %perchshopfinder_colour%
            case "colour":
            case "color":
                if (isRainbow(norm)) {
                    // Make the word "Rainbow" rainbow-coloured, then reset
                    return rainbowize("Rainbow") + "§r";
                }
                String code = LEGACY.getOrDefault(norm, "§e"); // default YELLOW
                return code + toDisplayName(norm) + "§r";

            default:
                return "";
        }
    }

    private static boolean isRainbow(String colour) {
        return colour != null && colour.equalsIgnoreCase("RAINBOW");
    }

    // Handle your manager's "ORANGE" default by mapping to GOLD (Bukkit has no ORANGE ChatColor)
    private static String normalize(String colour) {
        if (colour == null) return "YELLOW";
        String up = colour.toUpperCase(Locale.ROOT);
        if (up.equals("ORANGE")) return "GOLD";
        return up;
    }

    // "DARK_PURPLE" -> "dark_purple"
    private static String toMiniName(String colour) {
        return colour == null ? "yellow" : colour.toLowerCase(Locale.ROOT);
    }

    // "DARK_PURPLE" -> "Dark Purple"
    private static String toDisplayName(String colour) {
        if (colour == null) return "Yellow";
        String[] parts = colour.toLowerCase(Locale.ROOT).split("_");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            b.append(Character.toUpperCase(parts[i].charAt(0)))
                    .append(parts[i].substring(1));
            if (i < parts.length - 1) b.append(' ');
        }
        return b.toString();
    }

    // Colour-cycles a word with legacy codes: §c§6§e§a§b§9§d ...
    private static String rainbowize(String text) {
        String[] codes = {"§c","§6","§e","§a","§b","§9","§d"};
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (char ch : text.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                sb.append(ch);
            } else {
                sb.append(codes[i % codes.length]).append(ch);
                i++;
            }
        }
        return sb.toString();
    }
}
