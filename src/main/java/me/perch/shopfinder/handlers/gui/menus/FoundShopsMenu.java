package me.perch.shopfinder.handlers.gui.menus;

import com.olziedev.playerwarps.api.warp.Warp;
import me.perch.shopfinder.config.ConfigProvider;
import me.perch.shopfinder.dependencies.EssentialsXPlugin;
import me.perch.shopfinder.dependencies.PlayerWarpsPlugin;
import me.perch.shopfinder.dependencies.WGPlugin;
import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.handlers.gui.PaginatedMenu;
import me.perch.shopfinder.handlers.gui.PlayerMenuUtility;
import me.perch.shopfinder.models.FoundShopItemModel;
import me.perch.shopfinder.utils.enums.CustomCmdPlaceholdersEnum;
import me.perch.shopfinder.utils.enums.PlayerPermsEnum;
import me.perch.shopfinder.utils.enums.ShopLorePlaceholdersEnum;
import me.perch.shopfinder.utils.json.ShopSearchActivityStorageUtil;
import me.perch.shopfinder.utils.LocationUtils;
import me.perch.shopfinder.utils.log.Logger;
import me.perch.shopfinder.utils.warp.EssentialWarpsUtil;
import me.perch.shopfinder.utils.warp.PlayerWarpsUtil;
import me.perch.shopfinder.utils.warp.WGRegionUtils;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import io.papermc.lib.PaperLib;
import me.kodysimpson.simpapi.colors.ColorTranslator;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import me.perch.shopfinder.utils.ShopHighlighter;
import me.perch.shopfinder.utils.HighlightColourManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class FoundShopsMenu extends PaginatedMenu {

    public static final String SHOP_STOCK_UNLIMITED = "Unlimited";
    public static final String SHOP_STOCK_UNKNOWN = "Unknown";
    private static final String NAMEDSPACE_KEY_LOCATION_DATA = "locationData";
    private final ConfigProvider configProvider;
    private final List<FoundShopItemModel> currentPageShops = new ArrayList<>();
    private final boolean isBuying;

    public FoundShopsMenu(PlayerMenuUtility playerMenuUtility, List<FoundShopItemModel> searchResult, boolean isBuying) {
        super(playerMenuUtility, searchResult);
        configProvider = FindItemAddOn.getConfigProvider();
        this.isBuying = isBuying;
    }

    public FoundShopsMenu(PlayerMenuUtility playerMenuUtility, List<FoundShopItemModel> searchResult) {
        this(playerMenuUtility, searchResult, false);
    }

    @Override
    public String getMenuName() {
        if (!StringUtils.isEmpty(configProvider.SHOP_SEARCH_GUI_TITLE)) {
            return ColorTranslator.translateColorCodes(configProvider.SHOP_SEARCH_GUI_TITLE);
        } else {
            return ColorTranslator.translateColorCodes("&l» &rShops");
        }
    }

    @Override
    public int getSlots() {
        return 54;
    }

    @Override
    public void handleMenu(@NotNull InventoryClickEvent event) {
        int slot = event.getSlot();
        Player player = (Player) event.getWhoClicked();
        if (handleNavigationClick(event, slot)) {
            return;
        }
        if (slot == 0) {
            String origin = playerMenuUtility.getOriginCommand();
            if ("wtbmenu".equalsIgnoreCase(origin)) {
                player.performCommand("wtbmenu");
            } else if ("wtsmenu".equalsIgnoreCase(origin)) {
                player.performCommand("wtsmenu");
            }
            return;
        }
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().equals(Material.AIR)) {
            Logger.logDebugInfo(player.getName() + " just clicked on AIR!");
            return;
        }
        if (slot >= 9 && slot < 45) {
            int shopIndex = slot - 9;
            if (shopIndex >= 0 && shopIndex < currentPageShops.size()) {
                FoundShopItemModel foundShop = currentPageShops.get(shopIndex);
                // Shift click to favorite/unfavorite the warp
                if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT || event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_LEFT) {
                    String nearestWarp = getNearestWarpInfo(foundShop);
                    if (nearestWarp != null && !nearestWarp.isEmpty() && !nearestWarp.equals(configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG)) {
                        player.performCommand("w favorite " + nearestWarp);
// Delay menu refresh to allow favorite status to update
                        Bukkit.getScheduler().runTaskLater(
                                FindItemAddOn.getInstance(),
                                () -> super.open(super.playerMenuUtility.getPlayerShopSearchResult()),
                                10L // 10 ticks = 0.5 seconds, adjust if needed
                        );
                    } else {
                        // No warp found, just refresh (or do nothing)
                        super.open(super.playerMenuUtility.getPlayerShopSearchResult());
                    }
                    event.setCancelled(true);
                    return;
                }
                // Normal click behavior
                handleShopItemClick(event, player, foundShop);
            }
        }
    }

    private boolean handleNavigationClick(InventoryClickEvent event, int slot) {
        return switch (slot) {
            case 45 -> {
                handleMenuClickForNavToPrevPage(event);
                yield true;
            }
            case 46 -> {
                handleFirstPageClick(event);
                yield true;
            }
            case 52 -> {
                handleLastPageClick(event);
                yield true;
            }
            case 53 -> {
                handleMenuClickForNavToNextPage(event);
                yield true;
            }
            default -> false;
        };
    }

    private void handleShopItemClick(@NotNull InventoryClickEvent event, Player player, FoundShopItemModel foundShop) {
        if (configProvider.TP_PLAYER_DIRECTLY_TO_SHOP) {
            Location directShopLocation = foundShop.getShopLocation();
            handleDirectShopTeleport(player, directShopLocation);
            if (!shouldApplyTeleportDelay(player) && directShopLocation != null) {
                String colour = HighlightColourManager.getColour(player.getUniqueId());
                ShopHighlighter.highlightShop(directShopLocation, 600, FindItemAddOn.getInstance(), colour);
            }
        } else if (configProvider.TP_PLAYER_TO_NEAREST_WARP) {
            String nearestWarp = getNearestWarpInfo(foundShop);
            handleWarpTeleport(player, nearestWarp);
            Location warpShopLocation = foundShop.getShopLocation();
            if (warpShopLocation != null) {
                String colour = HighlightColourManager.getColour(player.getUniqueId());
                Bukkit.getScheduler().runTaskLater(
                        FindItemAddOn.getInstance(),
                        () -> ShopHighlighter.highlightShop(warpShopLocation, 600, FindItemAddOn.getInstance(), colour),
                        30L
                );
            }
        }
        handleCustomCommands(player, foundShop.getShopLocation());
        player.closeInventory();
    }

    private void handleDirectShopTeleport(@NotNull Player player, Location shopLocation) {
        if (!player.hasPermission(PlayerPermsEnum.FINDITEM_SHOPTP.value())) {
            sendNoPermissionMessage(player);
            return;
        }
        if (shopLocation == null)
            return;
        UUID shopOwner = ShopSearchActivityStorageUtil.getShopOwnerUUID(shopLocation);
        if (player.getUniqueId().equals(shopOwner) && !PlayerPermsEnum.canPlayerTpToOwnShop(player)) {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    configProvider.PLUGIN_PREFIX + configProvider.SHOP_TP_NO_PERMISSION_MSG));
            return;
        }
        Location locToTeleport = LocationUtils.findSafeLocationAroundShop(shopLocation, player);
        if (locToTeleport == null) {
            sendUnsafeAreaMessage(player);
            return;
        }
        ShopSearchActivityStorageUtil.addPlayerVisitEntryAsync(shopLocation, player);
        if (EssentialsXPlugin.isEnabled())
            EssentialsXPlugin.setLastLocation(player);
        if (shouldApplyTeleportDelay(player)) {
            long delay = Long.parseLong(configProvider.TP_DELAY_IN_SECONDS);
            String tpDelayMsg = configProvider.TP_DELAY_MESSAGE;
            if (!StringUtils.isEmpty(tpDelayMsg)) {
                player.sendMessage(ColorTranslator.translateColorCodes(
                        configProvider.PLUGIN_PREFIX + replaceDelayPlaceholder(tpDelayMsg, delay)));
            }
            Bukkit.getScheduler().scheduleSyncDelayedTask(
                    FindItemAddOn.getInstance(),
                    () -> {
                        PaperLib.teleportAsync(player, locToTeleport, PlayerTeleportEvent.TeleportCause.PLUGIN);
                        String colour = HighlightColourManager.getColour(player.getUniqueId());
                        ShopHighlighter.highlightShop(shopLocation, 600, FindItemAddOn.getInstance(), colour);
                    },
                    delay * 20);
        } else {
            PaperLib.teleportAsync(player, locToTeleport, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
    }

    private void handleWarpTeleport(Player player, String warpName) {
        switch (configProvider.NEAREST_WARP_MODE) {
            case 1:
                EssentialWarpsUtil.warpPlayer(player, warpName);
                break;
            case 2:
                PlayerWarpsUtil.executeWarpPlayer(player, warpName);
                break;
        }
    }

    private void handleCustomCommands(Player player, Location shopLocation) {
        if (configProvider.CUSTOM_CMDS_RUN_ENABLED && !configProvider.CUSTOM_CMDS_LIST.isEmpty()
                && shopLocation != null) {
            for (String cmd : configProvider.CUSTOM_CMDS_LIST) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        replaceCustomCmdPlaceholders(cmd, player, shopLocation));
            }
        }
    }

    @Override
    public void setMenuItems(List<FoundShopItemModel> foundShops) {
        ItemStack filler = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        for (int i = 1; i <= 8; i++) {
            inventory.setItem(i, filler);
        }
        addMenuBottomBar();
        currentPageShops.clear();
        if (foundShops == null || foundShops.isEmpty()) {
            return;
        }
        int maxItemsPerPage = MAX_ITEMS_PER_PAGE;
        int shopItemSlot = 9;
        for (int guiSlotCounter = 0; guiSlotCounter < maxItemsPerPage && shopItemSlot < 45; guiSlotCounter++, shopItemSlot++) {
            index = maxItemsPerPage * page + guiSlotCounter;
            if (index >= foundShops.size()) {
                break;
            }
            FoundShopItemModel foundShop = foundShops.get(index);
            if (foundShop == null) {
                continue;
            }
            ItemStack item = createShopItem(foundShop);
            inventory.setItem(shopItemSlot, item);
            currentPageShops.add(foundShop);
            if (configProvider.NEAREST_WARP_MODE == 2 && PlayerWarpsPlugin.getIsEnabled()) {
                Player viewingPlayer = playerMenuUtility.getOwner();
                Warp nearestWarp = PlayerWarpsUtil.findNearestWarp(
                        foundShop.getShopLocation(),
                        viewingPlayer,
                        foundShop.getShopOwner()
                );
                if (nearestWarp != null) {
                    final int slot = shopItemSlot;
                    PlayerWarpsPlugin.isFavourite(viewingPlayer, nearestWarp.getWarpName(), isFav -> {
                        if (isFav) {
                            ItemStack glowing = makeItemGlow(item.clone());
                            Bukkit.getScheduler().runTask(FindItemAddOn.getInstance(), () -> {
                                inventory.setItem(slot, glowing);
                            });
                        }
                    });
                }
            }
        }
    }

    private @NotNull ItemStack createShopItem(@NotNull FoundShopItemModel foundShop) {
        ItemStack item = new ItemStack(foundShop.getItem().getType(), foundShop.getItem().getAmount());
        ItemMeta meta = foundShop.getItem().getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(item.getType());
        }
        List<String> lore = new ArrayList<>();
        addItemLore(lore, foundShop);
        meta.setLore(lore);
        setLocationData(meta, foundShop);
        if (foundShop.getItem().getItemMeta().hasCustomModelData()) {
            meta.setCustomModelData(foundShop.getItem().getItemMeta().getCustomModelData());
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItemGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Enchantment durability = org.bukkit.enchantments.Enchantment.getByName("DURABILITY");
            if (durability != null) {
                meta.addEnchant(durability, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addItemLore(List<String> lore, FoundShopItemModel foundShop) {
        ItemMeta shopItemMeta = foundShop.getItem().getItemMeta();
        if (shopItemMeta != null && shopItemMeta.hasLore()) {
            for (String line : shopItemMeta.getLore()) {
                lore.add(ColorTranslator.translateColorCodes(line));
            }
        }
        List<String> loreTemplate;
        if (isBuying && configProvider.SHOP_GUI_ITEM_LORE_BUY != null) {
            loreTemplate = configProvider.SHOP_GUI_ITEM_LORE_BUY;
        } else if (!isBuying && configProvider.SHOP_GUI_ITEM_LORE_SELL != null) {
            loreTemplate = configProvider.SHOP_GUI_ITEM_LORE_SELL;
        } else {
            loreTemplate = configProvider.SHOP_GUI_ITEM_LORE;
        }
        for (String loreLine : loreTemplate) {
            if (loreLine.contains(ShopLorePlaceholdersEnum.NEAREST_WARP.value())) {
                String nearestWarpInfo = getNearestWarpInfo(foundShop);
                lore.add(ColorTranslator.translateColorCodes(
                        loreLine.replace(ShopLorePlaceholdersEnum.NEAREST_WARP.value(), nearestWarpInfo)));
            } else {
                lore.add(ColorTranslator.translateColorCodes(replaceLorePlaceholders(loreLine, foundShop)));
            }
        }
        if (configProvider.TP_PLAYER_DIRECTLY_TO_SHOP
                && playerMenuUtility.getOwner().hasPermission(PlayerPermsEnum.FINDITEM_SHOPTP.value())) {
            lore.add(ColorTranslator.translateColorCodes(configProvider.CLICK_TO_TELEPORT_MSG));
        }
    }

    private String getNearestWarpInfo(FoundShopItemModel foundShop) {
        int nearestWarpMode = configProvider.NEAREST_WARP_MODE;
        Player viewingPlayer = playerMenuUtility.getOwner();
        switch (nearestWarpMode) {
            case 1:
                if (EssentialsXPlugin.isEnabled()) {
                    String nearestEWarp = EssentialWarpsUtil.findNearestWarp(foundShop.getShopLocation());
                    return (nearestEWarp != null && !StringUtils.isEmpty(nearestEWarp)) ? nearestEWarp
                            : configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG;
                }
                break;
            case 2:
                if (PlayerWarpsPlugin.getIsEnabled()) {
                    Warp nearestPlayerWarp = PlayerWarpsUtil.findNearestWarp(
                            foundShop.getShopLocation(),
                            viewingPlayer,
                            foundShop.getShopOwner()
                    );
                    return (nearestPlayerWarp != null) ? nearestPlayerWarp.getWarpName()
                            : configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG;
                }
                break;
            case 3:
                if (WGPlugin.isEnabled()) {
                    String nearestWGRegion = new WGRegionUtils().findNearestWGRegion(foundShop.getShopLocation());
                    return (nearestWGRegion != null && !StringUtils.isEmpty(nearestWGRegion)) ? nearestWGRegion
                            : configProvider.NO_WG_REGION_NEAR_SHOP_ERROR_MSG;
                }
                break;
            default:
                Logger.logDebugInfo("Invalid value in 'nearest-warp-mode' in config.yml!");
        }
        return configProvider.NO_WARP_NEAR_SHOP_ERROR_MSG;
    }

    private void setLocationData(ItemMeta meta, FoundShopItemModel foundShop) {
        NamespacedKey key = new NamespacedKey(FindItemAddOn.getInstance(), NAMEDSPACE_KEY_LOCATION_DATA);
        String locData = "";
        if (configProvider.TP_PLAYER_DIRECTLY_TO_SHOP) {
            Location shopLoc = foundShop.getShopLocation();
            locData = String.format("%s,%d,%d,%d", shopLoc.getWorld().getName(), shopLoc.getBlockX(),
                    shopLoc.getBlockY(), shopLoc.getBlockZ());
        } else if (configProvider.TP_PLAYER_TO_NEAREST_WARP) {
            locData = getNearestWarpInfo(foundShop);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, locData);
    }

    private @NotNull String replaceLorePlaceholders(String text, @NotNull FoundShopItemModel shop) {
        text = text.replace(ShopLorePlaceholdersEnum.ITEM_PRICE.value(), formatNumber(shop.getShopPrice()));
        if (text.contains(ShopLorePlaceholdersEnum.SHOP_STOCK.value())) {
            int stock = shop.getRemainingStockOrSpace();
            String stockText;
            if (stock == -2) {
                int stockOrSpace = processUnknownStockSpace(shop);
                stockText = (stockOrSpace == -2) ? SHOP_STOCK_UNKNOWN
                        : (stockOrSpace == -1 ? SHOP_STOCK_UNLIMITED : String.valueOf(stockOrSpace));
            } else {
                stockText = (stock == Integer.MAX_VALUE) ? SHOP_STOCK_UNLIMITED : String.valueOf(stock);
            }
            text = text.replace(ShopLorePlaceholdersEnum.SHOP_STOCK.value(), stockText);
        }
        text = text.replace(ShopLorePlaceholdersEnum.SHOP_PER_ITEM_QTY.value(),
                String.valueOf(shop.getItem().getAmount()));
        if (text.contains(ShopLorePlaceholdersEnum.SHOP_OWNER.value())) {
            OfflinePlayer shopOwner = Bukkit.getOfflinePlayer(shop.getShopOwner());
            String ownerName = shopOwner.getName() != null ? shopOwner.getName() : "Admin";
            text = text.replace(ShopLorePlaceholdersEnum.SHOP_OWNER.value(), ownerName);
        }
        if (text.contains(ShopLorePlaceholdersEnum.SHOP_LOCATION.value())) {
            Location loc = shop.getShopLocation();
            String locText = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
            text = text.replace(ShopLorePlaceholdersEnum.SHOP_LOCATION.value(), locText);
        }
        text = text.replace(ShopLorePlaceholdersEnum.SHOP_WORLD.value(),
                Objects.requireNonNull(shop.getShopLocation().getWorld()).getName());
        text = text.replace(ShopLorePlaceholdersEnum.SHOP_VISITS.value(),
                String.valueOf(ShopSearchActivityStorageUtil.getPlayerVisitCount(shop.getShopLocation())));
        if (text.contains(ShopLorePlaceholdersEnum.WARP_AVG_RATING.value()) || text.contains(ShopLorePlaceholdersEnum.WARP_TOTAL_RATINGS.value())) {
            String warpName = null;
            if (PlayerWarpsPlugin.getIsEnabled() && FindItemAddOn.getConfigProvider().NEAREST_WARP_MODE == 2) {
                Player viewingPlayer = playerMenuUtility.getOwner();
                Warp nearestWarp = PlayerWarpsUtil.findNearestWarp(
                        shop.getShopLocation(),
                        viewingPlayer,
                        shop.getShopOwner()
                );
                if (nearestWarp != null) {
                    warpName = nearestWarp.getWarpName();
                }
            }
            double avg = (warpName != null) ? PlayerWarpsPlugin.getWarpAverageRating(warpName) : -1;
            int total = (warpName != null) ? PlayerWarpsPlugin.getWarpTotalRatings(warpName) : -1;
            String avgString = avg >= 0 ? String.format("%.2f", avg) : "N/A";
            String totalString = total >= 0 ? String.valueOf(total) : "N/A";
            text = text.replace(ShopLorePlaceholdersEnum.WARP_AVG_RATING.value(), avgString)
                    .replace(ShopLorePlaceholdersEnum.WARP_TOTAL_RATINGS.value(), "(" + totalString + " ratings)");
        }
        return text;
    }

    private int processUnknownStockSpace(FoundShopItemModel shop) {
        return FindItemAddOn.getQsApiInstance().processUnknownStockSpace(shop.getShopLocation(), shop.isToBuy());
    }

    private String replaceDelayPlaceholder(String tpDelayMsg, long delay) {
        return tpDelayMsg.replace("{DELAY}", String.valueOf(delay));
    }

    private String formatNumber(double number) {
        if (configProvider.SHOP_GUI_USE_SHORTER_CURRENCY_FORMAT) {
            if (number < 100_000) {
                return String.format("%,.2f", number);
            } else if (number < 1_000_000) {
                return String.format("%.2fK", number / 1_000.0);
            } else if (number < 1_000_000_000) {
                return String.format("%.2fM", number / 1_000_000.0);
            } else if (number < 1_000_000_000_000L) {
                return String.format("%.2fB", number / 1_000_000_000.0);
            } else {
                return String.format("%.2fT", number / 1_000_000_000_000.0);
            }
        } else {
            return String.format("%,.2f", number);
        }
    }

    private String replaceCustomCmdPlaceholders(String cmd, Player player, Location shopLoc) {
        return cmd
                .replace(CustomCmdPlaceholdersEnum.PLAYER_NAME.value(), player.getName())
                .replace(CustomCmdPlaceholdersEnum.SHOP_LOC_X.value(), Double.toString(shopLoc.getX()))
                .replace(CustomCmdPlaceholdersEnum.SHOP_LOC_Y.value(), Double.toString(shopLoc.getY()))
                .replace(CustomCmdPlaceholdersEnum.SHOP_LOC_Z.value(), Double.toString(shopLoc.getZ()));
    }

    private void sendNoPermissionMessage(Player player) {
        if (!StringUtils.isEmpty(configProvider.SHOP_TP_NO_PERMISSION_MSG)) {
            player.sendMessage(ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX
                    + configProvider.SHOP_TP_NO_PERMISSION_MSG));
        }
    }

    private void sendUnsafeAreaMessage(Player player) {
        if (!StringUtils.isEmpty(configProvider.UNSAFE_SHOP_AREA_MSG)) {
            player.sendMessage(ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX
                    + configProvider.UNSAFE_SHOP_AREA_MSG));
        }
    }

    private boolean shouldApplyTeleportDelay(Player player) {
        return StringUtils.isNumeric(configProvider.TP_DELAY_IN_SECONDS)
                && !"0".equals(configProvider.TP_DELAY_IN_SECONDS)
                && !PlayerPermsEnum.hasShopTpDelayBypassPermOrAdmin(player);
    }

    private void applyTeleportDelay(Player player, Location locToTeleport) {
        long delay = Long.parseLong(configProvider.TP_DELAY_IN_SECONDS);
        Logger.logDebugInfo("Teleporting delay is set to: " + delay);
        String tpDelayMsg = configProvider.TP_DELAY_MESSAGE;
        if (!StringUtils.isEmpty(tpDelayMsg)) {
            player.sendMessage(ColorTranslator.translateColorCodes(
                    configProvider.PLUGIN_PREFIX + replaceDelayPlaceholder(tpDelayMsg, delay)));
        }
        Bukkit.getScheduler().scheduleSyncDelayedTask(
                FindItemAddOn.getInstance(),
                () -> PaperLib.teleportAsync(player, locToTeleport, PlayerTeleportEvent.TeleportCause.PLUGIN),
                delay * 20);
    }

    private void handleMenuClickForNavToNextPage(InventoryClickEvent event) {
        if (!((index + 1) >= super.playerMenuUtility.getPlayerShopSearchResult().size())) {
            page = page + 1;
            super.open(super.playerMenuUtility.getPlayerShopSearchResult());
        } else {
            if (!StringUtils.isEmpty(configProvider.SHOP_NAV_LAST_PAGE_ALERT_MSG)) {
                event.getWhoClicked().sendMessage(
                        ColorTranslator.translateColorCodes(
                                configProvider.PLUGIN_PREFIX + configProvider.SHOP_NAV_LAST_PAGE_ALERT_MSG));
            }
        }
    }

    private void handleMenuClickForNavToPrevPage(InventoryClickEvent event) {
        if (page == 0) {
            if (!StringUtils.isEmpty(configProvider.SHOP_NAV_FIRST_PAGE_ALERT_MSG)) {
                event.getWhoClicked().sendMessage(
                        ColorTranslator.translateColorCodes(configProvider.PLUGIN_PREFIX
                                + configProvider.SHOP_NAV_FIRST_PAGE_ALERT_MSG));
            }
        } else {
            page = page - 1;
            super.open(super.playerMenuUtility.getPlayerShopSearchResult());
        }
    }

    private void handleFirstPageClick(InventoryClickEvent event) {
        if (page == 0) {
            if (!StringUtils.isEmpty(configProvider.SHOP_NAV_FIRST_PAGE_ALERT_MSG)) {
                event.getWhoClicked().sendMessage(
                        ColorTranslator.translateColorCodes(
                                configProvider.PLUGIN_PREFIX
                                        + configProvider.SHOP_NAV_FIRST_PAGE_ALERT_MSG));
            }
        } else {
            page = 0;
            super.open(super.playerMenuUtility.getPlayerShopSearchResult());
        }
    }

    private void handleLastPageClick(InventoryClickEvent event) {
        int listSize = super.playerMenuUtility.getPlayerShopSearchResult().size();
        if (!((index + 1) >= listSize)) {
            double totalPages = listSize / MAX_ITEMS_PER_PAGE;
            if (totalPages % 10 == 0) {
                page = (int) Math.floor(totalPages);
                Logger.logDebugInfo("Floor page value: " + page);
            } else {
                page = (int) Math.ceil(totalPages);
                Logger.logDebugInfo("Ceiling page value: " + page);
            }
            super.open(super.playerMenuUtility.getPlayerShopSearchResult());
        } else {
            if (!StringUtils.isEmpty(configProvider.SHOP_NAV_LAST_PAGE_ALERT_MSG)) {
                event.getWhoClicked().sendMessage(
                        ColorTranslator.translateColorCodes(
                                configProvider.PLUGIN_PREFIX
                                        + configProvider.SHOP_NAV_LAST_PAGE_ALERT_MSG));
            }
        }
    }
}