package com.mckenziejdan.slackminecraft;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IgnoreCommandTest {
    private static final UUID EXISTING = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final SlackMinecraft plugin = mock(SlackMinecraft.class);
    private final SlackBot bot = mock(SlackBot.class);
    private final CommandSender sender = mock(CommandSender.class);
    private final Command command = mock(Command.class);
    private final YamlConfiguration config = new YamlConfiguration();
    @TempDir Path temporaryDirectory;
    private Path configFile;
    private PlayerListener listener;
    private IgnoreCommand handler;

    @BeforeEach
    void setUp() {
        configFile = temporaryDirectory.resolve("config.yml");
        config.set(ConfigConstants.SLACK_CHANNEL, "CEXISTING");
        config.set(ConfigConstants.OPTIONS_IGNORED_PLAYERS, List.of(EXISTING.toString()));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(sender.hasPermission("slackminecraft.admin")).thenReturn(true);
        doAnswer(invocation -> { config.save(configFile.toFile()); return null; }).when(plugin).saveConfig();
        listener = new PlayerListener(plugin, bot);
        handler = new IgnoreCommand(plugin, listener);
    }

    @Test
    void permissionDenialDoesNotResolvePlayersChangeConfigOrSuggestNames() {
        when(sender.hasPermission("slackminecraft.admin")).thenReturn(false);
        try (var bukkit = mockStatic(Bukkit.class)) {
            assertTrue(run("add", "Dan"));
            assertTrue(handler.onTabComplete(sender, command, "smc", new String[]{"ignore", "add", "D"}).isEmpty());
            assertEquals(List.of(EXISTING.toString()), ignored(config));
            verify(plugin, never()).saveConfig();
            bukkit.verifyNoInteractions();
        }
    }

    @Test
    void addPreservesSettingsAndExistingUuidsAndUpdatesLiveSnapshot() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            assertTrue(run("add", TARGET.toString()));
            bukkit.verifyNoInteractions();
        }
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(configFile.toFile());
        assertEquals("CEXISTING", saved.getString(ConfigConstants.SLACK_CHANNEL));
        assertEquals(List.of(EXISTING.toString(), TARGET.toString()), ignored(saved));
        relay(listener, TARGET);
        relay(listener, EXISTING);
        verifyNoInteractions(bot);
        verify(plugin).saveConfig();

        when(plugin.getConfig()).thenReturn(saved);
        PlayerListener restartedListener = new PlayerListener(plugin, bot);
        relay(restartedListener, TARGET);
        verifyNoInteractions(bot);
    }

    @Test
    void removePersistsOtherUuidsAndResumesRelayImmediately() {
        config.set(ConfigConstants.OPTIONS_IGNORED_PLAYERS, List.of(EXISTING.toString(), TARGET.toString()));
        listener.loadIgnoredPlayers();
        assertTrue(run("remove", TARGET.toString()));
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(configFile.toFile());
        assertEquals(List.of(EXISTING.toString()), ignored(saved));
        assertEquals("CEXISTING", saved.getString(ConfigConstants.SLACK_CHANNEL));
        relay(listener, TARGET);
        relay(listener, EXISTING);
        verify(bot).sendMessage(eq("hello"), eq("Player"), anyString());
        verifyNoMoreInteractions(bot);
        verify(plugin).saveConfig();
    }

    @Test
    void duplicateAddAndMissingRemovalDoNotWriteConfiguration() {
        run("add", EXISTING.toString());
        run("remove", TARGET.toString());
        assertEquals(List.of(EXISTING.toString()), ignored(config));
        verify(plugin, never()).saveConfig();
    }

    @Test
    void onlineNameResolvesWithoutOfflineLookup() {
        Player online = mock(Player.class);
        when(online.getUniqueId()).thenReturn(TARGET);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Dan")).thenReturn(online);
            run("add", "Dan");
            bukkit.verify(() -> Bukkit.getPlayerExact("Dan"));
            bukkit.verifyNoMoreInteractions();
        }
        assertTrue(ignored(config).contains(TARGET.toString()));
    }

    @Test
    void previouslyJoinedNameResolvesFromLocalPlayers() {
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getName()).thenReturn("Dan");
        when(offline.getUniqueId()).thenReturn(TARGET);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[]{offline});
            run("add", "dan");
            bukkit.verify(() -> Bukkit.getPlayerExact("dan"));
            bukkit.verify(Bukkit::getOfflinePlayers);
            bukkit.verifyNoMoreInteractions();
        }
        assertTrue(ignored(config).contains(TARGET.toString()));
    }

    @Test
    void unknownNameDoesNotUseRemoteNameLookupOrChangeConfiguration() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[0]);
            run("add", "UnknownPlayer");
            bukkit.verify(() -> Bukkit.getPlayerExact("UnknownPlayer"));
            bukkit.verify(Bukkit::getOfflinePlayers);
            bukkit.verifyNoMoreInteractions();
        }
        assertEquals(List.of(EXISTING.toString()), ignored(config));
        verify(plugin, never()).saveConfig();
    }

    @Test
    void invalidCommandArgumentsDoNotChangeConfiguration() {
        handler.onCommand(sender, command, "smc", new String[]{"ignore", "add"});
        handler.onCommand(sender, command, "smc", new String[]{"ignore", "remove"});
        handler.onCommand(sender, command, "smc", new String[]{"ignore", "unknown"});
        assertEquals(List.of(EXISTING.toString()), ignored(config));
        verify(plugin, never()).saveConfig();
    }

    @Test
    void listShowsKnownNamesAndKeepsInvalidStoredEntriesVisible() {
        config.set(ConfigConstants.OPTIONS_IGNORED_PLAYERS, List.of(EXISTING.toString(), "invalid-entry"));
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getName()).thenReturn("Dan");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer(EXISTING)).thenReturn(player);
            handler.onCommand(sender, command, "smc", new String[]{"ignore", "list"});
        }
        verify(sender).sendMessage(contains(EXISTING + " (Dan)"));
        verify(sender).sendMessage(contains("invalid-entry"));
        verify(plugin, never()).saveConfig();
    }

    private boolean run(String operation, String player) {
        return handler.onCommand(sender, command, "smc", new String[]{"ignore", operation, player});
    }

    private static List<String> ignored(YamlConfiguration configuration) {
        return configuration.getStringList(ConfigConstants.OPTIONS_IGNORED_PLAYERS);
    }

    private static void relay(PlayerListener listener, UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getDisplayName()).thenReturn("Player");
        listener.onPlayerChat(new AsyncPlayerChatEvent(true, player, "hello", new HashSet<>()));
    }
}
