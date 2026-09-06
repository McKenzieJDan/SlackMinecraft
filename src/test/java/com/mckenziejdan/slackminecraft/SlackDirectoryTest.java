package com.mckenziejdan.slackminecraft;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.users.UsersListRequest;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.users.UsersListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ResponseMetadata;
import com.slack.api.model.User;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlackDirectoryTest {
    private final MethodsClient api = mock(MethodsClient.class);
    private final SlackDirectory directory = new SlackDirectory();

    @Test void channelIdsAvoidChannelEnumeration() throws Exception {
        assertEquals("C123ABC", directory.findChannel(api, " C123ABC "));
        assertEquals("G123ABC", directory.findChannel(api, "G123ABC"));
        verifyNoInteractions(api);
    }
    @Test void channelLookupFollowsCursorsIncludingEmptyPages() throws Exception {
        ConversationsListResponse first = new ConversationsListResponse();
        first.setOk(true);
        first.setChannels(List.of());
        first.setResponseMetadata(cursor("page2"));
        ConversationsListResponse second = new ConversationsListResponse();
        second.setOk(true);
        second.setChannels(List.of(Conversation.builder().id("CFOUND").name("minecraft").build()));
        when(api.conversationsList(any(ConversationsListRequest.class))).thenReturn(first, second);
        assertEquals("CFOUND", directory.findChannel(api, "#minecraft"));
        verify(api).conversationsList(argThat((ConversationsListRequest request) -> "page2".equals(request.getCursor())));
    }
    @Test void lookupReportsApiFailure() throws Exception {
        ConversationsListResponse response = new ConversationsListResponse();
        response.setError("missing_scope");
        when(api.conversationsList(any(ConversationsListRequest.class))).thenReturn(response);
        assertThrows(IOException.class, () -> directory.findChannel(api, "minecraft"));
    }
    @Test void failedRefreshKeepsEntirePreviousCache() throws Exception {
        UsersListResponse initial = users(user("UOLD", "original", false));
        UsersListResponse partial = users(user("UNEW", "replacement", false));
        partial.setResponseMetadata(cursor("page2"));
        UsersListResponse failure = new UsersListResponse();
        failure.setError("ratelimited");
        when(api.usersList(any(UsersListRequest.class))).thenReturn(initial, partial, failure);
        directory.refresh(api);
        assertThrows(IOException.class, () -> directory.refresh(api));
        assertEquals("original", directory.userName("UOLD"));
        assertFalse(directory.userIds().containsKey("replacement"));
    }
    @Test void completeRefreshRemovesStaleUsersAndSkipsBots() throws Exception {
        when(api.usersList(any(UsersListRequest.class))).thenReturn(
                users(user("UOLD", "old", false)),
                users(user("UNEW", "New.Name", false),
                        user("UBOT", "bot", true)));
        directory.refresh(api);
        directory.refresh(api);
        assertEquals("UOLD", directory.userName("UOLD"));
        assertEquals("UNEW", directory.userIds().get("new.name"));
        assertFalse(directory.userIds().containsKey("bot"));
    }
    private ResponseMetadata cursor(String value) {
        ResponseMetadata metadata = new ResponseMetadata();
        metadata.setNextCursor(value);
        return metadata;
    }
    private User user(String id, String name, boolean bot) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setBot(bot);
        return user;
    }
    private UsersListResponse users(User... users) {
        UsersListResponse response = new UsersListResponse();
        response.setOk(true);
        response.setMembers(List.of(users));
        return response;
    }
}
