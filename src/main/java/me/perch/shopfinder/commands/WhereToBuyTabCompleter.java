package me.perch.shopfinder.commands;

import me.perch.shopfinder.FindItemAddOn;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

public class WhereToBuyTabCompleter implements TabCompleter {
    private final List<String> itemsList;
    private final List<String> enchantBookNames;
    private final List<String> potionEffectNames;

    // --- Blocked items for tab completion (add more as needed) ---
    private static final Set<String> BLOCKED_TAB_ITEMS = Set.of(
            // ... (same as before, omitted for brevity) ...
            "PETRIFIED_OAK_SLAB"
    );

    // --- Friendly name map for potion effects and potion types (ALL UPPERCASE) ---
    private static final Map<String, String> FRIENDLY_POTION_EFFECT_NAMES = buildFriendlyPotionEffectNameMap();

    private static Map<String, String> buildFriendlyPotionEffectNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put("HASTE", "FAST_DIGGING");
        map.put("MININGFATIGUE", "SLOW_DIGGING");
        map.put("STRENGTH", "INCREASE_DAMAGE");
        map.put("INSTANTHEALTH", "HEAL");
        map.put("HEAL", "HEAL");
        map.put("INSTANTHARM", "HARM");
        map.put("HARM", "HARM");
        map.put("JUMPBOOST", "JUMP");
        map.put("JUMP", "JUMP");
        map.put("REGENERATION", "REGENERATION");
        map.put("REGEN", "REGENERATION");
        map.put("RESISTANCE", "DAMAGE_RESISTANCE");
        map.put("DAMAGERESISTANCE", "DAMAGE_RESISTANCE");
        map.put("FIRERESISTANCE", "FIRE_RESISTANCE");
        map.put("WATERBREATHING", "WATER_BREATHING");
        map.put("NIGHTVISION", "NIGHT_VISION");
        map.put("SLOWFALLING", "SLOW_FALLING");
        map.put("BADOMEN", "BAD_OMEN");
        map.put("HEROOFTHEVILLAGE", "HERO_OF_THE_VILLAGE");
        map.put("CONDUITPOWER", "CONDUIT_POWER");
        map.put("DOLPHINSGRACE", "DOLPHINS_GRACE");
        map.put("LUCK", "LUCK");
        map.put("BADLUCK", "UNLUCK");
        map.put("ABSORPTION", "ABSORPTION");
        map.put("INVISIBILITY", "INVISIBILITY");
        map.put("POISON", "POISON");
        map.put("SLOWNESS", "SLOW");
        map.put("SLOW", "SLOW");
        map.put("SPEED", "SPEED");
        map.put("WEAKNESS", "WEAKNESS");
        map.put("WITHER", "WITHER");
        map.put("LEVITATION", "LEVITATION");
        map.put("GLOW", "GLOWING");
        map.put("BLINDNESS", "BLINDNESS");
        // 1.21+ and aliases
        map.put("TURTLEMASTER", "TURTLE_MASTER"); // PotionType
        map.put("NAUSEA", "CONFUSION");
        map.put("CONFUSION", "CONFUSION");
        map.put("OOZING", "OOZING");
        map.put("INFESTATION", "INFESTED");
        map.put("WEAVING", "WEAVING");
        map.put("WINDCHARGING", "WIND_CHARGED");
        return map;
    }

    public WhereToBuyTabCompleter() {
        itemsList = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (!FindItemAddOn.getConfigProvider().getBlacklistedMaterials().contains(mat)) {
                itemsList.add(mat.name());
            }
        }
        // Build enchantment book names
        enchantBookNames = buildEnchantmentBookNames();

        // Build potion effect names (friendly + Bukkit names, uppercase, no spaces)
        potionEffectNames = buildPotionEffectNames();
    }

    private List<String> buildEnchantmentBookNames() {
        Map<String, String> special = new HashMap<>();
        special.put("PROTECTION", "PROTECTION");
        special.put("FIRE_PROTECTION", "FIREPROTECTION");
        special.put("FEATHER_FALLING", "FEATHERFALLING");
        special.put("BLAST_PROTECTION", "BLASTPROTECTION");
        special.put("PROJECTILE_PROTECTION", "PROJECTILEPROTECTION");
        special.put("RESPIRATION", "RESPIRATION");
        special.put("AQUA_AFFINITY", "AQUAAFFINITY");
        special.put("THORNS", "THORNS");
        special.put("DEPTH_STRIDER", "DEPTHSTRIDER");
        special.put("FROST_WALKER", "FROSTWALKER");
        special.put("BINDING_CURSE", "CURSEOFBINDING");
        special.put("SOUL_SPEED", "SOULSPEED");
        special.put("SWIFT_SNEAK", "SWIFTSNEAK");
        special.put("SHARPNESS", "SHARPNESS");
        special.put("SMITE", "SMITE");
        special.put("BANE_OF_ARTHROPODS", "BANEOFARTHROPODS");
        special.put("KNOCKBACK", "KNOCKBACK");
        special.put("FIRE_ASPECT", "FIREASPECT");
        special.put("LOOTING", "LOOTING");
        special.put("SWEEPING_EDGE", "SWEEPINGEDGE");
        special.put("EFFICIENCY", "EFFICIENCY");
        special.put("SILK_TOUCH", "SILKTOUCH");
        special.put("UNBREAKING", "UNBREAKING");
        special.put("FORTUNE", "FORTUNE");
        special.put("POWER", "POWER");
        special.put("PUNCH", "PUNCH");
        special.put("FLAME", "FLAME");
        special.put("INFINITY", "INFINITY");
        special.put("LUCK_OF_THE_SEA", "LUCKOFTHESSEA");
        special.put("LURE", "LURE");
        special.put("LOYALTY", "LOYALTY");
        special.put("IMPALING", "IMPALING");
        special.put("RIPTIDE", "RIPTIDE");
        special.put("CHANNELING", "CHANNELING");
        special.put("MULTISHOT", "MULTISHOT");
        special.put("PIERCING", "PIERCING");
        special.put("QUICK_CHARGE", "QUICKCHARGE");
        special.put("MENDING", "MENDING");
        special.put("VANISHING_CURSE", "CURSEOFVANISHING");

        Set<String> names = new LinkedHashSet<>();
        for (Enchantment ench : Enchantment.values()) {
            String key = ench.getKey().getKey().toUpperCase();
            String bookName = special.getOrDefault(key, key.replace("_", ""));
            names.add(bookName);
        }
        return new ArrayList<>(names);
    }

    private List<String> buildPotionEffectNames() {
        Set<String> names = new LinkedHashSet<>();
        // Add all friendly names (aliases and potion types)
        names.addAll(FRIENDLY_POTION_EFFECT_NAMES.keySet());
        // Add all Bukkit effect names (uppercased, no spaces/underscores)
        for (PotionEffectType effect : PotionEffectType.values()) {
            if (effect == null) continue;
            String effectName = effect.getName().toUpperCase().replace("_", "").replace(" ", "");
            names.add(effectName);
        }
        // Add potion types (for things like TURTLE_MASTER)
        try {
            Class<?> potionTypeClass = Class.forName("org.bukkit.potion.PotionType");
            for (Object type : (Object[]) potionTypeClass.getMethod("values").invoke(null)) {
                String typeName = type.toString().toUpperCase().replace("_", "");
                names.add(typeName);
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(names);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return Collections.emptyList();

        String toComplete = args[args.length - 1].toLowerCase();
        List<String> completions = new ArrayList<>();

        // Custom completions
        if ("inv".startsWith(toComplete)) completions.add("inv");
        if ("hand".startsWith(toComplete)) completions.add("hand");
        if ("safarinet".startsWith(toComplete)) completions.add("safarinet");
        if ("voucher".startsWith(toComplete)) completions.add("voucher");
        if ("unbreakable".startsWith(toComplete)) completions.add("unbreakable");
        if ("key".startsWith(toComplete)) completions.add("key");
        if ("claimblocks".startsWith(toComplete)) completions.add("claim");
        if ("playtime".startsWith(toComplete)) completions.add("playtime");
        if ("tags".startsWith(toComplete)) completions.add("tags");
        if ("tag".startsWith(toComplete)) completions.add("tag");
        if ("*".startsWith(toComplete)) completions.add("*");

        // Enchantment book names
        completions.addAll(enchantBookNames.stream()
                .filter(e -> e.toLowerCase().startsWith(toComplete))
                .collect(Collectors.toList()));

        // Potion effect names
        completions.addAll(potionEffectNames.stream()
                .filter(e -> e.toLowerCase().startsWith(toComplete))
                .collect(Collectors.toList()));

        // Item names, skipping blocked and already added
        completions.addAll(itemsList.stream()
                .filter(item -> item.toLowerCase().startsWith(toComplete))
                .filter(item -> !BLOCKED_TAB_ITEMS.contains(item.toUpperCase()))
                .filter(item -> !completions.contains(item))
                .collect(Collectors.toList()));

        return completions;
    }
}