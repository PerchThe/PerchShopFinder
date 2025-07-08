package me.perch.shopfinder.commands;

import me.perch.shopfinder.handlers.command.CmdExecutorHandler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OutOfStockCommand implements CommandExecutor {

    private final CmdExecutorHandler cmdExecutor;

    public OutOfStockCommand(CmdExecutorHandler cmdExecutor) {
        this.cmdExecutor = cmdExecutor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /outofstock!");
            return true;
        }
        cmdExecutor.handleOutOfStockMenu(player);
        return true;
    }
}