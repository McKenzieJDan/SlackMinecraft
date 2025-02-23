package com.mckenziejdan.slackminecraft;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.User;
import com.slack.api.rtm.RTMClient;
import com.slack.api.rtm.RTMMessageHandler;
import com.slack.api.model.Message;
import com.slack.api.socket_mode.SocketModeClient;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import com.slack.api.model.event.MessageEvent;
import com.slack.api.rtm.RTMEventHandler;
import com.slack.api.rtm.RTMEventsDispatcher;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlackBot {
    private final Slack slack;
    private final MethodsClient client;
    private final SlackMinecraft instance;
    private String channelId;
    private final Boolean debug;
    private RTMClient rtm;

    public SlackBot(String token, String channelName) {
        instance = SlackMinecraft.instance;
        debug = instance.getConfig().getBoolean("slack.debug");
        slack = Slack.getInstance();
        client = slack.methods(token);

        new Thread(() -> {
            try {
                init(channelName);
                receiveMessage(token);
            } catch (IOException | com.slack.api.methods.SlackApiException | javax.websocket.DeploymentException e) {
                instance.getLogger().severe("Failed to initialize Slack connection: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void init(String channelName) throws IOException, com.slack.api.methods.SlackApiException {
        // Find channel ID from channel name
        ConversationsListResponse channelsResponse = client.conversationsList(
            ConversationsListRequest.builder().build()
        );
        
        for (Conversation channel : channelsResponse.getChannels()) {
            if (channel.getName().equals(channelName)) {
                channelId = channel.getId();
                break;
            }
        }

        if (channelId == null) {
            instance.getLogger().severe("Could not find Slack channel: " + channelName);
            return;
        }

        sendMessage(instance.getConfig().getString("i18n.connected"), null, null);
        
        if (debug) {
            instance.getLogger().info("[Slack] [Debug] Connected to Slack");
        }
    }

    public void stop() throws Exception {
        if (rtm != null) {
            try {
                rtm.disconnect();
                slack.close();
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    }

    public void sendMessage(final String messageText, final String username, final String icon) {
        new Thread(() -> {
            try {
                final String strippedMessage = ChatColor.stripColor(messageText);
                final String formatMsg = convertMentions(strippedMessage);

                ChatPostMessageRequest.ChatPostMessageRequestBuilder requestBuilder = ChatPostMessageRequest.builder()
                    .channel(channelId)
                    .text(formatMsg);

                if (username != null) {
                    requestBuilder.username(username);
                    requestBuilder.iconUrl(icon);
                } else {
                    requestBuilder.username(instance.getConfig().getString("i18n.botName"));
                    requestBuilder.iconUrl(instance.getConfig().getString("slack.icon"));
                }

                ChatPostMessageResponse response = client.chatPostMessage(requestBuilder.build());

                if (debug) {
                    instance.getLogger().info("[Slack] [Debug] Sent \"" + formatMsg + "\" to Slack channel.");
                }
            } catch (IOException | com.slack.api.methods.SlackApiException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String convertMentions(String message) throws IOException, com.slack.api.methods.SlackApiException {
        final String regex = "@(.+.+)";
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(message);

        UsersListResponse usersResponse = client.usersList(UsersListRequest.builder().build());
        
        while (matcher.find()) {
            for (User user : usersResponse.getMembers()) {
                if (user.getName().equalsIgnoreCase(matcher.group(1))) {
                    message = message.replace(matcher.group(0), "<@" + user.getId() + ">");
                    break;
                }
            }
        }
        
        return message;
    }

    private void receiveMessage(String token) throws IOException, com.slack.api.methods.SlackApiException, javax.websocket.DeploymentException {
        try {
            rtm = slack.rtm(token);
            rtm.connect();

            rtm.addMessageHandler((String message) -> {
                // Ignore messages from bots or from other channels
                if (message == null || !message.contains(channelId)) {
                    return;
                }

                try {
                    // Parse the message JSON if needed
                    // For now, just broadcast the raw message for testing
                    Bukkit.broadcastMessage("[Slack] " + message);

                    if (debug) {
                        instance.getLogger().info("[Slack] [Debug] Received \"" + message + "\" from Slack channel.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            instance.getLogger().severe("Failed to connect to Slack RTM: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private String convertMentionsToUser(String message) throws IOException, com.slack.api.methods.SlackApiException {
        final String regex = "<@([A-Za-z0-9]*)>";
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(message);

        UsersListResponse usersResponse = client.usersList(UsersListRequest.builder().build());
        
        while (matcher.find()) {
            for (User user : usersResponse.getMembers()) {
                if (user.getId().equals(matcher.group(1))) {
                    message = message.replace(matcher.group(0), "@" + user.getName());
                    break;
                }
            }
        }
        
        return message;
    }

    private String parseMentions(String message) {
        final String regex = "(<@[A-Za-z0-9]*>)";
        return message.replaceAll(regex, "");
    }
}
