package com.mckenziejdan.slackminecraft;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MessageFormatter {
    private static final Pattern MINECRAFT_MENTION = Pattern.compile("(?<![\\w@])@([\\w.-]+)");
    private static final Pattern SLACK_MENTION = Pattern.compile("<@([A-Z0-9]+)>");
    private static final Pattern SLACK_LINK = Pattern.compile("<(https?://[^>|]+)(?:\\|([^>]+))?>");

    private MessageFormatter() {}

    static String toSlack(String message, Map<String, String> userIds) {
        // Escape user-controlled Slack syntax before inserting deliberate user mentions.
        String escaped = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return MINECRAFT_MENTION.matcher(escaped).replaceAll(match -> {
            String id = userIds.get(match.group(1).toLowerCase(Locale.ROOT));
            return Matcher.quoteReplacement(id == null ? match.group() : "<@" + id + ">");
        });
    }

    static String toMinecraft(String message, Function<String, String> userName) {
        String mentions = SLACK_MENTION.matcher(message).replaceAll(match ->
                Matcher.quoteReplacement("@" + userName.apply(match.group(1))));
        String links = SLACK_LINK.matcher(mentions).replaceAll(match -> Matcher.quoteReplacement(
                match.group(2) == null ? match.group(1) : match.group(2) + " (" + match.group(1) + ")"));
        return links.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
