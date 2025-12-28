package me.perch.shopfinder.quickshop.impl;

import cc.carm.lib.easysql.api.SQLQuery;
import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.QuickShopBukkit;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.command.CommandContainer;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.api.shop.permission.BuiltInShopPermission;
import com.ghostchu.quickshop.database.DataTables;
import com.ghostchu.quickshop.util.Util;
import me.perch.shopfinder.quickshop.QSApi;
import me.perch.shopfinder.commands.quickshop.subcommands.FindItemCmdHikariImpl;
import me.perch.shopfinder.FindItemAddOn;
import me.perch.shopfinder.models.CachedShop;
import me.perch.shopfinder.models.FoundShopItemModel;
import me.perch.shopfinder.models.ShopSearchActivityModel;
import me.perch.shopfinder.utils.enums.PlayerPermsEnum;
import me.perch.shopfinder.utils.json.HiddenShopStorageUtil;
import me.perch.shopfinder.utils.log.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class QSHikariAPIHandler implements QSApi<QuickShop, Shop> {

    private final QuickShopAPI api;
    private final String pluginVersion;
    private final ConcurrentMap<Long, CachedShop> shopCache;
    private final int SHOP_CACHE_TIMEOUT_SECONDS = 5 * 60;
    private final boolean isQSHikariShopCacheImplemented;

    public QSHikariAPIHandler() {
        api = QuickShopAPI.getInstance();
        pluginVersion = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari").getDescription().getVersion();
        Logger.logInfo("Initializing Shop caching");
        shopCache = new ConcurrentHashMap<>();
        isQSHikariShopCacheImplemented = checkIfQSHikariShopCacheImplemented();
    }

    public List<FoundShopItemModel> findItemBasedOnTypeFromAllShops(ItemStack item, boolean toBuy, Player searchingPlayer) {
        var begin = Instant.now();
        List<FoundShopItemModel> shopsFoundList = new ArrayList<>();
        List<Shop> allShops = fetchAllShopsFromQS();
        for (Shop shopIterator : allShops) {
            if (shopIterator.playerAuthorize(searchingPlayer.getUniqueId(), BuiltInShopPermission.SEARCH)
                    && (!FindItemAddOn.getConfigProvider().getBlacklistedWorlds().contains(shopIterator.getLocation().getWorld())
                    && shopIterator.getItem().getType().equals(item.getType())
                    && (toBuy ? shopIterator.isSelling() : shopIterator.isBuying()))
                    && (!HiddenShopStorageUtil.isShopHidden(shopIterator))) {
                processPotentialShopMatchAndAddToFoundList(toBuy, shopIterator, shopsFoundList, searchingPlayer);
            }
        }
        List<FoundShopItemModel> sortedShops = handleShopSorting(toBuy, shopsFoundList);
        QSApi.logTimeTookMsg(begin);
        return sortedShops;
    }

    private static boolean isOwnerHavingEnoughBalance(@NotNull Shop shop) {
        if (shop.getOwner().getUniqueIdOptional().isEmpty()) {
            return true;
        }

        double pricePerTransaction = shop.getPrice() * shop.getItem().getAmount();

        QUser qUser = shop.getOwner();
        UUID uuid = qUser.getUniqueIdIfRealPlayer().orElse(null);
        if (uuid == null) {
            return true;
        }

        Location shopLoc = shop.getLocation();
        if (shopLoc == null) {
            return true;
        }
        World world = shopLoc.getWorld();
        if (world == null) {
            return true;
        }

        Object currency = shop.getCurrency();
        if (currency == null) {
            return true;
        }

        QuickShop quickShop = getQuickShop();

        try {
            Method getEconomyManager = quickShop.getClass().getMethod("getEconomyManager");
            Object economyManager = getEconomyManager.invoke(quickShop);
            if (economyManager == null) {
                return true;
            }

            Method providerMethod = economyManager.getClass().getMethod("provider");
            Object provider = providerMethod.invoke(economyManager);
            if (provider == null) {
                return true;
            }

            Method balanceMethod = provider.getClass().getMethod(
                    "balance",
                    QUser.class,
                    String.class,
                    currency.getClass()
            );

            Object result = balanceMethod.invoke(
                    provider,
                    qUser,
                    world.getName(),
                    currency
            );

            if (result instanceof java.math.BigDecimal bd) {
                return bd.compareTo(java.math.BigDecimal.valueOf(pricePerTransaction)) >= 0;
            }
            if (result instanceof Number n) {
                return java.math.BigDecimal.valueOf(n.doubleValue())
                        .compareTo(java.math.BigDecimal.valueOf(pricePerTransaction)) >= 0;
            }
            return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) {
        }

        try {
            Object economy = quickShop.getEconomy();
            if (economy == null) {
                return true;
            }
            Method legacyBalance = economy.getClass().getMethod(
                    "getBalance",
                    QUser.class,
                    World.class,
                    currency.getClass()
            );
            Object res = legacyBalance.invoke(economy, qUser, world, currency);
            if (res instanceof Number n) {
                return n.doubleValue() >= pricePerTransaction;
            }
            return true;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static QuickShop getQuickShop() {
        return ((QuickShopBukkit) QuickShopAPI.getPluginInstance()).getQuickShop();
    }

    @NotNull
    static List<FoundShopItemModel> handleShopSorting(boolean toBuy, @NotNull List<FoundShopItemModel> shopsFoundList) {
        if (!shopsFoundList.isEmpty()) {
            int sortingMethod = 2;
            try {
                sortingMethod = FindItemAddOn.getConfigProvider().SHOP_SORTING_METHOD;
            } catch (Exception e) {
                Logger.logError("Invalid value in config.yml : 'shop-sorting-method'");
                Logger.logError("Defaulting to sorting by prices method");
            }
            return QSApi.sortShops(sortingMethod, shopsFoundList, toBuy);
        }
        return shopsFoundList;
    }

    public List<FoundShopItemModel> findItemBasedOnDisplayNameFromAllShops(String displayName, boolean toBuy, Player searchingPlayer) {
        var begin = Instant.now();
        List<FoundShopItemModel> shopsFoundList = new ArrayList<>();
        List<Shop> allShops = fetchAllShopsFromQS();
        for (Shop shopIterator : allShops) {
            if (shopIterator.playerAuthorize(searchingPlayer.getUniqueId(), BuiltInShopPermission.SEARCH)
                    && !FindItemAddOn.getConfigProvider().getBlacklistedWorlds().contains(shopIterator.getLocation().getWorld())
                    && shopIterator.getItem().hasItemMeta()
                    && Objects.requireNonNull(shopIterator.getItem().getItemMeta()).hasDisplayName()
                    && (shopIterator.getItem().getItemMeta().getDisplayName().toLowerCase().contains(displayName.toLowerCase())
                    && (toBuy ? shopIterator.isSelling() : shopIterator.isBuying()))
                    && !HiddenShopStorageUtil.isShopHidden(shopIterator)) {
                processPotentialShopMatchAndAddToFoundList(toBuy, shopIterator, shopsFoundList, searchingPlayer);
            }
        }
        List<FoundShopItemModel> sortedShops = handleShopSorting(toBuy, shopsFoundList);
        QSApi.logTimeTookMsg(begin);
        return sortedShops;
    }

    public List<FoundShopItemModel> fetchAllItemsFromAllShops(boolean toBuy, Player searchingPlayer) {
        var begin = Instant.now();
        List<FoundShopItemModel> shopsFoundList = new ArrayList<>();
        List<Shop> allShops = fetchAllShopsFromQS();
        for (Shop shopIterator : allShops) {
            if (shopIterator.playerAuthorize(searchingPlayer.getUniqueId(), BuiltInShopPermission.SEARCH)
                    && (!FindItemAddOn.getConfigProvider().getBlacklistedWorlds().contains(shopIterator.getLocation().getWorld())
                    && (toBuy ? shopIterator.isSelling() : shopIterator.isBuying()))
                    && (!HiddenShopStorageUtil.isShopHidden(shopIterator))) {
                processPotentialShopMatchAndAddToFoundList(toBuy, shopIterator, shopsFoundList, searchingPlayer);
            }
        }
        List<FoundShopItemModel> sortedShops = new ArrayList<>(shopsFoundList);
        if (!shopsFoundList.isEmpty()) {
            int sortingMethod = 1;
            sortedShops = QSApi.sortShops(sortingMethod, shopsFoundList, toBuy);
        }
        QSApi.logTimeTookMsg(begin);
        return sortedShops;
    }

    private List<Shop> fetchAllShopsFromQS() {
        List<Shop> allShops;
        if (FindItemAddOn.getConfigProvider().SEARCH_LOADED_SHOPS_ONLY) {
            allShops = new ArrayList<>(api.getShopManager().getLoadedShops());
        } else {
            allShops = getAllShops();
        }
        return allShops;
    }

    public Material getShopSignMaterial() {
        return com.ghostchu.quickshop.util.Util.getSignMaterial();
    }

    public Shop findShopAtLocation(Block block) {
        Location loc = block.getLocation();

        Shop shop = api.getShopManager().getShopIncludeAttached(loc);

        if (shop == null && block.getType() == Material.CHEST) {
            Block secondHalf = com.ghostchu.quickshop.util.Util.getSecondHalf(block);
            if (secondHalf != null) {
                shop = api.getShopManager().getShopIncludeAttached(secondHalf.getLocation());
            }
        }

        return shop;
    }

    public boolean isShopOwnerCommandRunner(Player player, Shop shop) {
        return shop.getOwner().getUniqueId().toString().equalsIgnoreCase(player.getUniqueId().toString());
    }

    @Override
    public List<Shop> getAllShops() {
        return api.getShopManager().getAllShops();
    }

    @Override
    public List<ShopSearchActivityModel> syncShopsListForStorage(List<ShopSearchActivityModel> globalShopsList) {
        long start = System.currentTimeMillis();
        List<ShopSearchActivityModel> tempGlobalShopsList = new ArrayList<>();
        for (Shop shop_i : getAllShops()) {
            Location shopLoc = shop_i.getLocation();
            tempGlobalShopsList.add(new ShopSearchActivityModel(
                    shopLoc.getWorld().getName(),
                    shopLoc.getX(),
                    shopLoc.getY(),
                    shopLoc.getZ(),
                    shopLoc.getPitch(),
                    shopLoc.getYaw(),
                    convertQUserToUUID(shop_i.getOwner()).toString(),
                    new ArrayList<>(),
                    false
            ));
        }

        for (ShopSearchActivityModel shop_temp : tempGlobalShopsList) {
            ShopSearchActivityModel tempShopToRemove = null;
            for (ShopSearchActivityModel shop_global : globalShopsList) {
                if (shop_temp.getWorldName().equalsIgnoreCase(shop_global.getWorldName())
                        && shop_temp.getX() == shop_global.getX()
                        && shop_temp.getY() == shop_global.getY()
                        && shop_temp.getZ() == shop_global.getZ()
                        && shop_temp.getShopOwnerUUID().equalsIgnoreCase(shop_global.getShopOwnerUUID())
                ) {
                    shop_temp.setPlayerVisitList(shop_global.getPlayerVisitList());
                    shop_temp.setHiddenFromSearch(shop_global.isHiddenFromSearch());
                    tempShopToRemove = shop_global;
                    break;
                }
            }
            if (tempShopToRemove != null)
                globalShopsList.remove(tempShopToRemove);
        }
        Logger.logInfo("Shops List sync complete. Time took: " + (System.currentTimeMillis() - start) + "ms.");
        return tempGlobalShopsList;
    }

    @Override
    public void registerSubCommand() {
        Logger.logInfo("Unregistered find sub-command for /qs");
        for (CommandContainer cmdContainer : api.getCommandManager().getRegisteredCommands()) {
            if (cmdContainer.getPrefix().equalsIgnoreCase("find")) {
                api.getCommandManager().unregisterCmd(cmdContainer);
                break;
            }
        }
        Logger.logInfo("Registered finditem sub-command for /qs");
        api.getCommandManager().registerCmd(
                CommandContainer.builder()
                        .prefix("finditem")
                        .permission(PlayerPermsEnum.FINDITEM_USE.value())
                        .hidden(false)
                        .description(locale -> Component.text("Search for items from all shops using an interactive GUI"))
                        .executor(new FindItemCmdHikariImpl())
                        .build());
    }

    @Override
    public boolean isQSShopCacheImplemented() {
        return isQSHikariShopCacheImplemented;
    }

    @Override
    public int processUnknownStockSpace(Location shopLoc, boolean toBuy) {
        Util.ensureThread(false);
        Shop qsShop = api.getShopManager().getShop(shopLoc);
        if (qsShop != null) {
            return (toBuy ? qsShop.getRemainingStock() : qsShop.getRemainingSpace());
        } else {
            return -2;
        }
    }

    private UUID convertQUserToUUID(QUser qUser) {
        Optional<UUID> uuid = qUser.getUniqueIdOptional();
        if (uuid.isPresent()) {
            return uuid.get();
        }
        String username = qUser.getUsernameOptional().orElse("Unknown");
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public UUID convertNameToUuid(String playerName) {
        return api.getPlayerFinder().name2Uuid(playerName);
    }

    private boolean isShopToBeIgnoredForFullOrEmpty(int stockOrSpace) {
        boolean ignoreEmptyChests = FindItemAddOn.getConfigProvider().IGNORE_EMPTY_CHESTS;
        if (ignoreEmptyChests) {
            return stockOrSpace == 0;
        }
        return false;
    }

    private int getRemainingStockOrSpaceFromShopCache___test(Shop shop, boolean fetchRemainingStock) {
        CachedShop cachedShop = shopCache.get(shop.getShopId());
        if (cachedShop == null || QSApi.isTimeDifferenceGreaterThanSeconds(cachedShop.getLastFetched(), new Date(), SHOP_CACHE_TIMEOUT_SECONDS)) {
            cachedShop = CachedShop.builder()
                    .shopId(shop.getShopId())
                    .remainingStock(shop.getRemainingStock())
                    .remainingSpace(shop.getRemainingSpace())
                    .lastFetched(new Date())
                    .build();
            shopCache.put(cachedShop.getShopId(), cachedShop);
        }
        return (fetchRemainingStock ? cachedShop.getRemainingStock() : cachedShop.getRemainingSpace());
    }

    private int getRemainingStockOrSpaceFromShopCache(Shop shop, boolean fetchRemainingStock) {
        String mainVersionStr = pluginVersion.split("\\.")[0];
        int mainVersion = Integer.parseInt(mainVersionStr);
        if (mainVersion >= 6) {
            Util.ensureThread(true);
            return (fetchRemainingStock ? shop.getRemainingStock() : shop.getRemainingSpace());
        } else {
            Logger.logWarning("Update recommended to QuickShop-Hikari v6+! You are still using v" + pluginVersion);
            CachedShop cachedShop = shopCache.get(shop.getShopId());
            if (cachedShop == null || QSApi.isTimeDifferenceGreaterThanSeconds(cachedShop.getLastFetched(), new Date(), SHOP_CACHE_TIMEOUT_SECONDS)) {
                cachedShop = CachedShop.builder()
                        .shopId(shop.getShopId())
                        .remainingStock(shop.getRemainingStock())
                        .remainingSpace(shop.getRemainingSpace())
                        .lastFetched(new Date())
                        .build();
                shopCache.put(cachedShop.getShopId(), cachedShop);
            }
            return (fetchRemainingStock ? cachedShop.getRemainingStock() : cachedShop.getRemainingSpace());
        }
    }

    private void testQuickShopHikariExternalCache(Shop shop) throws RuntimeException {
        boolean fetchRemainingStock = false;
        long shopId = shop.getShopId();
        try (SQLQuery query = DataTables.EXTERNAL_CACHE.createQuery()
                .addCondition("shop", shopId)
                .selectColumns("space", "stock")
                .setLimit(1)
                .build()
                .execute(); ResultSet resultSet = query.getResultSet()) {
            if (resultSet.next()) {
                long stock = resultSet.getLong("stock");
                long space = resultSet.getLong("space");
                Logger.logWarning("1: Location: " + shop.getLocation() + " | Stock: " + stock + " | Space: " + space);
            } else {
                Logger.logWarning("No cached data found!");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean checkIfQSHikariShopCacheImplemented() {
        String mainVersionStr = pluginVersion.split("\\.")[0];
        int mainVersion = Integer.parseInt(mainVersionStr);
        return mainVersion >= 6;
    }

    private void processPotentialShopMatchAndAddToFoundList(boolean toBuy, Shop shopIterator, List<FoundShopItemModel> shopsFoundList, Player searchingPlayer) {
        int stockOrSpace = (toBuy ? getRemainingStockOrSpaceFromShopCache(shopIterator, true)
                : getRemainingStockOrSpaceFromShopCache(shopIterator, false));
        if (isShopToBeIgnoredForFullOrEmpty(stockOrSpace)) {
            return;
        }
        if (!toBuy && !isOwnerHavingEnoughBalance(shopIterator)) {
            return;
        }
        shopsFoundList.add(new FoundShopItemModel(
                shopIterator.getPrice(),
                QSApi.processStockOrSpace(stockOrSpace),
                shopIterator.getOwner().getUniqueIdOptional().orElse(new UUID(0, 0)),
                shopIterator.getLocation(),
                shopIterator.getItem(),
                toBuy
        ));
    }
}
