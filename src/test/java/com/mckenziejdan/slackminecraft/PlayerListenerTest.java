package com.mckenziejdan.slackminecraft;

import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;

class PlayerListenerTest {
    private final SlackMinecraft plugin = mock(SlackMinecraft.class);
    private final SlackBot bot = mock(SlackBot.class);
    private final Player player = mock(Player.class);
    private final YamlConfiguration config = new YamlConfiguration();
    private final UUID id = UUID.randomUUID();
    private PlayerListener listener;

    @BeforeEach void setUp() {
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(player.getUniqueId()).thenReturn(id);
        when(player.getDisplayName()).thenReturn("Dan");
        listener = new PlayerListener(plugin, bot);
    }
    @Test void cancelledChatIsNotRelayed() {
        var event = new AsyncPlayerChatEvent(true, player, "private", new HashSet<>());
        event.setCancelled(true);
        listener.onPlayerChat(event);
        verifyNoInteractions(bot);
    }
    @Test void ignoredPlayersTakeEffectAfterReload() {
        config.set(ConfigConstants.OPTIONS_IGNORED_PLAYERS, List.of(id.toString()));
        listener.loadIgnoredPlayers();
        listener.onPlayerChat(new AsyncPlayerChatEvent(true, player, "hidden", new HashSet<>()));
        verifyNoInteractions(bot);
        config.set(ConfigConstants.OPTIONS_IGNORED_PLAYERS, List.of());
        listener.loadIgnoredPlayers();
        listener.onPlayerChat(new AsyncPlayerChatEvent(true, player, "visible", new HashSet<>()));
        verify(bot).sendMessage(eq("visible"), eq("Dan"), anyString());
    }
    @Test void commandArgumentsAreNeverRelayed() {
        config.set(ConfigConstants.OPTIONS_ECHO_COMMANDS, true);
        config.set(ConfigConstants.I18N_COMMAND_EXECUTED, "Command: ");
        listener.onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/login secret-password", new HashSet<>()));
        verify(bot).sendMessage(eq("Command: /login"), eq("Dan"), anyString());
    }
    @Test void cancelledCommandsAreNotRelayed() {
        config.set(ConfigConstants.OPTIONS_ECHO_COMMANDS, true);
        var event = new PlayerCommandPreprocessEvent(player, "/msg Dan secret", new HashSet<>());
        event.setCancelled(true);
        listener.onPlayerCommand(event);
        verifyNoInteractions(bot);
    }
    @Test void commandsDefaultToNotBeingRelayed() {
        listener.onPlayerCommand(new PlayerCommandPreprocessEvent(player, "/login secret", new HashSet<>()));
        verifyNoInteractions(bot);
    }
    @Test void recipeAdvancementsAreNotRelayed() {
        Advancement advancement = mock(Advancement.class);
        listener.onPlayerAdvancement(new PlayerAdvancementDoneEvent(player, advancement));
        verifyNoInteractions(bot);
    }
    @Test void usesAnnouncedAdvancementTitle() {
        Advancement advancement = mock(Advancement.class);
        AdvancementDisplay display = mock(AdvancementDisplay.class);
        when(advancement.getDisplay()).thenReturn(display);
        when(display.shouldAnnounceChat()).thenReturn(true);
        when(display.getTitle()).thenReturn("Stone Age");
        config.set(ConfigConstants.I18N_ADVANCEMENT_DONE, "Earned: ");
        listener.onPlayerAdvancement(new PlayerAdvancementDoneEvent(player, advancement));
        verify(bot).sendMessage(eq("Earned: Stone Age"), eq("Dan"), anyString());
    }
}
