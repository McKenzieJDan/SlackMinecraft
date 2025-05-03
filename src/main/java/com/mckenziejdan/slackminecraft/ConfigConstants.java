package com.mckenziejdan.slackminecraft;

/**
 * Holds constant values for configuration keys used throughout the plugin.
 */
public final class ConfigConstants {

    // Private constructor to prevent instantiation
    private ConfigConstants() {}

    // Slack Section
    public static final String SLACK_ENABLED = "slack.enabled";
    public static final String SLACK_TOKEN = "slack.token";
    public static final String SLACK_APP_TOKEN = "slack.app-token";
    public static final String SLACK_CHANNEL = "slack.channel";
    public static final String SLACK_ICON = "slack.icon";
    public static final String SLACK_DEBUG = "slack.debug";
    public static final String SLACK_CACHE_REFRESH_MINUTES = "slack.cacheRefreshMinutes";
    public static final String SLACK_PLAYER_ICON_FALLBACK = "slack.playerIconUrlFallback"; // Used in SlackBot

    // i18n Section
    public static final String I18N_BOT_NAME = "i18n.botName";
    public static final String I18N_CONNECTED = "i18n.connected";
    public static final String I18N_DISCONNECTED = "i18n.disconnected";
    public static final String I18N_JOINED_GAME = "i18n.joinedGame";
    public static final String I18N_LEFT_GAME = "i18n.leftGame";
    public static final String I18N_ADVANCEMENT_DONE = "i18n.advancementDone";
    public static final String I18N_DEATH = "i18n.death";
    public static final String I18N_COMMAND_EXECUTED = "i18n.commandExecuted";
    public static final String I18N_SLACK_TO_MINECRAFT_FORMAT = "i18n.slackToMinecraftFormat"; // Used in SlackBot

    // Options Section
    public static final String OPTIONS_ECHO_COMMANDS = "options.echoCommands";
    public static final String OPTIONS_IGNORED_PLAYERS = "options.ignoredPlayers";

} 