package me.perch.shopfinder.commands;

import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.handlers.command.CmdExecutorHandler;
import me.perch.shopfinder.models.FoundShopItemModel;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

public class WhereToSellCommand implements CommandExecutor {

    private final CmdExecutorHandler cmdExecutor;
    private final String sellCommand;

    // --- Friendly name map for potion effects (ALL UPPERCASE) ---
    private static final Map<String, PotionEffectType> FRIENDLY_POTION_EFFECTS = buildFriendlyPotionEffectMap();

    // --- Book name to Enchantment map for robust matching ---
    private static final Map<String, Enchantment> BOOK_NAME_TO_ENCHANTMENT = buildBookNameToEnchantmentMap();

    private static Map<String, PotionEffectType> buildFriendlyPotionEffectMap() {
        Map<String, PotionEffectType> map = new HashMap<>();
        map.put("HASTE", PotionEffectType.getByName("FAST_DIGGING"));
        map.put("MININGFATIGUE", PotionEffectType.getByName("SLOW_DIGGING"));
        map.put("STRENGTH", PotionEffectType.getByName("INCREASE_DAMAGE"));
        map.put("INSTANTHEALTH", PotionEffectType.getByName("HEAL"));
        map.put("INSTANTHARM", PotionEffectType.getByName("HARM"));
        map.put("HARM", PotionEffectType.getByName("HARM"));
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
        map.put("SPEED", PotionEffectType.getByName("SPEED"));
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

    // --- Book name mapping for enchantments ---
    private static Map<String, Enchantment> buildBookNameToEnchantmentMap() {
        Map<String, String> special = new HashMap<>();
        special.put("BINDING_CURSE", "CURSEOFBINDING");
        special.put("VANISHING_CURSE", "CURSEOFVANISHING");
        // Add more special cases as needed

        Map<String, Enchantment> map = new HashMap<>();
        for (Enchantment ench : Enchantment.values()) {
            String key = ench.getKey().getKey().toUpperCase();
            String bookName = special.getOrDefault(key, key.replace("_", ""));
            map.put(bookName.toLowerCase(), ench);
            // Add aliases for curse books
            if (bookName.equals("CURSEOFBINDING")) map.put("bindingcurse", ench);
            if (bookName.equals("CURSEOFVANISHING")) map.put("vanishingcurse", ench);
        }
        return map;
    }

    public WhereToSellCommand() {
        this.cmdExecutor = new CmdExecutorHandler();
        if (StringUtils.isEmpty(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE)
                || StringUtils.containsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE, " ")) {
            this.sellCommand = "TO_SELL";
        } else {
            this.sellCommand = FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (player == null) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (args.length == 0) {
            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                player.sendMessage(ColorTranslator.translateColorCodes("&cInvalid item, redirecting to menu."));
                player.performCommand("wtsmenu");
            });
            return true;
        }

        String firstArg = args[0];
        if (firstArg.equalsIgnoreCase("inv")) {
            cmdExecutor.handleShopSearchForInventory(sellCommand, player);
            return true;
        }

        String[] searchArgs = args.clone();
        if (firstArg.equalsIgnoreCase("hand")) {
            Material handMat = player.getInventory().getItemInMainHand().getType();
            if (handMat == Material.AIR) {
                sender.sendMessage(ColorTranslator.translateColorCodes(
                        FindItemAddOn.getConfigProvider().PLUGIN_PREFIX + "&cYou are not holding any item!"));
                return true;
            }
            searchArgs[0] = handMat.name();
        }

        if (firstArg.equalsIgnoreCase("unbreakable")) {
            cmdExecutor.handleShopSearchForUnbreakable(sellCommand, sender);
            return true;
        }

        if (firstArg.equals("*")) {
            Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                boolean isSelling = sellCommand.equalsIgnoreCase("TO_SELL") ||
                        sellCommand.equalsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE);

