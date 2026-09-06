package com.mckenziejdan.slackminecraft;

import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.event.MessageEvent;
import com.slack.api.socket_mode.SocketModeClient;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SlackBot {
    interface ConnectionFactory {
        Slack createSlack(SlackConfig config);
        SocketModeApp createSocket(String appToken, App app) throws IOException;
    }

    private static final ConnectionFactory CONNECTIONS = new ConnectionFactory() {
        public Slack createSlack(SlackConfig config) { return Slack.getInstance(config); }
        public SocketModeApp createSocket(String appToken, App app) throws IOException {
            return new SocketModeApp(appToken, app, SocketModeClient.Backend.JavaWebSocket);
        }
    };

    private final SlackMinecraft plugin;
    private final SlackDirectory directory = new SlackDirectory();
    private final ArrayBlockingQueue<SlackDelivery.Message> outgoing = new ArrayBlockingQueue<>(256);
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicLong lastQueueWarning = new AtomicLong();
    private final String botToken;
    private final String appToken;
    private final String channel;
    private final String botName;
    private final String botIcon;
    private final String playerIconFallback;
    private final String connectedMessage;
    private final String disconnectedMessage;
    private final String incomingFormat;
    private final long refreshMinutes;
    private final boolean debug;
    private final Thread worker;
    private final ConnectionFactory connections;
    private volatile String channelId;
    private volatile boolean connected;
    // Network resources are owned and closed by the worker, including during startup failure.
    private Slack slack;
    private SocketModeApp socket;
    private MethodsClient api;
    private SlackDelivery delivery;

    public SlackBot(SlackMinecraft plugin, String botToken, String appToken, String channel) {
        this(plugin, botToken, appToken, channel, CONNECTIONS);
    }

    SlackBot(SlackMinecraft plugin, String botToken, String appToken, String channel, ConnectionFactory connections) {
        this.connections = connections;
        this.plugin = plugin;
        this.botToken = botToken;
        this.appToken = appToken;
        this.channel = channel;
        var config = plugin.getConfig();
        botName = config.getString(ConfigConstants.I18N_BOT_NAME, "Minecraft");
        botIcon = config.getString(ConfigConstants.SLACK_ICON, "");
        playerIconFallback = config.getString(ConfigConstants.SLACK_PLAYER_ICON_FALLBACK, "");
        connectedMessage = config.getString(ConfigConstants.I18N_CONNECTED, ":white_check_mark: Online");
        disconnectedMessage = config.getString(ConfigConstants.I18N_DISCONNECTED, ":x: Offline");
        incomingFormat = validateFormat(config.getString(ConfigConstants.I18N_SLACK_TO_MINECRAFT_FORMAT, "[Slack] <%s> %s"));
        refreshMinutes = config.getLong(ConfigConstants.SLACK_CACHE_REFRESH_MINUTES, 60);
        debug = config.getBoolean(ConfigConstants.SLACK_DEBUG);
        worker = new Thread(this::run, "SlackMinecraft-relay");
        worker.setDaemon(true);
    }

    public void start() {
        worker.start();
    }

    private String validateFormat(String format) {
        try {
            String.format(format, "player", "message");
            return format;
        } catch (IllegalFormatException e) {
            plugin.getLogger().warning("Invalid i18n.slackToMinecraftFormat; using [Slack] <%s> %s.");
            return "[Slack] <%s> %s";
        }
    }

    private void run() {
        try {
            SlackConfig config = new SlackConfig();
            config.setHttpClientCallTimeoutMillis(5000);
            config.setHttpClientReadTimeoutMillis(5000);
            config.setStatsEnabled(false);
            slack = connections.createSlack(config);
            api = slack.methods(botToken);
            channelId = directory.findChannel(api, channel);
            delivery = new SlackDelivery(api, directory,
                    new SlackDelivery.Settings(channelId, botName, botIcon, playerIconFallback, debug),
                    plugin.getLogger(), stopping::get);
            if (stopping.get()) return;
            refreshUsers();
            if (stopping.get()) return;
            App app = new App(AppConfig.builder().slack(slack).singleTeamBotToken(botToken).build());
            app.event(MessageEvent.class, (payload, ctx) -> {
                handleIncoming(payload.getEvent());
                return ctx.ack();
            });
            socket = connections.createSocket(appToken, app);
            for (int attempt = 1; !stopping.get(); attempt++) {
                try {
                    socket.startAsync();
                    break;
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    if (attempt == 3) throw e;
                    plugin.getLogger().warning("Slack connection failed; retrying in five seconds.");
                    TimeUnit.SECONDS.sleep(5);
                }
            }
            if (stopping.get()) return;
            connected = true;
            plugin.getLogger().info("Connected to Slack Socket Mode.");
            delivery.send(new SlackDelivery.Message(connectedMessage, null, null));
            long nextRefresh = System.nanoTime() + TimeUnit.MINUTES.toNanos(Math.max(1, refreshMinutes));
            while (!stopping.get()) {
                SlackDelivery.Message message = outgoing.poll(1, TimeUnit.SECONDS);
                if (message != null) delivery.send(message);
                if (refreshMinutes > 0 && System.nanoTime() >= nextRefresh) {
                    refreshUsers();
                    nextRefresh = System.nanoTime() + TimeUnit.MINUTES.toNanos(refreshMinutes);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Exception bodies can contain HTTP tokens or private message content.
            plugin.getLogger().severe("Slack relay stopped (" + e.getClass().getSimpleName()
                    + "). Check tokens, channel membership, scopes and network access, then restart.");
        } finally {
            stopping.set(true);
            outgoing.clear();
            Thread.interrupted(); // Allow bounded network cleanup after an interrupted queue wait.
            try {
                if (connected && delivery != null && !disconnectedMessage.isBlank()) {
                    delivery.sendOffline(disconnectedMessage);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                connected = false;
                closeResources();
            }
        }
    }

    private void refreshUsers() {
        try {
            directory.refresh(api);
        } catch (IOException | SlackApiException e) {
            plugin.getLogger().warning("Could not refresh Slack users; keeping the previous cache. Check users:read and Slack availability.");
        }
    }

    void handleIncoming(MessageEvent event) {
        if (stopping.get() || !connected || event == null || event.getSubtype() != null
                || event.getBotId() != null || event.getUser() == null || event.getText() == null
                || !channelId.equals(event.getChannel())) return;
        String message = event.getText();
        // Queue only Minecraft work here: the Socket Mode callback must acknowledge promptly.
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (stopping.get()) return;
                switch (message.trim().toLowerCase(Locale.ROOT)) {
                    case "!list" -> {
                        String names = String.join(", ", plugin.getServer().getOnlinePlayers().stream()
                                .map(Player::getName).sorted().toList());
                        sendMessage(names.isEmpty() ? "No players online." : "Online players: " + names, null, null);
                    }
                    case "!tps", "!status" -> sendMessage(serverStatus(), null, null);
                    default -> {
                        // Translate color codes only in the administrator-controlled format.
                        String format = ChatColor.translateAlternateColorCodes('&', incomingFormat);
                        String text = MessageFormatter.toMinecraft(message, directory::userName);
                        plugin.getServer().broadcastMessage(String.format(format,
                                ChatColor.stripColor(directory.userName(event.getUser())), ChatColor.stripColor(text)));
                    }
                }
            });
        } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) {
            // The server disabled the plugin while a Socket Mode callback was in flight.
        }
    }

    private String serverStatus() {
        String tps = "TPS: unavailable on this server";
        try {
            // Paper exposes getTPS(); Spigot does not. Keep the plugin loadable on both.
            double[] values = (double[]) plugin.getServer().getClass().getMethod("getTPS").invoke(plugin.getServer());
            if (values.length >= 3) tps = String.format(Locale.ROOT,
                    "TPS (1m, 5m, 15m): %.2f, %.2f, %.2f", values[0], values[1], values[2]);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // This is an optional Paper extension.
        }
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        return "Server status:\n" + tps + "\nOnline players: " + plugin.getServer().getOnlinePlayers().size()
                + "\nMemory: " + used + " MB / " + runtime.maxMemory() / 1024 / 1024 + " MB maximum";
    }

    public void sendMessage(String text, String username, String icon) {
        if (stopping.get() || text == null || text.isBlank()) return;
        if (!outgoing.offer(new SlackDelivery.Message(text, username, icon))) {
            long now = System.nanoTime();
            long previous = lastQueueWarning.get();
            if ((previous == 0 || now - previous > TimeUnit.MINUTES.toNanos(1))
                    && lastQueueWarning.compareAndSet(previous, now)) {
                plugin.getLogger().warning("Slack queue is full (256 messages); dropping new messages until it recovers.");
            }
        }
    }

    public void stop() {
        stopping.set(true);
        worker.interrupt();
        try {
            worker.join(3000);
            if (worker.isAlive()) plugin.getLogger().warning("Slack cleanup is still finishing in the background.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeResources() {
        try {
            if (socket != null) {
                var client = socket.getClient();
                try {
                    socket.close();
                } finally {
                    // SocketModeApp.close() disconnects but does not close the client's reconnect scheduler.
                    if (client != null) client.close();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not close the Slack socket cleanly.");
        } finally {
            try {
                if (slack != null) slack.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Could not close the Slack HTTP client cleanly.");
            }
        }
    }
}
