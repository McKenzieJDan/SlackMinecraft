package com.mckenziejdan.slackminecraft;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import org.bukkit.ChatColor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

final class SlackDelivery {
    record Message(String text, String username, String icon) {}
    record Settings(String channelId, String botName, String botIcon, String playerIconFallback, boolean debug) {}

    interface Timing {
        long nanoTime();
        void sleep(long nanos) throws InterruptedException;
    }

    private static final Timing SYSTEM_TIMING = new Timing() {
        public long nanoTime() { return System.nanoTime(); }
        public void sleep(long nanos) throws InterruptedException { TimeUnit.NANOSECONDS.sleep(nanos); }
    };

    private final MethodsClient api;
    private final SlackDirectory directory;
    private final Settings settings;
    private final Logger logger;
    private final BooleanSupplier stopping;
    private final Timing timing;
    // Only the relay worker reads or changes the delivery deadline.
    private long nextSendNanos;

    SlackDelivery(MethodsClient api, SlackDirectory directory, Settings settings, Logger logger,
                  BooleanSupplier stopping) {
        this(api, directory, settings, logger, stopping, SYSTEM_TIMING);
    }

    SlackDelivery(MethodsClient api, SlackDirectory directory, Settings settings, Logger logger,
                  BooleanSupplier stopping, Timing timing) {
        this.api = api;
        this.directory = directory;
        this.settings = settings;
        this.logger = logger;
        this.stopping = stopping;
        this.timing = timing;
    }

    void send(Message message) throws InterruptedException {
        send(message, false);
    }

    void sendOffline(String text) throws InterruptedException {
        // Shutdown makes one best-effort attempt without waiting for the outgoing backlog or cooldown.
        nextSendNanos = 0;
        send(new Message(text, null, null), true);
    }

    private void send(Message message, boolean offline) throws InterruptedException {
        if (message.text() == null || message.text().isBlank() || (!offline && stopping.getAsBoolean())) return;
        String text = MessageFormatter.toSlack(ChatColor.stripColor(message.text()), directory.userIds());
        var request = ChatPostMessageRequest.builder().channel(settings.channelId()).text(text)
                .username(ChatColor.stripColor(message.username() == null ? settings.botName() : message.username()))
                .iconUrl(message.username() == null ? settings.botIcon()
                        : message.icon() == null ? settings.playerIconFallback() : message.icon())
                .unfurlLinks(false).unfurlMedia(false).build();
        for (int attempt = 0; attempt < 3; attempt++) {
            long delay = nextSendNanos - timing.nanoTime();
            if (delay > 0) timing.sleep(delay);
            if (!offline && stopping.getAsBoolean()) return;
            try {
                var response = api.chatPostMessage(request);
                if (!response.isOk()) logger.warning("Slack rejected a message: " + response.getError());
                else if (settings.debug()) logger.info("Delivered a message to the configured Slack channel.");
                return;
            } catch (SlackApiException e) {
                if (e.getResponse().code() == 429) {
                    long seconds = retryAfterSeconds(e.getResponse().header("Retry-After"));
                    // The cooldown applies to the next message even when this message has no attempts left.
                    nextSendNanos = timing.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
                    if (seconds > 60) {
                        logger.warning("Slack requested a long rate-limit delay; dropping this message.");
                        return;
                    }
                    if (attempt < 2 && !offline && !stopping.getAsBoolean()) continue;
                }
                logger.warning("Slack HTTP error " + e.getResponse().code() + "; message was not retried.");
                return;
            } catch (IOException e) {
                logger.warning("Slack message delivery failed; not retrying an uncertain delivery.");
                return;
            } finally {
                nextSendNanos = Math.max(nextSendNanos, timing.nanoTime() + TimeUnit.SECONDS.toNanos(1));
            }
        }
    }

    static long retryAfterSeconds(String value) {
        try {
            return Math.max(1, Math.min(3600, Long.parseLong(value)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
