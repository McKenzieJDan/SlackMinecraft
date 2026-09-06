package com.mckenziejdan.slackminecraft;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final SlackBot slackBot;
    private final SlackMinecraft plugin;
    private volatile Set<UUID> ignoredPlayerUUIDs = Set.of();

    public PlayerListener(SlackMinecraft plugin, SlackBot slackBot) {
        this.plugin = plugin;
        this.slackBot = slackBot;
        loadIgnoredPlayers();
    }

    public void loadIgnoredPlayers() {
        Set<UUID> updated = new HashSet<>();
        List<String> ignoredList = plugin.getConfig().getStringList(ConfigConstants.OPTIONS_IGNORED_PLAYERS);
        for (String uuidString : ignoredList) {
            if (uuidString == null || uuidString.isEmpty() || uuidString.startsWith("example-")) continue;
            try {
                updated.add(UUID.fromString(uuidString));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID format in options.ignoredPlayers: " + uuidString);
            }
        }
        ignoredPlayerUUIDs = Set.copyOf(updated);
        plugin.getLogger().info("Loaded " + ignoredPlayerUUIDs.size() + " ignored player UUIDs.");
    }

    private boolean isPlayerIgnored(UUID playerUUID) {
        return ignoredPlayerUUIDs.contains(playerUUID);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent playerJoinEvent) {
        if (slackBot == null) return;
        if (isPlayerIgnored(playerJoinEvent.getPlayer().getUniqueId())) return;
        String playerName = playerJoinEvent.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + playerJoinEvent.getPlayer().getUniqueId();

        slackBot.sendMessage(plugin.getConfig().getString(ConfigConstants.I18N_JOINED_GAME), playerName, icon);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent playerQuitEvent) {
        if (slackBot == null) return;
        if (isPlayerIgnored(playerQuitEvent.getPlayer().getUniqueId())) return;
        String playerName = playerQuitEvent.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + playerQuitEvent.getPlayer().getUniqueId();

        slackBot.sendMessage(plugin.getConfig().getString(ConfigConstants.I18N_LEFT_GAME), playerName, icon);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        if (slackBot == null || e.isCancelled()) return;
        if (isPlayerIgnored(e.getPlayer().getUniqueId())) return;
        String playerMessage = e.getMessage();
        String playerName = e.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + e.getPlayer().getUniqueId();

        slackBot.sendMessage(playerMessage, playerName, icon);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent playerDeathEvent) {
        if (slackBot == null) return;
        if (isPlayerIgnored(playerDeathEvent.getEntity().getUniqueId())) return;
        if (playerDeathEvent.getDeathMessage() == null) return;
        String deathMessage = plugin.getConfig().getString(ConfigConstants.I18N_DEATH) + playerDeathEvent.getDeathMessage();
        String playerName = playerDeathEvent.getEntity().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + playerDeathEvent.getEntity().getUniqueId();

        slackBot.sendMessage(deathMessage, playerName, icon);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent e){
        if (slackBot == null) return;
        if (isPlayerIgnored(e.getPlayer().getUniqueId())) return;
        var display = e.getAdvancement().getDisplay();
        if (display == null || !display.shouldAnnounceChat()) return;
        String advancementName = display.getTitle();
        String message = plugin.getConfig().getString(ConfigConstants.I18N_ADVANCEMENT_DONE) + advancementName;
        String playerName = e.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + e.getPlayer().getUniqueId();

        slackBot.sendMessage(message, playerName, icon);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (slackBot == null || e.isCancelled()) return;
        if (isPlayerIgnored(e.getPlayer().getUniqueId())) return;
        if (!plugin.getConfig().getBoolean(ConfigConstants.OPTIONS_ECHO_COMMANDS)) {
            return;
        }

        String message = plugin.getConfig().getString(ConfigConstants.I18N_COMMAND_EXECUTED) + e.getMessage().split("\\s+", 2)[0];
        String playerName = e.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + e.getPlayer().getUniqueId();

        slackBot.sendMessage(message, playerName, icon);
    }
}
