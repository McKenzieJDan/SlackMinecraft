# Set up SlackMinecraft on your server

This guide takes you from installing the plugin to sending messages between Minecraft and Slack.

## Before you start

You need:

- A Spigot or Paper **26.2** server running **Java 25**.
- Access to the server's files and restart controls.
- A Slack workspace where you can install an app, or approval from a workspace administrator.
- A Slack channel for your Minecraft community.

Messages sent through the plugin are visible to people in the connected Slack channel and on the Minecraft server.

## 1. Install the plugin

Use the plugin JAR supplied for this 26.2 update. Published downloads are available on [Spigot](https://www.spigotmc.org/resources/slackminecraft.117207/) and [GitHub Releases](https://github.com/Staticpast/SlackMinecraft/releases). Check that the version you download supports your server version.

1. Stop your Minecraft server.
2. If you are upgrading, back up `plugins/SlackMinecraft/config.yml` and remove the previous SlackMinecraft JAR.
3. Upload the new SlackMinecraft JAR to the server's `plugins` folder.
4. Start the server once. The plugin creates `plugins/SlackMinecraft/config.yml` if it does not exist.
5. Stop the server before editing the configuration.

The plugin cannot connect yet because its Slack tokens are blank. You will add them below.

## 2. Create your Slack app

1. Open [Your Apps in Slack](https://api.slack.com/apps) and select **Create New App**.
2. Choose **From a manifest** and select your workspace.
3. Open [the SlackMinecraft app manifest](slack-app-manifest.yml). Copy its YAML contents into Slack's manifest editor, then create the app. The manifest sets the required permissions, message events and Socket Mode option.
4. Open **Socket Mode** and check that it is enabled. Generate an **App-Level Token** with the `connections:write` scope. Keep the token that starts with `xapp-` for the next step.
5. Open **OAuth & Permissions** and install the app to your workspace. Copy the **Bot User OAuth Token**, which starts with `xoxb-`.
6. In Slack, invite the app to the channel you want to connect. You must invite it to private channels too.
7. Open the channel's details and copy its **Channel ID**.

Keep both tokens private. Do not include them in screenshots or support requests.

### If you already have a Slack app

Check these settings in your existing app:

| Slack settings page | Required settings |
| --- | --- |
| Socket Mode | Enabled, with an app-level token that has `connections:write`. |
| OAuth & Permissions | Bot scopes: `chat:write`, `chat:write.customize`, `users:read`, `channels:read`, `channels:history`, `groups:read`, `groups:history`. |
| Event Subscriptions | Enabled, with bot events `message.channels` and `message.groups`. |

Reinstall the app to your workspace after changing its permissions. You do not need to configure a request URL or slash commands.

## 3. Connect the plugin to Slack

Open `plugins/SlackMinecraft/config.yml` in your server's file editor. Update these fields in the existing `slack` section:

```yaml
slack:
  enabled: true
  token: "PASTE_YOUR_XOXB_BOT_TOKEN_HERE"
  app-token: "PASTE_YOUR_XAPP_APP_TOKEN_HERE"
  channel: "PASTE_YOUR_CHANNEL_ID_HERE"
```

Replace each placeholder with the value from Slack. Keep the quotes and indentation. Leave the other settings in the file in place.

The bot token and app-level token are different. Put the `xoxb-` token in `token` and the `xapp-` token in `app-token`.

A channel name without `#` also works. A channel ID continues to work if you rename the channel.

Save the file and start your server. Check the console for `Connected to Slack Socket Mode.` and your Slack channel for the online notification.

## 4. Check that it works

1. Join the Minecraft server and send a chat message. Check that it appears in Slack.
2. Send a message in the connected Slack channel. Check that it appears in Minecraft.
3. Send `!list` in Slack. Check that the reply lists your player.

Chat relay works automatically. Players do not need to run a command to use it.

## Customise the plugin

Edit `plugins/SlackMinecraft/config.yml` while the server is stopped, then start it again to apply your changes.

| Setting | What it changes |
| --- | --- |
| `i18n.botName` | The name used for the bot's server notifications. |
| `slack.icon` | The image URL used for the bot's server notifications. Leave blank for the default. |
| `i18n` message settings | The wording of join, quit, death, advancement and connection messages. |
| `i18n.slackToMinecraftFormat` | The format of incoming Slack chat. Keep two `%s` placeholders: one for the sender and one for the message. |
| `options.echoCommands` | Whether to send player command names to Slack. Defaults to `false`. Command arguments are never sent. |
| `slack.debug` | Whether to log delivery status for troubleshooting. Does not log message contents or tokens through the plugin's diagnostic messages. |

See [the commented default configuration](../src/main/resources/config.yml) for the complete settings list.

## Commands

### In Slack

- `!list` lists online players.
- `!status` or `!tps` shows player count, memory use and TPS when available on Paper. Spigot reports TPS as unavailable.

Anyone who can post in the connected channel can use these commands. They do not grant access to the server console.

### In Minecraft

Use these commands to stop a player's chat and events from being sent to Slack:

```text
/smc ignore add <playername|uuid>
/smc ignore remove <playername|uuid>
/smc ignore list
```

Replace `<playername|uuid>` with a player name or UUID. Names must belong to online players or players who have joined before. `/slackminecraft` also works in place of `/smc`.

These commands require `slackminecraft.admin`, which server operators have by default. If you use a permissions plugin, grant that permission to the staff who manage relay exclusions.

The ignore list survives a restart. Ignored players still receive incoming Slack messages and appear in `!list`.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| The plugin does not load | Check the server console for errors. Confirm Spigot/Paper 26.2, Java 25 and a compatible plugin JAR. Remove duplicate SlackMinecraft JARs. |
| No connection to Slack | Check `slack.enabled`, both token prefixes and the channel ID. Confirm Socket Mode is enabled and the app is invited to the channel. Restart after correcting the configuration. |
| Minecraft messages reach Slack, but Slack messages do not reach Minecraft | Check the `message.channels` event and `channels:history` scope for public channels, or `message.groups` and `groups:history` for private channels. Reinstall the app after changing scopes. |
| A private channel does not work | Invite the app to that channel. Check `groups:read`, `groups:history` and `message.groups`. Use the channel ID in the configuration. |
| Player names or avatars do not appear as expected | Check `chat:write.customize` and reinstall the app if you add it. |
| User mentions do not resolve | Check `users:read`. The user list refreshes every 60 minutes by default; restart to refresh it sooner. |
| Messages arrive slowly or are missing | Slack limits message delivery. The plugin may drop messages during a long outage or a full queue. Check the server console. Pending messages do not survive a restart. |
| The connection is blocked by the server host | Ask your host whether outbound HTTPS and WebSocket connections to Slack are allowed. |

If you need help, include your Minecraft version, Java version, plugin version and relevant console errors. Remove tokens and private chat content before sharing them.
