package com.mckenziejdan.slackminecraft;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.User;
import com.slack.api.model.event.MessageEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SlackBot {
    private final SlackMinecraft instance;
    private final Boolean debug;
    private final App boltApp;
    private final MethodsClient apiClient; // Keep using MethodsClient for sending
    private SocketModeApp socketModeApp;
    private String channelId;
    private final ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutorService; // Added for periodic refresh

    // Cache fields
    private final Map<String, User> userIdMap = new ConcurrentHashMap<>();
    private final Map<String, User> userNameMap = new ConcurrentHashMap<>(); // Stores lowercase name -> User
    private volatile boolean userCachePopulated = false;

    public SlackBot(String botToken, String appToken, String channelName) {
        instance = SlackMinecraft.instance;
        debug = instance.getConfig().getBoolean("slack.debug");
        // Use a scheduled executor for periodic tasks + general tasks
        scheduledExecutorService = Executors.newScheduledThreadPool(2); 
        executorService = scheduledExecutorService; // Can use the same executor

        // Configure Bolt App
        AppConfig appConfig = AppConfig.builder().singleTeamBotToken(botToken).build();
        this.boltApp = new App(appConfig);
        this.apiClient = boltApp.client(); // Get MethodsClient from Bolt App

        // Initialize Socket Mode (will connect later)
        try {
            this.socketModeApp = new SocketModeApp(appToken, this.boltApp);
        } catch (Exception e) {
            instance.getLogger().severe("Failed to initialize Socket Mode App: " + e.getMessage());
            e.printStackTrace();
            // Handle initialization failure (e.g., prevent plugin enable)
            return;
        }

        registerEventHandlers();

        // Perform initial setup (find channel ID, connect) in background
        executorService.submit(() -> {
            try {
                findChannelId(channelName); // Find channel ID first
                // Populate user cache *after* apiClient is ready and channel is found (or attempt anyway)
                refreshUserCache(); 
                if (channelId != null) {
                    socketModeApp.start(); // Start Socket Mode connection
                    // Schedule periodic user cache refresh (e.g., every hour)
                    long refreshInterval = instance.getConfig().getLong("slack.cacheRefreshMinutes", 60);
                    scheduledExecutorService.scheduleAtFixedRate(this::refreshUserCache, refreshInterval, refreshInterval, TimeUnit.MINUTES);
                    // Send connected message *after* connection is likely established
                     sendMessage(instance.getConfig().getString("i18n.connected"), null, null);
                } else {
                     instance.getLogger().severe("Slack Bot initialization failed: Could not find channel " + channelName);
                }
            } catch (Exception e) {
                instance.getLogger().severe("Failed to start Slack Socket Mode connection or find channel: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void registerEventHandlers() {
        // Listen for Message Events
        boltApp.event(MessageEvent.class, (payload, ctx) -> {
            MessageEvent event = payload.getEvent();

            // Ignore messages from bots, subtypes (edits, deletes), or wrong channel
            if (event.getSubtype() != null || event.getBotId() != null || !event.getChannel().equals(channelId)) {
                return ctx.ack(); // Acknowledge event even if ignored
            }

            String messageText = event.getText();
            if (messageText == null) {
                return ctx.ack();
            }

            // Process message asynchronously
             executorService.submit(() -> {
                 try {
                    String senderName = getSlackUserName(event.getUser()); // Get sender's name
                    String convertedMessage = convertSlackMentionsToUsernames(messageText);
                    
                    String broadcastMessage = String.format(
                        instance.getConfig().getString("i18n.slackToMinecraftFormat", "[Slack] <%s> %s"), 
                        senderName != null ? senderName : "UnknownUser", 
                        convertedMessage
                    );

                    // Run broadcast message on main thread
                    Bukkit.getScheduler().runTask(instance, () -> {
                         Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcastMessage));
                     });

                    if (debug) {
                        instance.getLogger().info("[Slack] [Debug] Received \"" + convertedMessage + "\" from Slack user " + senderName);
                    }
                 } catch (Exception e) {
                      instance.getLogger().severe("Error processing incoming Slack message: " + e.getMessage());
                      e.printStackTrace();
                 }
             });

            return ctx.ack(); // Acknowledge the event
        });
    }
    
    private void findChannelId(String channelName) throws IOException, SlackApiException {
        // Find channel ID from channel name
        // TODO: Implement pagination for large channel lists if necessary
        ConversationsListResponse channelsResponse = apiClient.conversationsList(
                ConversationsListRequest.builder().limit(1000).build() // Increase limit if needed
        );

        if (!channelsResponse.isOk()) {
             instance.getLogger().severe("Failed to list Slack channels: " + channelsResponse.getError());
             channelId = null;
             return;
        }

        for (Conversation channel : channelsResponse.getChannels()) {
            if (channel.getName().equals(channelName)) {
                channelId = channel.getId();
                 instance.getLogger().info("Found Slack channel #" + channelName + " with ID: " + channelId);
                break;
            }
        }

        if (channelId == null) {
            instance.getLogger().severe("Could not find Slack channel: " + channelName);
        }
    }

    private void refreshUserCache() {
        instance.getLogger().info("Refreshing Slack user cache...");
        Map<String, User> newUserIdMap = new ConcurrentHashMap<>();
        Map<String, User> newUserNameMap = new ConcurrentHashMap<>();
        String cursor = null;
        int fetchedCount = 0;
        int pageCount = 0;

        try {
            do {
                pageCount++;
                UsersListRequest request = UsersListRequest.builder().limit(200).cursor(cursor).build(); // Recommended limit is 200-1000
                UsersListResponse response = apiClient.usersList(request);
                if (!response.isOk()) {
                    instance.getLogger().warning("Failed to fetch Slack user list (Page " + pageCount + "): " + response.getError());
                    // Decide if we should abort or continue with partial cache
                    break; 
                }

                for (User user : response.getMembers()) {
                    // Skip bots and null users/IDs
                    if (user == null || user.isBot() || user.isDeleted() || user.getId() == null) continue;
                    
                    fetchedCount++;
                    newUserIdMap.put(user.getId(), user);
                    if (user.getName() != null) {
                         newUserNameMap.put(user.getName().toLowerCase(), user);
                    }
                     // Also cache by display name if available and different
                    if (user.getProfile() != null && 
                        user.getProfile().getDisplayName() != null && 
                        !user.getProfile().getDisplayName().isEmpty() && 
                        !user.getProfile().getDisplayName().equalsIgnoreCase(user.getName())) {
                            newUserNameMap.put(user.getProfile().getDisplayName().toLowerCase(), user);
                        }
                }
                cursor = response.getResponseMetadata() != null ? response.getResponseMetadata().getNextCursor() : null;
            } while (cursor != null && !cursor.isEmpty());

            // Atomically replace the old maps with the new ones
            userIdMap.clear();
            userIdMap.putAll(newUserIdMap);
            userNameMap.clear();
            userNameMap.putAll(newUserNameMap);
            userCachePopulated = !userIdMap.isEmpty(); // Mark as populated if we got any users

            instance.getLogger().info("Slack user cache refreshed. Found " + userIdMap.size() + " users.");

        } catch (IOException | SlackApiException e) {
            instance.getLogger().severe("Error refreshing Slack user cache: " + e.getMessage());
            e.printStackTrace();
            // Keep old cache if refresh fails?
        } catch (Exception e) {
            instance.getLogger().severe("Unexpected error during user cache refresh: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Method to get user name from ID using cache
    private String getSlackUserName(String userId) {
        if (userId == null) return null;
        User user = userIdMap.get(userId);
        if (user != null) {
             // Prefer display name, fallback to name
             if (user.getProfile() != null && user.getProfile().getDisplayName() != null && !user.getProfile().getDisplayName().isEmpty()) {
                 return user.getProfile().getDisplayName();
             }
            return user.getName();
        }
        // Optionally: Trigger a cache refresh or log missing user? For now, fallback.
        instance.getLogger().fine("Slack user ID not found in cache: " + userId);
        return userId; // Fallback to ID if not found
    }

    public void stop() {
        // Send disconnect message before stopping
        if (channelId != null && socketModeApp != null) {
            try {
                // Use API client directly to send the final message synchronously if needed
                 apiClient.chatPostMessage(req -> req
                     .channel(channelId)
                     .text(instance.getConfig().getString("i18n.disconnected"))
                     .username(instance.getConfig().getString("i18n.botName"))
                     .iconUrl(instance.getConfig().getString("slack.icon"))
                 );
            } catch (IOException | SlackApiException e) {
                instance.getLogger().warning("Failed to send disconnect message to Slack: " + e.getMessage());
            }
        }
        
        // Stop Socket Mode client
        if (socketModeApp != null) {
            try {
                socketModeApp.stop();
                socketModeApp.close();
            } catch (Exception e) {
                 instance.getLogger().severe("Error stopping Slack Socket Mode client: " + e.getMessage());
                e.printStackTrace();
            }
        }
        // Shutdown executor service
        executorService.shutdown();
        scheduledExecutorService.shutdown(); // Shutdown scheduled executor too
        try {
            if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow();
            }
             if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                 executorService.shutdownNow();
             }
        } catch (InterruptedException e) {
            scheduledExecutorService.shutdownNow();
             executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // No need to explicitly close boltApp or apiClient normally
    }

    // Send message remains largely the same, but uses the injected apiClient
    public void sendMessage(final String messageText, final String username, final String icon) {
         // Don't send if channel ID wasn't found
         if (channelId == null) {
             instance.getLogger().warning("Cannot send message to Slack, channel ID is not set.");
             return;
         }

        executorService.submit(() -> {
            try {
                final String strippedMessage = ChatColor.stripColor(messageText);
                final String formatMsg = convertMinecraftMentionsToSlackTags(strippedMessage);

                ChatPostMessageRequest.ChatPostMessageRequestBuilder requestBuilder = ChatPostMessageRequest.builder()
                        .channel(channelId)
                        .text(formatMsg);

                if (username != null) {
                    requestBuilder.username(username);
                    // Use icon URL if provided, otherwise fallback to config (ensure config key exists)
                    requestBuilder.iconUrl(icon != null ? icon : instance.getConfig().getString("slack.playerIconUrlFallback", null)); 
                } else {
                    requestBuilder.username(instance.getConfig().getString("i18n.botName", "Minecraft Bot"));
                    requestBuilder.iconUrl(instance.getConfig().getString("slack.icon", null)); // Use configured bot icon
                }

                ChatPostMessageResponse response = apiClient.chatPostMessage(requestBuilder.build());

                 if (!response.isOk()) {
                      instance.getLogger().warning("Failed to send message to Slack: " + response.getError());
                      if (response.getErrors() != null) {
                           response.getErrors().forEach(err -> instance.getLogger().warning(" - " + err.toString()));
                      }
                 } else if (debug) {
                    instance.getLogger().info("[Slack] [Debug] Sent \"" + formatMsg + "\" to Slack channel " + channelId);
                }
            } catch (IOException | SlackApiException e) {
                 instance.getLogger().severe("Error sending message to Slack: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) { // Catch unexpected errors
                instance.getLogger().severe("Unexpected error sending Slack message: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // Renamed from convertMentions
    private String convertMinecraftMentionsToSlackTags(String message) {
        if (!userCachePopulated) { // Don't attempt conversion if cache isn't ready
             instance.getLogger().fine("User cache not populated, skipping Minecraft mention conversion.");
             return message;
         }
        final String regex = "@([\\w.]+)"; // Matches @ followed by word characters or dots
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(message);
        StringBuffer sb = new StringBuffer(); // Use StringBuffer for efficient replacement

        // Use cache
        while (matcher.find()) {
            String mentionName = matcher.group(1).toLowerCase(); // Use lowercase for lookup
            User user = userNameMap.get(mentionName);
            
            // Replace if found, otherwise keep original mention
            if (user != null) {
                matcher.appendReplacement(sb, "<@" + user.getId() + ">");
            } else {
                matcher.appendReplacement(sb, matcher.group(0)); // Keep original @mention if user not found
            }
        }
        
        matcher.appendTail(sb);
        return sb.toString();
    }


    // Renamed from convertMentionsToUser
    private String convertSlackMentionsToUsernames(String message) {
        if (!userCachePopulated) { // Don't attempt conversion if cache isn't ready
             instance.getLogger().fine("User cache not populated, skipping Slack mention conversion.");
             return message;
         }
        final String regex = "<@([A-Z0-9]+)>"; // Slack user IDs are typically uppercase alphanumeric
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(message);
        StringBuffer sb = new StringBuffer();

        // Use cache
        while (matcher.find()) {
            String userId = matcher.group(1);
            String userName = getSlackUserName(userId); // Use the helper method which checks cache
            
            // Replace with @Username if found (and not null/empty), otherwise keep original tag
            if (userName != null && !userName.equals(userId)) { // Check if we got a name back, not just the ID fallback
                matcher.appendReplacement(sb, "@" + userName);
            } else {
                matcher.appendReplacement(sb, matcher.group(0)); // Keep original <@...> if user not found
            }
        }

        matcher.appendTail(sb);
        return sb.toString();
    }
    
    // Unused method removed: parseMentions
}
