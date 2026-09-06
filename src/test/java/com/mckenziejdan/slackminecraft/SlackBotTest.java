package com.mckenziejdan.slackminecraft;

import com.slack.api.model.event.MessageEvent;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.Field;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlackBotTest {
    private final SlackMinecraft plugin = mock(SlackMinecraft.class);
    private final Server server = mock(Server.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private SlackBot bot;

    @BeforeEach void setUp() throws Exception {
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        bot = new SlackBot(plugin, "xoxb-test", "xapp-test", "C123");
        set("channelId", "C123");
        set("connected", true);
    }
    @Test void broadcastsOnlyWhenServerRunsScheduledTask() {
        bot.handleIncoming(message("hello &a <https://example.com|Docs>"));
        verify(server, never()).broadcastMessage(anyString());
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        verify(server).broadcastMessage("[Slack] <U123> hello &a Docs (https://example.com)");
    }
    @Test void listReadsPlayersOnlyOnServerThread() {
        bot.handleIncoming(message("!list"));
        verify(server, never()).getOnlinePlayers();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        task.getValue().run();
        verify(server).getOnlinePlayers();
    }
    @Test void filtersOtherChannelsBotsAndMessageEdits() {
        MessageEvent wrong = message("private"); wrong.setChannel("COTHER");
        MessageEvent automated = message("bot"); automated.setBotId("B123");
        MessageEvent edited = mock(MessageEvent.class); when(edited.getSubtype()).thenReturn("message_changed");
        bot.handleIncoming(wrong);
        bot.handleIncoming(automated);
        bot.handleIncoming(edited);
        verifyNoInteractions(scheduler);
    }
    @Test void queuedBroadcastDoesNotRunAfterStop() {
        bot.handleIncoming(message("hello"));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        bot.stop();
        task.getValue().run();
        verify(server, never()).broadcastMessage(anyString());
    }
    @Test void outgoingQueueIsBoundedAndRejectsMessagesAfterStop() throws Exception {
        for (int i = 0; i < 300; i++) bot.sendMessage("message", null, null);
        Field field = SlackBot.class.getDeclaredField("outgoing"); field.setAccessible(true);
        var queue = (java.util.Queue<?>) field.get(bot);
        assertEquals(256, queue.size());
        queue.clear();
        bot.stop();
        bot.sendMessage("after stop", null, null);
        assertTrue(queue.isEmpty());
    }
    private MessageEvent message(String text) {
        MessageEvent event = new MessageEvent();
        event.setChannel("C123"); event.setUser("U123"); event.setText(text);
        return event;
    }
    private void set(String name, Object value) throws Exception {
        Field field = SlackBot.class.getDeclaredField(name); field.setAccessible(true); field.set(bot, value);
    }
}
