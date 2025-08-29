package me.perch.shopfinder.commands;

import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.models.FoundShopItemModel;
import me.perch.shopfinder.utils.HighlightColourManager;
import me.perch.shopfinder.utils.ShopHighlighter;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

public class WhereToGetCommand implements CommandExecutor {

    private static final int SEARCH_RADIUS = 100;

    private static final Map<String, PotionEffectType> FRIENDLY_POTION_EFFECTS = buildFriendlyPotionEffectMap();
    private static final Map<String, Enchantment> BOOK_NAME_TO_ENCHANTMENT = buildBookNameToEnchantmentMap();

    private static Map<String, PotionEffectType> buildFriendlyPotionEffectMap() {
        Map<String, PotionEffectType> map = new HashMap<>();
        map.put("HASTE", PotionEffectType.getByName("FAST_DIGGING"));
        map.put("MININGFATIGUE", PotionEffectType.getByName("SLOW_DIGGING"));
        map.put("STRENGTH", PotionEffectType.getByName("INCREASE_DAMAGE"));
        map.put("INSTANTHEALTH", PotionEffectType.getByName("HEAL"));
        map.put("INSTANTHARM", PotionEffectType.getByName("HARM"));
        map.put("JUMPBOOST", PotionEffectType.getByName("JUMP"));
        map.put("JUMP", PotionEffectType.getByName("JUMP"));
        map.put("REGENERATION", PotionEffectType.getByName("REGENERATION"));
        map.put("RESISTANCE", PotionEffectType.getByName("DAMAGE_RESISTANCE"));
        map.put("FIRERESISTANCE", PotionEffectType.getByName("FIRE_RESISTANCE"));
        map.put("WATERBREATHING", PotionEffectType.getByName("WATER_BREATHING"));
        map.put("NIGHTVISION", PotionEffectType.getByName("NIGHT_VISION"));
        map.put("SLOWFALLING", PotionEffectType.getByName("SLOW_FALLING"));
        map.put("BADOMEN", PotionEffectType.getByName("BAD_OMEN"));
        map.put("HEROOFTHEVILLAGE", PotionEffectType.getByName("HERO_OF_THE_VILLAGE"));
        map.put("CONDUITPOWER", PotionEffectType.getByName("CONDUIT_POWER"));
        map.put("DOLPHINSGRACE", PotionEffectType.getByName("DOLPHINS_GRACE"));
        map.put("LUCK", PotionEffectType.getByName("LUCK"));
        map.put("BADLUCK", PotionEffectType.getByName("UNLUCK"));
        map.put("ABSORPTION", PotionEffectType.getByName("ABSORPTION"));
        map.put("INVISIBILITY", PotionEffectType.getByName("INVISIBILITY"));
        map.put("POISON", PotionEffectType.getByName("POISON"));
        map.put("SLOWNESS", PotionEffectType.getByName("SLOW"));
        map.put("SWIFTNESS", PotionEffectType.getByName("SPEED"));
        map.put("WEAKNESS", PotionEffectType.getByName("WEAKNESS"));
        map.put("WITHER", PotionEffectType.getByName("WITHER"));
        map.put("LEVITATION", PotionEffectType.getByName("LEVITATION"));
        map.put("GLOW", PotionEffectType.getByName("GLOWING"));
        map.put("BLINDNESS", PotionEffectType.getByName("BLINDNESS"));
        map.put("OOZING", PotionEffectType.getByName("OOZING"));
        map.put("INFESTATION", PotionEffectType.getByName("INFESTED"));
        map.put("WEAVING", PotionEffectType.getByName("WEAVING"));
        map.put("WINDCHARGING", PotionEffectType.getByName("WIND_CHARGED"));
        map.put("TURTLEMASTER", PotionEffectType.getByName("DAMAGE_RESISTANCE"));
        map.put("NAUSEA", PotionEffectType.getByName("CONFUSION"));
        map.put("CONFUSION", PotionEffectType.getByName("CONFUSION"));
        return map;
    }

