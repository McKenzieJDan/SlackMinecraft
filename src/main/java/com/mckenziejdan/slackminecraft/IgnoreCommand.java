package com.mckenziejdan.slackminecraft;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

public class IgnoreCommand implements CommandExecutor, TabCompleter {

    private final SlackMinecraft plugin;
    private final PlayerListener playerListener; // Need this to reload the internal list

    public IgnoreCommand(SlackMinecraft plugin, PlayerListener playerListener) {
        this.plugin = plugin;
        this.playerListener = playerListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("slackminecraft.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length < 1 || !args[0].equalsIgnoreCase("ignore")) {
            sendUsage(sender, label);
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender, label);
            return true;
        }

        String subCommand = args[1].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "add":
                handleAdd(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            default:
                sendUsage(sender, label);
                break;
        }

        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + (sender instanceof Player ? "smc" : "slackminecraft") + " ignore add <playername|uuid>");
            return;
        }
        String playerName = args[2];
        UUID targetUUID = resolvePlayer(playerName);

        if (targetUUID == null) { // Should rarely happen unless name is invalid
            sender.sendMessage(ChatColor.RED + "Use a known player name or UUID: " + playerName);
            return;
        }

        List<String> ignoredList = plugin.getConfig().getStringList(ConfigConstants.OPTIONS_IGNORED_PLAYERS);
        String uuidString = targetUUID.toString();

        if (ignoredList.contains(uuidString)) {
            sender.sendMessage(ChatColor.YELLOW + playerName + " (" + uuidString + ") is already ignored.");
            return;
        }

        ignoredList.add(uuidString);
        plugin.getConfig().set(ConfigConstants.OPTIONS_IGNORED_PLAYERS, ignoredList);
        plugin.saveConfig();
        playerListener.loadIgnoredPlayers(); // Reload listener's internal set

        sender.sendMessage(ChatColor.GREEN + "Added " + playerName + " (" + uuidString + ") to the ignore list.");
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + (sender instanceof Player ? "smc" : "slackminecraft") + " ignore remove <playername|uuid>");
            return;
        }
        String playerName = args[2];
        UUID targetUUID = resolvePlayer(playerName);

        if (targetUUID == null) {
            sender.sendMessage(ChatColor.RED + "Use a known player name or UUID: " + playerName);
            return;
        }

        List<String> ignoredList = plugin.getConfig().getStringList(ConfigConstants.OPTIONS_IGNORED_PLAYERS);
        String uuidString = targetUUID.toString();

        if (!ignoredList.contains(uuidString)) {
            sender.sendMessage(ChatColor.YELLOW + playerName + " (" + uuidString + ") is not on the ignore list.");
            return;
        }

        ignoredList.remove(uuidString);
        plugin.getConfig().set(ConfigConstants.OPTIONS_IGNORED_PLAYERS, ignoredList);
        plugin.saveConfig();
        playerListener.loadIgnoredPlayers(); // Reload listener's internal set

        sender.sendMessage(ChatColor.GREEN + "Removed " + playerName + " (" + uuidString + ") from the ignore list.");
    }

    private UUID resolvePlayer(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            // Only consult local players. Name-based getOfflinePlayer can block on Mojang HTTP.
            Player online = Bukkit.getPlayerExact(input);
            if (online != null) return online.getUniqueId();
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (input.equalsIgnoreCase(player.getName())) return player.getUniqueId();
            }
            return null;
        }
    }

    private void handleList(CommandSender sender) {
        List<String> ignoredList = plugin.getConfig().getStringList(ConfigConstants.OPTIONS_IGNORED_PLAYERS);
        if (ignoredList.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "The ignore list is currently empty.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "--- Ignored Players (UUIDs) ---");
        for (String uuidString : ignoredList) {
            // Try to get name for context, but show UUID regardless
            OfflinePlayer player = null;
            try {
                 player = Bukkit.getOfflinePlayer(UUID.fromString(uuidString));
            } catch (IllegalArgumentException ignored) { }
            String namePart = (player != null && player.getName() != null) ? " (" + player.getName() + ")" : "";
            sender.sendMessage(ChatColor.YELLOW + "- " + uuidString + namePart);
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " ignore <add|remove|list> [playername|uuid]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> possibilities = new ArrayList<>();

        if (!sender.hasPermission("slackminecraft.admin")) {
            return completions; // Empty list
        }

        if (args.length == 1) {
            possibilities.add("ignore");
            StringUtil.copyPartialMatches(args[0], possibilities, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ignore")) {
            possibilities.addAll(Arrays.asList("add", "remove", "list"));
            StringUtil.copyPartialMatches(args[1], possibilities, completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ignore") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            // Suggest online players for add/remove
            possibilities.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            StringUtil.copyPartialMatches(args[2], possibilities, completions);
        }

        Collections.sort(completions);
        return completions;
    }
} 