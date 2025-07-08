package me.perch.shopfinder.handlers.gui;

import me.perch.shopfinder.models.FoundShopItemModel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * @author myzticbean
 */
public class PlayerMenuUtility {
    private UUID owner;

    @Setter
    @Getter
    private List<FoundShopItemModel> playerShopSearchResult;

    // --- Added for menu origin tracking ---
    private String originCommand;

    public void setOriginCommand(String originCommand) {
        this.originCommand = originCommand;
    }

    public String getOriginCommand() {
        return this.originCommand;
    }
    // --- End added ---

    public PlayerMenuUtility(Player owner) {
        this.owner = owner.getUniqueId();
    }

    public PlayerMenuUtility(UUID owner) {
        this.owner = owner;
    }

    @Nullable
    public Player getOwner() {
        return Bukkit.getPlayer(owner);
    }

    public void setOwner(Player owner) {
        this.owner = owner.getUniqueId();
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }
}