package com.mckenziejdan.slackminecraft;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.model.User;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlackDeliveryTest {
    private final MethodsClient api = mock(MethodsClient.class);
    private final SlackDirectory directory = new SlackDirectory();
    private final Logger logger = mock(Logger.class);
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final FakeTiming timing = new FakeTiming();
    private final SlackDelivery delivery = new SlackDelivery(api, directory,
            new SlackDelivery.Settings("C123", "§aMinecraft", "bot-icon", "fallback-icon", true),
            logger, stopping::get, timing);
    private final List<Long> attempts = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenAnswer(invocation -> {
            attempts.add(timing.nanoTime());
            return success();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"30", "120"})
    void exhaustedRetriesPreserveCooldownForNextMessage(String finalDelay) throws Exception {
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenAnswer(invocation -> {
            attempts.add(timing.nanoTime());
            if (attempts.size() <= 3) throw httpError(429, attempts.size() == 3 ? finalDelay : "1");
            return success();
        });
        send("first");
        assertEquals(3, attempts.size());
        send("next");
        assertEquals(List.of(0L, seconds(1), seconds(2), seconds(2 + Long.parseLong(finalDelay))), attempts);
    }

    @Test
    void longCooldownDropsCurrentMessageAndDelaysNextMessage() throws Exception {
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenAnswer(invocation -> {
            attempts.add(timing.nanoTime());
            if (attempts.size() == 1) throw httpError(429, "120");
            return success();
        });
        send("first");
        assertEquals(1, attempts.size());
        send("next");
        assertEquals(List.of(0L, seconds(120)), attempts);
    }

    @Test
    void successfulRetryUsesSameRequestAndHonoursRetryAfter() throws Exception {
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenAnswer(invocation -> {
            attempts.add(timing.nanoTime());
            if (attempts.size() == 1) throw httpError(429, "5");
            return success();
        });
        send("hello");
        ArgumentCaptor<ChatPostMessageRequest> requests = ArgumentCaptor.forClass(ChatPostMessageRequest.class);
        verify(api, times(2)).chatPostMessage(requests.capture());
        assertSame(requests.getAllValues().get(0), requests.getAllValues().get(1));
        assertEquals(List.of(0L, seconds(5)), attempts);
    }

    @Test
    void uncertainDeliveryIsNotRetriedOrLoggedWithPrivateContent() throws Exception {
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenThrow(new IOException("secret-token private-message"));
        send("private-message");
        verify(api).chatPostMessage(any(ChatPostMessageRequest.class));
        verify(logger).warning("Slack message delivery failed; not retrying an uncertain delivery.");
        verifyNoMoreInteractions(logger);
    }

    @Test
    void nonRateLimitHttpErrorsAreNotRetried() throws Exception {
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenThrow(httpError(500, null));
        send("hello");
        verify(api).chatPostMessage(any(ChatPostMessageRequest.class));
    }

    @Test
    void apiRejectionsAreNotRetried() throws Exception {
        ChatPostMessageResponse rejected = new ChatPostMessageResponse();
        rejected.setError("not_in_channel");
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(rejected);
        send("hello");
        verify(api).chatPostMessage(any(ChatPostMessageRequest.class));
        verify(logger).warning("Slack rejected a message: not_in_channel");
    }

    @Test
    void normalSendsArePacedOneSecondApart() throws Exception {
        send("first");
        send("second");
        send("third");
        assertEquals(List.of(0L, seconds(1), seconds(2)), attempts);
    }

    @Test
    void requestPreservesEscapingMentionsAndPlayerIdentity() throws Exception {
        User user = new User();
        user.setId("U123");
        user.setName("dan");
        UsersListResponse users = new UsersListResponse();
        users.setOk(true);
        users.setMembers(List.of(user));
        when(api.usersList(any(UsersListRequest.class))).thenReturn(users);
        directory.refresh(api);
        delivery.send(new SlackDelivery.Message("§aHi @dan & <!channel>", "§bPlayer", "player-icon"));
        ArgumentCaptor<ChatPostMessageRequest> request = ArgumentCaptor.forClass(ChatPostMessageRequest.class);
        verify(api).chatPostMessage(request.capture());
        assertEquals("C123", request.getValue().getChannel());
        assertEquals("Hi <@U123> &amp; &lt;!channel&gt;", request.getValue().getText());
        assertEquals("Player", request.getValue().getUsername());
        assertEquals("player-icon", request.getValue().getIconUrl());
        assertEquals(false, request.getValue().isUnfurlLinks());
        assertEquals(false, request.getValue().isUnfurlMedia());
    }

    @Test
    void botAndPlayerFallbackIconsStayDistinct() throws Exception {
        send("bot");
        delivery.send(new SlackDelivery.Message("player", "Player", null));
        ArgumentCaptor<ChatPostMessageRequest> requests = ArgumentCaptor.forClass(ChatPostMessageRequest.class);
        verify(api, times(2)).chatPostMessage(requests.capture());
        assertEquals("Minecraft", requests.getAllValues().get(0).getUsername());
        assertEquals("bot-icon", requests.getAllValues().get(0).getIconUrl());
        assertEquals("fallback-icon", requests.getAllValues().get(1).getIconUrl());
    }

    @Test
    void stoppingDuringPacingPreventsNextSend() throws Exception {
        send("first");
        timing.onSleep = () -> stopping.set(true);
        send("next");
        verify(api).chatPostMessage(any(ChatPostMessageRequest.class));
    }

    @Test
    void interruptedRetryWaitPropagatesWithoutAnotherSend() throws Exception {
        when(api.chatPostMessage(any(ChatPostMessageRequest.class))).thenThrow(httpError(429, "5"));
        timing.interrupt = true;
        assertThrows(InterruptedException.class, () -> send("hello"));
        verify(api).chatPostMessage(any(ChatPostMessageRequest.class));
    }

    @Test
    void offlineNotificationSkipsCooldownAndDoesNotRetry() throws Exception {
        when(api.chatPostMessage(any(ChatPostMessageRequest.class)))
                .thenThrow(httpError(429, "120"), httpError(429, "1"));
        send("first");
        stopping.set(true);
        send("discarded");
        delivery.sendOffline("offline");
        ArgumentCaptor<ChatPostMessageRequest> requests = ArgumentCaptor.forClass(ChatPostMessageRequest.class);
        verify(api, times(2)).chatPostMessage(requests.capture());
        assertEquals("offline", requests.getAllValues().get(1).getText());
        assertEquals(0, timing.nanoTime());
    }

    @Test
    void blankMessagesDoNotReachSlack() throws Exception {
        send(null);
        send("  ");
        delivery.sendOffline("");
        verifyNoInteractions(api);
    }

    @Test
    void rateLimitHeaderIsValidated() {
        assertEquals(1, SlackDelivery.retryAfterSeconds(null));
        assertEquals(1, SlackDelivery.retryAfterSeconds("bad"));
        assertEquals(1, SlackDelivery.retryAfterSeconds("-10"));
        assertEquals(30, SlackDelivery.retryAfterSeconds("30"));
        assertEquals(3600, SlackDelivery.retryAfterSeconds("99999999"));
    }

    private void send(String text) throws InterruptedException {
        delivery.send(new SlackDelivery.Message(text, null, null));
    }

    private static long seconds(long value) {
        return TimeUnit.SECONDS.toNanos(value);
    }

    private static ChatPostMessageResponse success() {
        ChatPostMessageResponse response = new ChatPostMessageResponse();
        response.setOk(true);
        return response;
    }

    private static SlackApiException httpError(int code, String retryAfter) {
        Response.Builder response = new Response.Builder().request(new Request.Builder().url("https://example.invalid/").build())
                .protocol(Protocol.HTTP_1_1).code(code).message("test response");
        if (retryAfter != null) response.header("Retry-After", retryAfter);
        return new SlackApiException(response.build(), "{}");
    }

    private static final class FakeTiming implements SlackDelivery.Timing {
        private long now;
        private boolean interrupt;
        private Runnable onSleep = () -> {};

        public long nanoTime() { return now; }
        public void sleep(long nanos) throws InterruptedException {
            if (interrupt) throw new InterruptedException("test interruption");
            now += nanos;
            onSleep.run();
        }
    }
}
