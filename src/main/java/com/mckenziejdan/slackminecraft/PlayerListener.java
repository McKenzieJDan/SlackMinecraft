package com.mckenziejdan.slackminecraft;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.stream.Collectors;

public class PlayerListener implements Listener {
    private final SlackBot slackBot;
    private final SlackMinecraft plugin;

    public PlayerListener(SlackMinecraft plugin, SlackBot slackBot) {
        this.plugin = plugin;
        this.slackBot = slackBot;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent playerJoinEvent) {
        if (slackBot == null) return;
        String playerName = playerJoinEvent.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + playerJoinEvent.getPlayer().getUniqueId();

        slackBot.sendMessage(plugin.getConfig().getString(ConfigConstants.I18N_JOINED_GAME), playerName, icon);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent playerQuitEvent) {
        if (slackBot == null) return;
        String playerName = playerQuitEvent.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + playerQuitEvent.getPlayer().getUniqueId();

        slackBot.sendMessage(plugin.getConfig().getString(ConfigConstants.I18N_LEFT_GAME), playerName, icon);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        if (slackBot == null) return;
        String playerMessage = e.getMessage();
        String playerName = e.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + e.getPlayer().getUniqueId();

        slackBot.sendMessage(playerMessage, playerName, icon);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent playerDeathEvent) {
        if (slackBot == null) return;
        String deathMessage = plugin.getConfig().getString(ConfigConstants.I18N_DEATH) + playerDeathEvent.getDeathMessage();
        String playerName = playerDeathEvent.getEntity().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + playerDeathEvent.getEntity().getUniqueId();

        slackBot.sendMessage(deathMessage, playerName, icon);
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent e){
        if (slackBot == null) return;
        String rawAdvancementName = e.getAdvancement().getKey().getKey();
        String advancementName = Arrays.stream(rawAdvancementName.substring(rawAdvancementName.lastIndexOf("/") + 1).toLowerCase().split("_"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                .collect(Collectors.joining(" "));
        String message = plugin.getConfig().getString(ConfigConstants.I18N_ADVANCEMENT_DONE) + advancementName;
        String playerName = e.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + e.getPlayer().getUniqueId();

        slackBot.sendMessage(message, playerName, icon);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (slackBot == null) return;
        if (!plugin.getConfig().getBoolean(ConfigConstants.OPTIONS_ECHO_COMMANDS)) {
            return;
        }

        String message = plugin.getConfig().getString(ConfigConstants.I18N_COMMAND_EXECUTED) + e.getMessage();
        String playerName = e.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + e.getPlayer().getUniqueId();

        slackBot.sendMessage(message, playerName, icon);
    }
}
