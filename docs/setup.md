# Install and configure SlackMinecraft

This guide applies to the development build for Spigot/Paper 26.2 and Java 25.

## Build and install

```sh
mise run verify
```

Set up the toolchain in [the development guide](development.md#set-up). To use Maven directly, run `mise x -- mvn clean verify`. This runs regression tests, creates the plugin, and checks that its bundled Slack client loads independently of server libraries.

1. Stop the server and back up your existing `plugins/SlackMinecraft/config.yml`.
2. Remove the old SlackMinecraft JAR and copy `target/SlackMinecraft-2.0.0-SNAPSHOT.jar` into `plugins/`. Do not use `original-*.jar`.
3. Start the server once to generate or update the configuration.
4. Configure Slack as described below, then restart the server.

Keep tokens private. The plugin does not require credentials to build or run tests. Existing configuration values and ignored UUIDs are retained when defaults are added.

## Slack app setup

1. [Create a Slack app](https://api.slack.com/apps) **from a manifest** using [the Slack app manifest](slack-app-manifest.yml). Select your workspace.
2. Under **Socket Mode**, ensure it is enabled. Generate an app-level token with `connections:write`; copy the `xapp-…` token to `slack.app-token`.
3. Install the app to the workspace. Copy its **Bot User OAuth Token** (`xoxb-…`) to `slack.token`.
4. Invite the bot to the intended channel, including when using a private channel.
5. Copy the channel ID from Slack's channel details into `slack.channel`. IDs survive channel renames and avoid channel enumeration. Channel names (with or without `#`) remain supported.
6. Restart the Minecraft server. Check for `Connected to Slack Socket Mode.` and the online message in Slack.

For an existing app, add the following scopes and event subscriptions, then **reinstall it** in the workspace:

| Purpose | Bot scopes / events |
| --- | --- |
| Send messages | `chat:write` |
| Player display names and avatars | `chat:write.customize` |
| Resolve user mentions | `users:read` |
| Public channel lookup and incoming chat | `channels:read`, `channels:history`, `message.channels` |
| Private channel lookup and incoming chat | `groups:read`, `groups:history`, `message.groups` |

The `message.*` entries belong under **Event Subscriptions → Subscribe to bot events**. The other entries belong under **OAuth & Permissions → Bot Token Scopes**. `connections:write` belongs on the app-level token. No request URL or slash-command registration is needed.

See Slack's [Socket Mode guide](https://docs.slack.dev/tools/java-slack-sdk/guides/socket-mode/) and [message customization requirements](https://docs.slack.dev/reference/methods/chat.postMessage/).

## Features and commands

- Bidirectional chat with user mentions, Slack link conversion and configurable formatting.
- Join, quit, death and announced advancement notifications. Recipe unlocks are not relayed.
- Slack messages `!list`, `!status` and `!tps` return server information. TPS is available on Paper; Spigot reports it as unavailable. These commands can be used by members of the configured channel and do not execute arbitrary server commands.
- `/slackminecraft ignore add <playername|uuid>`, `ignore remove <playername|uuid>` and `ignore list` manage relay exclusions. `/smc` is an alias. Permission `slackminecraft.admin` defaults to operators.
- Names for ignore commands must belong to online or previously joined players; UUIDs can be supplied directly. No external player-name lookup runs on the server thread.

Ignored players' chat and events are excluded from outgoing relay. The ignore list does not hide players from `!list` or stop them receiving Slack messages.

## Configuration

See [the commented default configuration](../src/main/resources/config.yml) for every shipped setting. Important options:

```yaml
slack:
  enabled: true
  token: ""                 # xoxb-… bot token
  app-token: ""             # xapp-… app-level token
  channel: "general"        # Prefer a channel ID, e.g. C0123456789
  icon: ""
  debug: false              # Delivery status only; no message bodies or tokens
  cacheRefreshMinutes: 60   # <= 0 disables periodic refresh, not the initial load

i18n:
  slackToMinecraftFormat: "[Slack] <%s> %s"

options:
  echoCommands: false
  ignoredPlayers: []
```

Command forwarding is now **off by default**. When enabled, only the command name is sent, never its arguments, including for existing configurations with `echoCommands: true`. Cancelled chat and commands are excluded. Formatting codes in the incoming format are supported; Slack users cannot insert Minecraft color codes through `&` sequences.

Messages are queued off the server thread, with a maximum backlog of 256 and one outgoing message per second. Slack HTTP 429 responses use `Retry-After` with limited retries; uncertain network deliveries are not retried to avoid duplicates. A full queue drops new messages and logs a warning. The queue is held in memory and is not preserved across restarts. Shutdown drops pending relay messages and attempts a final offline notification with bounded HTTP timeouts; delivery during shutdown is best effort.

## Troubleshooting and validation

- **No connection:** check both token types, channel ID, bot membership, outbound HTTPS/WebSocket access and the required scopes. Restart after fixing configuration.
- **Minecraft → Slack works but Slack → Minecraft does not:** enable the matching `message.channels` / `message.groups` bot event and history scope, and reinstall the app.
- **Names or avatars do not change:** add `chat:write.customize` and reinstall the app.
- **Mentions do not resolve:** check `users:read`; failed refreshes retain the last complete user cache.

`mise run verify` validates compilation, relay logic, command registration metadata and the shaded runtime dependencies. It does **not** prove a live server-to-workspace connection. Before replacing a production installation, test on a staging 26.2 server with your Slack app: chat in both directions, joins/quits/deaths, advancements, ignore commands, `!list`/`!status`, and a clean server stop/restart. Check that another plugin's cancelled chat is not forwarded.
