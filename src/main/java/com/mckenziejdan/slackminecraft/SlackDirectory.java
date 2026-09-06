package com.mckenziejdan.slackminecraft;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.model.ConversationType;
import com.slack.api.model.User;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SlackDirectory {
    private record Cache(Map<String, String> names, Map<String, String> ids) {}
    private volatile Cache cache = new Cache(Map.of(), Map.of());

    String findChannel(MethodsClient api, String configured) throws IOException, SlackApiException {
        String name = configured.trim();
        if (name.matches("[CG][A-Z0-9]+")) return name;
        if (name.startsWith("#")) name = name.substring(1);
        String cursor = null;
        do {
            var response = api.conversationsList(ConversationsListRequest.builder()
                    .types(List.of(ConversationType.PUBLIC_CHANNEL, ConversationType.PRIVATE_CHANNEL))
                    .excludeArchived(true).limit(200).cursor(cursor).build());
            if (!response.isOk()) throw new IOException("conversations.list: " + response.getError());
            if (response.getChannels() != null) {
                for (var channel : response.getChannels()) {
                    if (name.equals(channel.getName())) return channel.getId();
                }
            }
            cursor = response.getResponseMetadata() == null ? null : response.getResponseMetadata().getNextCursor();
        } while (cursor != null && !cursor.isBlank());
        throw new IOException("Channel not found. Set slack.channel to its channel ID and invite the bot.");
    }

    void refresh(MethodsClient api) throws IOException, SlackApiException {
        Map<String, String> names = new HashMap<>();
        Map<String, String> ids = new HashMap<>();
        String cursor = null;
        do {
            var response = api.usersList(UsersListRequest.builder().limit(200).cursor(cursor).build());
            if (!response.isOk()) throw new IOException("users.list: " + response.getError());
            if (response.getMembers() != null) {
                for (User user : response.getMembers()) {
                    if (user == null || user.isBot() || user.isDeleted() || user.getId() == null) continue;
                    String display = user.getProfile() == null ? null : user.getProfile().getDisplayName();
                    String name = display == null || display.isBlank() ? user.getName() : display;
                    names.put(user.getId(), name == null || name.isBlank() ? user.getId() : name);
                    addAlias(ids, user.getName(), user.getId());
                    addAlias(ids, display, user.getId());
                }
            }
            cursor = response.getResponseMetadata() == null ? null : response.getResponseMetadata().getNextCursor();
        } while (cursor != null && !cursor.isBlank());
        // Readers see either the complete previous cache or the complete new cache.
        cache = new Cache(Map.copyOf(names), Map.copyOf(ids));
    }

    private static void addAlias(Map<String, String> ids, String alias, String id) {
        if (alias != null && !alias.isBlank()) ids.put(alias.toLowerCase(Locale.ROOT), id);
    }

    String userName(String id) {
        return id == null ? "UnknownUser" : cache.names().getOrDefault(id, id);
    }

    Map<String, String> userIds() {
        return cache.ids();
    }
}
