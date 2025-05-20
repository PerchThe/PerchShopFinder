package io.myzticbean.finditemaddon.commands;

import io.myzticbean.finditemaddon.FindItemAddOn;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WhereToBuyTabCompleter implements TabCompleter {
    private final List<String> itemsList;

    public WhereToBuyTabCompleter() {
        itemsList = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (!FindItemAddOn.getConfigProvider().getBlacklistedMaterials().contains(mat)) {
                itemsList.add(mat.name());
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> custom = new ArrayList<>();
            List<String> items = new ArrayList<>();

            // Always add custom completions first if they match (or if input is empty)
            if (input.isEmpty() || "hand".startsWith(input)) custom.add("hand");
            if (input.isEmpty() || "safarinet".startsWith(input)) custom.add("safarinet");
            if (input.isEmpty() || "voucher".startsWith(input)) custom.add("voucher");

            // Then add matching item names, but skip any that are already in custom
            items.addAll(itemsList.stream()
                    .filter(item -> item.toLowerCase().startsWith(input))
                    .filter(item -> !custom.contains(item.toLowerCase()))
                    .collect(Collectors.toList()));

            // Combine and return
            List<String> completions = new ArrayList<>();
            completions.addAll(custom);
            completions.addAll(items);
            return completions;
        }
        return Collections.emptyList();
    }
}