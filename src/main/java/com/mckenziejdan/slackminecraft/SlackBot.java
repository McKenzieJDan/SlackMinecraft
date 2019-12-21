package com.mckenziejdan.slackminecraft;

import com.ullink.slack.simpleslackapi.*;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.IOException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlackBot{
    private SlackSession session;
    private SlackMinecraft instance;
    private SlackChannel channel;
    private Boolean debug;

    public SlackBot(String token, String channelName){
        instance = SlackMinecraft.instance;
        debug = instance.getConfig().getBoolean("slack.debug");
        new Thread(() -> {
            init(token, channelName);
            receiveMessage();
        }).start();
    }

    private void init(String token, String channelName){
        session = SlackSessionFactory.getSlackSessionBuilder(token).withAutoreconnectOnDisconnection(true).build();
        try{
            session.connect();
        } catch(IOException e) {
            e.printStackTrace();
        }
        channel = session.findChannelByName(channelName);
        sendMessage(instance.getConfig().getString("i18n.connected"), null, null);
        if (debug) {
            instance.getLogger().info("[Slack] [Debug] Connected to " + session.getTeam().getName());
        }
    }

    public void stop(){
        try{
            session.disconnect();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    /* Send messages from Minecraft server to Slack */
    public void sendMessage(String message, String username, String icon){
        new Thread(() -> {
            ChatColor.stripColor(message);
            String formatMsg = convertMentions(message);
            SlackPreparedMessage msg = new SlackPreparedMessage.Builder().withMessage(formatMsg).build();
            SlackChatConfiguration config;

            if(username != null)
                config = SlackChatConfiguration.getConfiguration().withName(username).withIcon(icon);
            else
                config = SlackChatConfiguration.getConfiguration().withName(instance.getConfig().getString("i18n.botName")).withIcon(instance.getConfig().getString("slack.icon"));

            session.sendMessage(channel, msg, config);

            if (debug) {
                instance.getLogger().info("[Slack] [Debug] Sent \"" + formatMsg + "\" to Slack channel.");
            }
        }).start();
    }

    private String convertMentions(String message){
        final String regex = "@(.+.+)";

        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(message);

        while (matcher.find()) {
            for (SlackUser user : session.getUsers())
                if (user.getUserName().equalsIgnoreCase(matcher.group(1))) {
                    message = message.replace(matcher.group(0), "<@" + user.getId() + ">");
                    break;
                }
        }
        return message;
    }

    /* Receive messages from Slack and broadcast to Minecraft Server */
    private void receiveMessage(){
        session.addMessagePostedListener((slackMessagePosted, slackSession) -> {
            if(!slackMessagePosted.getChannel().equals(channel) || slackMessagePosted.getSender().isBot()){
                return;
            }

            String user = slackMessagePosted.getUser().getUserName();
            String message = slackMessagePosted.getMessageContent();
            String formatMsg = "[Slack] <" + user + "> " + message;

            formatMsg = convertMentionsToUser(formatMsg);
            formatMsg = parseMentions(formatMsg);
            formatMsg = formatMsg.trim();

            Bukkit.broadcastMessage(formatMsg);

            if (debug) {
                instance.getLogger().info("[Slack] [Debug] Received \"" + formatMsg + "\" from Slack channel.");
            }

        });
    }

    private String convertMentionsToUser(String message) {
        final String regex = "<@([A-Za-z0-9]*)>";

        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(message);

        while (matcher.find()) {
            for (SlackUser user : session.getUsers())
                if (user.getId().equals(matcher.group(1))) {
                    message = message.replace(matcher.group(0), "@" + user.getUserName());
                    break;
                }
        }
        return message;
    }

    private String parseMentions(String message) {
        final String regex = "(<@[A-Za-z0-9]*>)";
        message = message.replaceAll(regex, "");
        return message;
    }
}