    private static Map<String, Enchantment> buildBookNameToEnchantmentMap() {
        Map<String, String> special = new HashMap<>();
        special.put("BINDING_CURSE", "CURSEOFBINDING");
        special.put("VANISHING_CURSE", "CURSEOFVANISHING");
        Map<String, Enchantment> map = new HashMap<>();
        for (Enchantment ench : Enchantment.values()) {
            String key = ench.getKey().getKey().toUpperCase();
            String bookName = special.getOrDefault(key, key.replace("_", ""));
            map.put(bookName.toLowerCase(), ench);
            if (bookName.equals("CURSEOFBINDING")) map.put("bindingcurse", ench);
            if (bookName.equals("CURSEOFVANISHING")) map.put("vanishingcurse", ench);
        }
        return map;
    }

    private static String[] remapArtAliases(String[] args) {
        if (args == null || args.length == 0) return args;
        String a0 = args[0].toLowerCase(Locale.ROOT);
        switch (a0) {
            case "art":
                return new String[]{"lore:copyright", "lore:artwork"};
            case "artmap":
                return new String[]{"lore:artwork"};
            case "mapart":
                return new String[]{"lore:copyright"};
            default:
                return args;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorTranslator.translateColorCodes("&cOnly players can use this command!"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ColorTranslator.translateColorCodes("&eUsage: &f/where <item|lore:...|enchant|effect>"));
            return true;
        }

        // Apply alias remap and work with all tokens
        args = remapArtAliases(args);
        final String[] searchArgs = Arrays.stream(args)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        if (searchArgs.length == 0) {
            player.sendMessage(ColorTranslator.translateColorCodes("&cInvalid item!"));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(FindItemAddOn.getInstance(), () -> {
            List<FoundShopItemModel> foundShops = new ArrayList<>();

            for (String singleItem : searchArgs) {
                if (singleItem.toLowerCase().startsWith("lore:")) {
                    String loreSearch = singleItem.substring(5).toLowerCase();
                    List<FoundShopItemModel> loreMatches = ((List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .fetchAllItemsFromAllShops(true, player))
                            .stream()
                            .filter(shopItem -> {
                                ItemStack item = shopItem.getItemStack();
                                if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                                    List<String> lore = item.getItemMeta().getLore();
                                    if (lore != null) {
                                        for (String line : lore) {
                                            if (line.toLowerCase().contains(loreSearch)) return true;
                                        }
                                    }
                                }
                                return false;
                            })
                            .collect(Collectors.toList());
                    foundShops.addAll(loreMatches);
                    continue;
                }

                Enchantment enchantment = getEnchantmentByName(singleItem);
                if (enchantment != null) {
                    List<FoundShopItemModel> books = ((List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .findItemBasedOnTypeFromAllShops(new ItemStack(Material.ENCHANTED_BOOK), true, player))
                            .stream()
                            .filter(shopItem -> {
                                ItemStack item = shopItem.getItemStack();
                                if (item != null && item.getType() == Material.ENCHANTED_BOOK && item.hasItemMeta()) {
                                    if (item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                                        return meta.hasStoredEnchant(enchantment);
                                    }
                                }
                                return false;
                            })
                            .collect(Collectors.toList());
                    foundShops.addAll(books);
                    continue;
                }

                PotionEffectType effect = getPotionEffectByName(singleItem);
                if (effect != null) {
                    List<Material> potionMaterials = Arrays.asList(
                            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.TIPPED_ARROW
                    );
                    for (Material mat : potionMaterials) {
                        List<FoundShopItemModel> potions = ((List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                                .findItemBasedOnTypeFromAllShops(new ItemStack(mat), true, player))
                                .stream()
                                .filter(shopItem -> {
                                    ItemStack item = shopItem.getItemStack();
                                    if (item != null && item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta meta) {
                                        try {
                                            var data = meta.getBasePotionData();
                                            if (data != null && data.getType() != null && data.getType().getEffectType() == effect) return true;
                                        } catch (Throwable ignored) {}
                                        return meta.hasCustomEffects() && meta.getCustomEffects().stream().anyMatch(e -> e.getType().equals(effect));
                                    }
                                    return false;
                                })
                                .collect(Collectors.toList());
                        foundShops.addAll(potions);
                    }
                    continue;
                }

                if (singleItem.equalsIgnoreCase("tags") || singleItem.equalsIgnoreCase("tag")) {
                    List<FoundShopItemModel> allNameTags = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .findItemBasedOnTypeFromAllShops(new ItemStack(Material.NAME_TAG), true, player);

                    List<FoundShopItemModel> renamedTags = allNameTags.stream()
                            .filter(shopItem -> {
                                ItemStack item = shopItem.getItemStack();
                                return item != null && item.getType() == Material.NAME_TAG &&
                                        item.hasItemMeta() && item.getItemMeta().hasDisplayName();
                            })
                            .collect(Collectors.toList());

                    foundShops.addAll(renamedTags);
                    continue;
                }

                if (singleItem.equalsIgnoreCase("shulker_box")) {
                    List<Material> shulkerVariants = Arrays.stream(Material.values())
                            .filter(m -> m.name().endsWith("_SHULKER_BOX"))
                            .collect(Collectors.toList());
                    if (!shulkerVariants.contains(Material.SHULKER_BOX)) {
                        shulkerVariants.add(Material.SHULKER_BOX);
                    }
                    for (Material variant : shulkerVariants) {
                        List<FoundShopItemModel> variantMatches = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                                .findItemBasedOnTypeFromAllShops(new ItemStack(variant), true, player);
                        foundShops.addAll(variantMatches);
                    }
                    continue;
                }

                Material mat = Material.getMaterial(singleItem.toUpperCase(Locale.ROOT));
                if (mat != null && mat.isItem()) {
                    List<FoundShopItemModel> foundItems = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .findItemBasedOnTypeFromAllShops(new ItemStack(mat), true, player);
                    foundShops.addAll(foundItems);
                } else {
                    List<FoundShopItemModel> byName = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .findItemBasedOnDisplayNameFromAllShops(singleItem, true, player);
                    foundShops.addAll(byName);
                }
            }

            Map<String, FoundShopItemModel> byLocKey = new HashMap<>();
            for (FoundShopItemModel m : foundShops) {
                Location l = m.getShopLocation();
                if (l == null || l.getWorld() == null) continue;
                String key = l.getWorld().getName() + ":" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
                byLocKey.putIfAbsent(key, m);
            }
            List<FoundShopItemModel> uniqueShops = new ArrayList<>(byLocKey.values());

            final String queryEcho = String.join(" ", searchArgs);

            Bukkit.getScheduler().runTask(FindItemAddOn.getInstance(), () -> {
                int highlighted = 0;
                String colour = HighlightColourManager.getColour(player.getUniqueId());
                Location playerLoc = player.getLocation();
                for (FoundShopItemModel shop : uniqueShops) {
                    Location shopLoc = shop.getShopLocation();
                    if (shopLoc != null && shopLoc.getWorld() != null &&
                            shopLoc.getWorld().equals(playerLoc.getWorld()) &&
                            shopLoc.distance(playerLoc) <= SEARCH_RADIUS) {
                        ShopHighlighter.highlightShop(shopLoc, 600, FindItemAddOn.getInstance(), colour);
                        highlighted++;
                    }
                }
                player.sendMessage(ColorTranslator.translateColorCodes(
                        "&8» &fHighlighted &e" + highlighted + " &fnearby shop(s) matching &e" + queryEcho + "&a."
                ));
            });
        });

        return true;
    }

    private static Enchantment getEnchantmentByName(String name) {
        String key = name.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        if (BOOK_NAME_TO_ENCHANTMENT.containsKey(key)) return BOOK_NAME_TO_ENCHANTMENT.get(key);
        for (Enchantment ench : Enchantment.values()) {
            String enchKey = ench.getKey().getKey().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
            if (enchKey.equals(key) || ench.getName().equalsIgnoreCase(name)) return ench;
        }
        return null;
    }

    private static PotionEffectType getPotionEffectByName(String name) {
        String key = name.toUpperCase(Locale.ROOT).replace("_", "").replace(" ", "");
        if (FRIENDLY_POTION_EFFECTS.containsKey(key)) return FRIENDLY_POTION_EFFECTS.get(key);
        for (PotionEffectType effect : PotionEffectType.values()) {
            if (effect == null) continue;
            String effectKey = effect.getName().toUpperCase(Locale.ROOT).replace("_", "").replace(" ", "");
            if (effectKey.equals(key)) return effect;
        }
        return null;
    }
}
