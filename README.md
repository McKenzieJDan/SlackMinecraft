# SlackMinecraft

A Minecraft Spigot plugin that relays chat messages, player events (join, quit, death, advancements), and optionally commands between a Minecraft server and a Slack channel using Socket Mode.

[![Java](https://img.shields.io/badge/Java-17-red.svg)](https://adoptium.net/temurin/releases/?version=17)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.5-green.svg)](https://www.minecraft.net/)
[![SpigotMC](https://img.shields.io/badge/SpigotMC-SlackMinecraft-orange)](https://www.spigotmc.org/resources/slackminecraft.117207/)
[![Donate](https://img.shields.io/badge/Donate-PayPal-blue.svg)](https://www.paypal.com/paypalme/mckenzio)

## Features

*   💬 Bidirectional chat relay between Minecraft & Slack (with @mention support)
*   📢 Relay game events (join, quit, death, advancements) to Slack
*   ⚙️ Configurable message formats & bot appearance
*   ⚡ Efficient & performant (Socket Mode, user caching)
*   🖥️ Optional command echoing to Slack

## Installation

1.  Download the latest release from [Spigot](https://www.spigotmc.org/resources/slackminecraft.117207/) or [GitHub Releases](https://github.com/McKenzieJDan/SlackMinecraft/releases).
2.  Place the JAR file in your server's `plugins` folder.
3.  Restart your server.
4.  Configure the plugin in `plugins/SlackMinecraft/config.yml`:
    *   Create a [Slack App](https://api.slack.com/apps).
    *   Enable **Socket Mode** under "Features". Generate an **App-Level Token** (starting with `xapp-`) with the `connections:write` scope and add it to `slack.app-token`.
    *   Go to "OAuth & Permissions". Add the following **Bot Token Scopes**: `chat:write`, `users:read`, `channels:read`.
    *   Install the app to your workspace and copy the **Bot User OAuth Token** (starting with `xoxb-`) into `slack.token`.
    *   Set the `slack.channel` to the name of the channel you want the bot to use (without the `#`).
    *   Review other settings like `i18n` messages and `options`.

## Usage

Once configured correctly, the plugin automatically connects to Slack and relays messages and events between the configured channel and the Minecraft server. No specific player commands are required for basic operation.

### Commands

This plugin does not currently add any commands.

### Permissions

This plugin does not currently add any permissions. All players can chat and trigger events that are relayed.

## Configuration

The plugin's configuration file (`config.yml`) allows customization:

```yaml
# Slack integration configuration
slack:
  # Set to true to enable Slack integration, false to disable
  enabled: true
  # Your Slack Bot Token (starts with xoxb-)
  token: ""
  # Your Slack App-Level Token for Socket Mode (starts with xapp-)
  app-token: ""
  # The Slack channel name (without #) to connect to and relay messages
  channel: "general"
  # URL for the bot's profile picture in Slack (leave empty for default)
  icon: ""
  # Set to true for extra diagnostic logging in the server console
  debug: false
  # How often (in minutes) to refresh the Slack user list cache (for @mentions)
  cacheRefreshMinutes: 60

# Internationalization / Message Customization
i18n:
  # Name used by the bot when posting messages (e.g., server status)
  botName: "Minecraft"
  # Message sent to Slack when the bot connects
  connected: ":white_check_mark: Online"
  # Message sent to Slack when the bot disconnects
  disconnected: ":x: Offline"
  # Message format for player joins
  joinedGame: "_Joined the game_"
  # Message format for player quits
  leftGame: "_Left the game_"
  # Prefix for advancement messages
  advancementDone: ":trophy: Has made the advancement "
  # Prefix for player death messages (followed by Minecraft's death message)
  death: ":skull: "
  # Prefix for command messages (if options.echoCommands is true)
  commandExecuted: ":warning: Executed command: "
  # Format for messages coming FROM Slack TO Minecraft (%s = username, %s = message)
  slackToMinecraftFormat: "[Slack] <%s> %s"

# Other plugin options
options:
  # Set to true to send messages to Slack when players use commands
  echoCommands: true
```

## Requirements

*   Spigot/Paper 1.21.5+
*   Java 17+

## Used By

[SuegoFaults](https://suegofaults.com) - A curated adult Minecraft community where this plugin powers Slack chat and events integration.

## Support

If you find this plugin helpful, consider [buying me a coffee](https://www.paypal.com/paypalme/mckenzio) ☕

## License

[MIT License](LICENSE)

Made with ❤️ by [McKenzieJDan](https://github.com/McKenzieJDan)