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
    private SlackBot slackBot;
    private SlackMinecraft instance;

    public PlayerListener(SlackBot slackBot) {
        this.slackBot = slackBot;
        instance = SlackMinecraft.instance;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent playerJoinEvent) {
        String playerName = playerJoinEvent.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + playerJoinEvent.getPlayer().getUniqueId();

        slackBot.sendMessage(instance.getConfig().getString("i18n.joinedGame"), playerName, icon);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent playerQuitEvent) {
        String playerName = playerQuitEvent.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + playerQuitEvent.getPlayer().getUniqueId();

        slackBot.sendMessage(instance.getConfig().getString("i18n.leftGame"), playerName, icon);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        String playerMessage = e.getMessage();
        String playerName = e.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + e.getPlayer().getUniqueId();

        slackBot.sendMessage(playerMessage, playerName, icon);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent playerDeathEvent) {
        String deathMessage = instance.getConfig().getString("i18n.death") + playerDeathEvent.getDeathMessage();
        String playerName = playerDeathEvent.getEntity().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + playerDeathEvent.getEntity().getUniqueId();

        slackBot.sendMessage(deathMessage, playerName, icon);
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent e){
        String rawAdvancementName = e.getAdvancement().getKey().getKey();
        String advancementName = Arrays.stream(rawAdvancementName.substring(rawAdvancementName.lastIndexOf("/") + 1).toLowerCase().split("_"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                .collect(Collectors.joining(" "));
        String message = instance.getConfig().getString("i18n.advancementDone") + advancementName;
        String playerName = e.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + e.getPlayer().getUniqueId();

        slackBot.sendMessage(message, playerName, icon);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!instance.getConfig().getBoolean("options.echoCommands")) {
            return;
        }

        String message = instance.getConfig().getString("i18n.commandExecuted") + e.getMessage();
        String playerName = e.getPlayer().getDisplayName();
        String icon = "https://www.mc-heads.net/avatar/" + e.getPlayer().getUniqueId();

        slackBot.sendMessage(message, playerName, icon);
    }
}
