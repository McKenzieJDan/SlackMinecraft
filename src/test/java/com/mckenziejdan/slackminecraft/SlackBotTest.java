package com.mckenziejdan.slackminecraft;

import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.model.event.MessageEvent;
import com.slack.api.socket_mode.SocketModeClient;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlackBotTest {
    private final SlackMinecraft plugin = mock(SlackMinecraft.class);
    private final Server server = mock(Server.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final Logger logger = mock(Logger.class);
    private final SlackBot.ConnectionFactory connections = mock(SlackBot.ConnectionFactory.class);
    private final Slack slack = mock(Slack.class);
    private final MethodsClient api = mock(MethodsClient.class);
    private final SocketModeApp socket = mock(SocketModeApp.class);
    private final SocketModeClient client = mock(SocketModeClient.class);
    private final CountDownLatch connected = new CountDownLatch(1);
    private final CountDownLatch closed = new CountDownLatch(1);
    private SlackBot bot;

    @BeforeEach
    void setUp() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set(ConfigConstants.I18N_CONNECTED, "online");
        config.set(ConfigConstants.I18N_DISCONNECTED, "offline");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(connections.createSlack(any())).thenReturn(slack);
        SlackConfig slackConfig = new SlackConfig();
        slackConfig.setStatsEnabled(false);
        when(slack.getConfig()).thenReturn(slackConfig);
        when(slack.methods("xoxb-test")).thenReturn(api);
        UsersListResponse users = new UsersListResponse();
        users.setOk(true);
        users.setMembers(List.of());
        when(api.usersList(any(UsersListRequest.class))).thenReturn(users);
        when(connections.createSocket(eq("xapp-test"), any())).thenReturn(socket);
        when(socket.getClient()).thenReturn(client);
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(success());
        doAnswer(invocation -> { connected.countDown(); return null; })
                .when(logger).info("Connected to Slack Socket Mode.");
        doAnswer(invocation -> { closed.countDown(); return null; }).when(slack).close();
        bot = new SlackBot(plugin, "xoxb-test", "xapp-test", "C123", connections);
    }

    @AfterEach
    void stopWorker() {
        bot.stop();
    }

    @Test
    void broadcastsOnlyWhenServerRunsScheduledTask() throws Exception {
        startBot();
        bot.handleIncoming(message("hello &a <https://example.com|Docs>"));
        verify(server, never()).broadcastMessage(anyString());
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        verify(server).broadcastMessage("[Slack] <U123> hello &a Docs (https://example.com)");
    }

    @Test
    void listReadsPlayersOnlyOnServerThread() throws Exception {
        startBot();
        bot.handleIncoming(message("!list"));
        verify(server, never()).getOnlinePlayers();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        verify(server).getOnlinePlayers();
    }

    @Test
    void filtersOtherChannelsBotsAndMessageEdits() throws Exception {
        startBot();
        MessageEvent wrong = message("private");
        wrong.setChannel("COTHER");
        MessageEvent automated = message("bot");
        automated.setBotId("B123");
        MessageEvent edited = mock(MessageEvent.class);
        when(edited.getSubtype()).thenReturn("message_changed");
        bot.handleIncoming(wrong);
        bot.handleIncoming(automated);
        bot.handleIncoming(edited);
        verifyNoInteractions(scheduler);
    }

    @Test
    void queuedBroadcastDoesNotRunAfterStop() throws Exception {
        startBot();
        bot.handleIncoming(message("hello"));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        bot.stop();
        task.getValue().run();
        verify(server, never()).broadcastMessage(anyString());
    }

    @Test
    void outgoingQueueHasCapacityOf256AndLimitsWarnings() {
        for (int i = 0; i < 256; i++) bot.sendMessage("message", null, null);
        verify(logger, never()).warning(anyString());
        for (int i = 0; i < 44; i++) bot.sendMessage("overflow", null, null);
        verify(logger).warning("Slack queue is full (256 messages); dropping new messages until it recovers.");
        bot.stop();
        bot.sendMessage("after stop", null, null);
        verifyNoMoreInteractions(logger);
    }

    @Test
    void channelLookupFailureClosesHttpResources() throws Exception {
        ConversationsListResponse failure = new ConversationsListResponse();
        failure.setError("missing_scope");
        when(api.conversationsList(any(ConversationsListRequest.class))).thenReturn(failure);
        bot = new SlackBot(plugin, "xoxb-test", "xapp-test", "minecraft", connections);
        bot.start();
        awaitCleanup();
        verify(connections, never()).createSocket(anyString(), any());
        verify(api, never()).chatPostMessage(any(ChatPostMessageRequest.class));
    }

    @Test
    void socketConstructionFailureClosesHttpResources() throws Exception {
        when(connections.createSocket(anyString(), any())).thenThrow(new IOException("private diagnostic"));
        bot.start();
        awaitCleanup();
        verify(api, never()).chatPostMessage(any(ChatPostMessageRequest.class));
        verify(logger).severe(argThat((String text) -> text.contains("IOException") && !text.contains("private diagnostic")));
    }

    @Test
    void interruptedSocketStartupClosesAllCreatedResources() throws Exception {
        doThrow(new InterruptedException("startup interrupted")).when(socket).startAsync();
        bot.start();
        awaitCleanup();
        verify(socket).startAsync();
        verify(socket).close();
        verify(client).close();
        verify(api, never()).chatPostMessage(any(ChatPostMessageRequest.class));
        verify(logger, never()).warning("Slack connection failed; retrying in five seconds.");
    }

    @Test
    void terminalSocketStartupFailureClosesResourcesAfterThreeAttempts() throws Exception {
        doThrow(new IOException("startup failed")).when(socket).startAsync();
        bot.start();
        assertTrue(closed.await(15, TimeUnit.SECONDS), "Relay did not finish its bounded startup attempts");
        bot.stop();
        verify(socket, times(3)).startAsync();
        verify(socket).close();
        verify(client).close();
        verify(slack).close();
        verify(api, never()).chatPostMessage(any(ChatPostMessageRequest.class));
    }

    @Test
    void socketCloseFailureStillClosesUnderlyingClientAndHttpResources() throws Exception {
        doThrow(new IOException("close failed")).when(socket).close();
        startBot();
        bot.stop();
        var order = inOrder(socket, client, slack);
        order.verify(socket).close();
        order.verify(client).close();
        order.verify(slack).close();
    }

    @Test
    void stopDuringSendDiscardsBacklogAndMakesOneOfflineAttempt() throws Exception {
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenAnswer(invocation -> {
            ChatPostMessageRequest request = invocation.getArgument(0);
            if (request.getText().equals("blocked")) {
                sending.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted send", e);
                }
            }
            return success();
        });
        try {
            startBot();
            bot.sendMessage("blocked", null, null);
            assertTrue(sending.await(5, TimeUnit.SECONDS));
            bot.sendMessage("queued", null, null);
            assertTimeout(Duration.ofSeconds(4), bot::stop);
            bot.sendMessage("after stop", null, null);
            bot.handleIncoming(message("after stop"));
            ArgumentCaptor<ChatPostMessageRequest> requests = ArgumentCaptor.forClass(ChatPostMessageRequest.class);
            verify(api, times(3)).chatPostMessage(requests.capture());
            assertEquals(List.of("online", "blocked", "offline"),
                    requests.getAllValues().stream().map(ChatPostMessageRequest::getText).toList());
            verifyNoInteractions(scheduler);
            verify(client).close();
            verify(slack).close();
        } finally {
            release.countDown();
        }
    }

    @Test
    void stopReturnsWithinBoundWhileUninterruptibleSendFinishesInBackground() throws Exception {
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenAnswer(invocation -> {
            ChatPostMessageRequest request = invocation.getArgument(0);
            if (request.getText().equals("blocked")) {
                sending.countDown();
                boolean waiting = true;
                while (waiting) {
                    try {
                        release.await();
                        waiting = false;
                    } catch (InterruptedException ignored) {
                        // Simulate an HTTP operation that does not finish when interrupted.
                    }
                }
            }
            return success();
        });
        try {
            startBot();
            bot.sendMessage("blocked", null, null);
            assertTrue(sending.await(5, TimeUnit.SECONDS));
            assertTimeout(Duration.ofSeconds(4), bot::stop);
            verify(logger).warning("Slack cleanup is still finishing in the background.");
            verify(slack, never()).close();
        } finally {
            release.countDown();
        }
        awaitCleanup();
        verify(socket).close();
        verify(client).close();
    }

    private void startBot() throws InterruptedException {
        bot.start();
        assertTrue(connected.await(5, TimeUnit.SECONDS), "Relay did not connect");
    }

    private void awaitCleanup() throws Exception {
        assertTrue(closed.await(5, TimeUnit.SECONDS), "Relay did not close its HTTP resources");
        bot.stop();
        verify(slack).close();
    }

    private static ChatPostMessageResponse success() {
        ChatPostMessageResponse response = new ChatPostMessageResponse();
        response.setOk(true);
        return response;
    }

    private static MessageEvent message(String text) {
        MessageEvent event = new MessageEvent();
        event.setChannel("C123");
        event.setUser("U123");
        event.setText(text);
        return event;
    }
}
