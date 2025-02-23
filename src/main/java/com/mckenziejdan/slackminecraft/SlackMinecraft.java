package com.mckenziejdan.slackminecraft;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class SlackMinecraft extends JavaPlugin{
    public static SlackBot slackBot;
    public static SlackMinecraft instance;
    private boolean slackEnabled = false;

    @Override
    public void onEnable(){
        instance = this;

        final File configFile = new File(this.getDataFolder() + "/config.yml");
        if(!configFile.exists()){
            this.saveDefaultConfig();
        }
        this.getConfig().options().copyDefaults(true);

        try {
            getConfig().save(configFile);
        } catch(IOException ignored) {
        }

        if(getConfig().getBoolean("slack.enabled")) {
            slackBot = new SlackBot(getConfig().getString("slack.token"), getConfig().getString("slack.channel"));
            slackEnabled = true;
        }

        registerListeners();
    }

    @Override
    public void onDisable(){
        if(slackEnabled){
            try {
                slackBot.sendMessage(getConfig().getString("i18n.disconnected"), null, null);
                slackBot.stop();
            } catch (Exception e) {
                getLogger().severe("Error stopping Slack bot: " + e.getMessage());
                e.printStackTrace();
            }
        }
        slackBot = null;
        instance = null;
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerListener(slackBot), this);
    }
}