                List<FoundShopItemModel> allItems = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                        .fetchAllItemsFromAllShops(!isSelling, player);

                Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                    if (allItems.isEmpty()) {
                        player.sendMessage(ColorTranslator.translateColorCodes("&cNo items found to sell."));
                        player.performCommand("wtsmenu");
                    } else {
                        allItems.sort(
                                Comparator.comparing((FoundShopItemModel m) -> m.getItem().getType().name())
                                        .thenComparing(Comparator.comparingDouble(FoundShopItemModel::getShopPrice).reversed())
                        );
                        cmdExecutor.openShopMenuDescending(player, allItems, false, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, "wtsmenu");
                    }
                });
            });
            return true;
        }

        boolean isSelling = sellCommand.equalsIgnoreCase("TO_SELL") ||
                sellCommand.equalsIgnoreCase(FindItemAddOn.getConfigProvider().FIND_ITEM_TO_SELL_AUTOCOMPLETE);

        Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            ShopSearchResult result = new ShopSearchResult();

            for (String singleItem : searchArgs) {
                singleItem = singleItem.trim();
                if (singleItem.isEmpty()) continue;

                if (singleItem.toLowerCase().startsWith("lore:")) {
                    result.anyValid = true;
                    String loreSearch = singleItem.substring(5).toLowerCase();
                    List<FoundShopItemModel> loreMatches = ((List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .fetchAllItemsFromAllShops(!isSelling, player))
                            .stream()
                            .filter(shopItem -> {
                                ItemStack item = shopItem.getItemStack();
                                if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
                                    List<String> lore = item.getItemMeta().getLore();
                                    if (lore != null) {
                                        for (String line : lore) {
                                            if (line.toLowerCase().contains(loreSearch)) {
                                                return true;
                                            }
                                        }
                                    }
                                }
                                return false;
                            })
                            .collect(Collectors.toList());
                    result.allResults.addAll(loreMatches);
                    continue;
                }

                Enchantment enchantment = getEnchantmentByName(singleItem);
                if (enchantment != null) {
                    result.anyValid = true;
                    List<FoundShopItemModel> books = ((List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .findItemBasedOnTypeFromAllShops(new ItemStack(Material.ENCHANTED_BOOK), !isSelling, player))
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
                    result.allResults.addAll(books);
                    continue;
                }

                PotionEffectType effect = getPotionEffectByName(singleItem);
                if (effect != null) {
                    result.anyValid = true;
                    List<Material> potionMaterials = Arrays.asList(
                            Material.POTION,
                            Material.SPLASH_POTION,
                            Material.LINGERING_POTION,
                            Material.TIPPED_ARROW
                    );
                    for (Material mat : potionMaterials) {
                        List<FoundShopItemModel> potions = ((List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                                .findItemBasedOnTypeFromAllShops(new ItemStack(mat), !isSelling, player))
                                .stream()
                                .filter(shopItem -> {
                                    ItemStack item = shopItem.getItemStack();
                                    if (item != null && item.hasItemMeta() && item.getItemMeta() instanceof PotionMeta meta) {
                                        try {
                                            var data = meta.getBasePotionData();
                                            if (data != null && data.getType() != null && data.getType().getEffectType() == effect) {
                                                return true;
                                            }
                                        } catch (Throwable ignored) {}
                                        return meta.hasCustomEffects() && meta.getCustomEffects().stream().anyMatch(e -> e.getType().equals(effect));
                                    }
                                    return false;
                                })
                                .collect(Collectors.toList());
                        result.allResults.addAll(potions);
                    }
                    continue;
                }

                // --- Special case: /wts tags or /wts tag shows only renamed name tags ---
                if (singleItem.equalsIgnoreCase("tags") || singleItem.equalsIgnoreCase("tag")) {
                    result.anyValid = true;
                    List<FoundShopItemModel> allNameTags = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .findItemBasedOnTypeFromAllShops(new ItemStack(Material.NAME_TAG), !isSelling, player);

                    List<FoundShopItemModel> renamedTags = allNameTags.stream()
                            .filter(shopItem -> {
                                ItemStack item = shopItem.getItemStack();
                                return item != null && item.getType() == Material.NAME_TAG && item.hasItemMeta() && item.getItemMeta().hasDisplayName();
                            })
                            .collect(Collectors.toList());

                    result.allResults.addAll(renamedTags);
                    continue;
                }

                if (singleItem.equalsIgnoreCase("shulker_box")) {
                    result.anyValid = true;
                    List<Material> shulkerVariants = Arrays.stream(Material.values())
                            .filter(m -> m.name().endsWith("_SHULKER_BOX"))
                            .collect(Collectors.toList());

                    if (!shulkerVariants.contains(Material.SHULKER_BOX)) {
                        shulkerVariants.add(Material.SHULKER_BOX);
                    }

                    for (Material variant : shulkerVariants) {
                        List<FoundShopItemModel> variantMatches = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                                .findItemBasedOnTypeFromAllShops(new ItemStack(variant), !isSelling, player);
                        result.allResults.addAll(variantMatches);
                    }
                    continue;
                }


                // --- Material or display name support ---
                Material mat = Material.getMaterial(singleItem.toUpperCase());
                if (mat != null && mat.isItem()) {
                    result.anyValid = true;
                    List<FoundShopItemModel> foundItems = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .findItemBasedOnTypeFromAllShops(new ItemStack(mat), !isSelling, player);

                    // If searching for NAME_TAG, filter out renamed ones (vanilla only)
                    if (mat == Material.NAME_TAG) {
                        foundItems = foundItems.stream()
                                .filter(shopItem -> {
                                    ItemStack item = shopItem.getItemStack();
                                    return item != null && item.getType() == Material.NAME_TAG && (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName());
                                })
                                .collect(Collectors.toList());
                    }
                    // If searching for paper, filter out renamed ones (vanilla only)
                    if (mat == Material.PAPER) {
                        foundItems = foundItems.stream()
                                .filter(shopItem -> {
                                    ItemStack item = shopItem.getItemStack();
                                    return item != null && item.getType() == Material.PAPER && (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName());
                                })
                                .collect(Collectors.toList());
                    }
                    // If searching for MAP, filter out renamed ones (vanilla only)
                    if (mat == Material.MAP) {
                        foundItems = foundItems.stream()
                                .filter(shopItem -> {
                                    ItemStack item = shopItem.getItemStack();
                                    return item != null && item.getType() == Material.MAP && (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName());
                                })
                                .collect(Collectors.toList());
                    }
                    // If searching for BOOK, filter out renamed ones (vanilla only)
                    if (mat == Material.BOOK) {
                        foundItems = foundItems.stream()
                                .filter(shopItem -> {
                                    ItemStack item = shopItem.getItemStack();
                                    return item != null && item.getType() == Material.BOOK && (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName());
                                })
                                .collect(Collectors.toList());
                    }
                    result.allResults.addAll(foundItems);
                } else {
                    List<FoundShopItemModel> displayNameResults = (List<FoundShopItemModel>) FindItemAddOn.getQsApiInstance()
                            .findItemBasedOnDisplayNameFromAllShops(singleItem, !isSelling, player);
                    if (!displayNameResults.isEmpty()) {
                        result.anyValid = true;
                        result.allResults.addAll(displayNameResults);
                    }
                }
            }

            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                handleShopResults(player, result);
            });
        });

        return true;
    }

    private static class ShopSearchResult {
        boolean anyValid = false;
        List<FoundShopItemModel> allResults = new ArrayList<>();
    }

    private void handleShopResults(Player player, ShopSearchResult result) {
        if (!result.anyValid) {
            player.sendMessage(ColorTranslator.translateColorCodes("&cInvalid item, redirecting to menu."));
            player.performCommand("wtsmenu");
        } else {
            cmdExecutor.openShopMenuDescending(player, result.allResults, false, FindItemAddOn.getConfigProvider().NO_SHOP_FOUND_MSG, "wtsmenu");
        }
    }

    // Utility method to map user input to Bukkit Enchantment (now supports book names and aliases)
    private static Enchantment getEnchantmentByName(String name) {
        String key = name.toLowerCase().replace("_", "").replace(" ", "");
        // Try book name mapping first
        if (BOOK_NAME_TO_ENCHANTMENT.containsKey(key)) {
            return BOOK_NAME_TO_ENCHANTMENT.get(key);
        }
        // Fallback to internal key and Bukkit name
        for (Enchantment ench : Enchantment.values()) {
            String enchKey = ench.getKey().getKey().toLowerCase().replace("_", "").replace(" ", "");
            if (enchKey.equals(key) || ench.getName().equalsIgnoreCase(name)) {
                return ench;
            }
        }
        return null;
    }

    // Utility method to map user input to Bukkit PotionEffectType, including friendly names (ALL UPPERCASE)
    private static PotionEffectType getPotionEffectByName(String name) {
        String key = name.toUpperCase().replace("_", "").replace(" ", "");
        // Check friendly names first
        if (FRIENDLY_POTION_EFFECTS.containsKey(key)) {
            return FRIENDLY_POTION_EFFECTS.get(key);
        }
        // Fallback to Bukkit's names
        for (PotionEffectType effect : PotionEffectType.values()) {
            if (effect == null) continue;
            String effectKey = effect.getName().toUpperCase().replace("_", "").replace(" ", "");
            if (effectKey.equals(key)) {
                return effect;
            }
        }
        return null;
    }
}