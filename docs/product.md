# SlackMinecraft

SlackMinecraft connects a Minecraft community's server with its Slack channel. Players can talk with community members who are outside the game.

## Users

- **Players** send and receive chat in Minecraft. Their joins, quits, deaths and announced advancements can appear in Slack.
- **Slack channel members** send chat into Minecraft and request the player list or server status.
- **Server administrators** configure the Slack app, set message formats and manage relay exclusions.

## Scope

- One Spigot/Paper server connects to one configured Slack channel.
- Chat travels in both directions.
- Minecraft player events travel to Slack.
- Slack commands return server information.
- Minecraft administrator commands manage the ignore list.

See [shaping/chat-bridge.md](shaping/chat-bridge.md) for the behaviour and acceptance checks.

## Data and access

- Relay content leaves the Minecraft server and enters the configured Slack workspace.
- Slack chat becomes visible to players on the Minecraft server.
- Player names, UUID-based avatar URLs and event text can be sent to Slack.
- The ignore list excludes a player's outgoing chat and events. It does not hide the player from the player list or stop incoming Slack chat.
- Command forwarding is optional. It sends the command name without its arguments.
- The server stores Slack credentials in its local plugin configuration.

See [architecture.md](architecture.md) for data storage and network boundaries. See [setup.md](setup.md) for permissions and installation.

## Outside the current scope

- Multiple channels or workspaces per plugin instance.
- Arbitrary server command execution from Slack.
- A separate web service, database or administration interface.
- Persistent message storage or replay after a restart.
- Support for older Minecraft versions or Folia.

These items are not a committed roadmap.
