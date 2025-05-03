package com.mckenziejdan.slackminecraft;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class SlackMinecraft extends JavaPlugin{
    private SlackBot slackBot;
    private boolean slackEnabled = false;

    @Override
    public void onEnable(){
        final File configFile = new File(this.getDataFolder() + "/config.yml");
        if(!configFile.exists()){
            this.saveDefaultConfig();
        }
        this.getConfig().options().copyDefaults(true);

        try {
            getConfig().save(configFile);
        } catch(IOException e) {
            getLogger().warning("Could not save config.yml: " + e.getMessage());
        }

        if(getConfig().getBoolean(ConfigConstants.SLACK_ENABLED)) {
            String botToken = getConfig().getString(ConfigConstants.SLACK_TOKEN);
            String appToken = getConfig().getString(ConfigConstants.SLACK_APP_TOKEN);
            String channelName = getConfig().getString(ConfigConstants.SLACK_CHANNEL);
            
            if (botToken == null || botToken.isEmpty() || appToken == null || appToken.isEmpty() || channelName == null || channelName.isEmpty()) {
                 getLogger().severe("Slack Bot Token, App Token, or Channel Name is missing in config.yml! Disabling Slack integration.");
                 slackEnabled = false;
            } else {
                 this.slackBot = new SlackBot(this, botToken, appToken, channelName);
                 slackEnabled = true;
            }
        }

        registerListeners();
    }

    @Override
    public void onDisable(){
        if(slackEnabled && slackBot != null){
            try {
                slackBot.stop();
            } catch (Exception e) {
                getLogger().severe("Error stopping Slack bot: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void registerListeners() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerListener(this, this.slackBot), this);
    }
}
