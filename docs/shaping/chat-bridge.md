# Minecraft and Slack chat bridge

## Problem

Community members outside Minecraft need a way to talk with players and see server activity through Slack.

## Users

- Players use Minecraft chat.
- Community members use the configured Slack channel.
- Server administrators install the plugin and control which player events leave the server.

## Scope

### Chat and events

- Relay Minecraft chat to one configured Slack channel.
- Relay human messages from that Slack channel into Minecraft.
- Convert known user mentions and Slack links.
- Ignore bot messages and unsupported message subtypes.
- Relay joins, quits, non-null death messages and advancements that announce to chat.
- Exclude cancelled chat and commands.
- Exclude an ignored player's outgoing chat and events.
- Allow administrators to configure message text, the bot name and avatar URLs.
- Keep command forwarding off by default. If enabled, send only command names.

### Commands and access

| Entry point | Behaviour | Access |
| --- | --- | --- |
| Slack `!list` | Return online player names. | Members who can post in the configured channel. |
| Slack `!status` or `!tps` | Return player count, memory and TPS when available. | Members who can post in the configured channel. |
| `/smc ignore add <playername|uuid>` | Add a player to the outgoing relay exclusions. | `slackminecraft.admin`, operators by default. |
| `/smc ignore remove <playername|uuid>` | Remove an exclusion. | `slackminecraft.admin`, operators by default. |
| `/smc ignore list` | List excluded UUIDs with known player names. | `slackminecraft.admin`, operators by default. |

`/slackminecraft` is the full name of `/smc`. Name lookup uses online or previously joined players. UUID input does not need a player-name lookup.

## Data and access implications

- Outgoing content becomes part of the configured Slack workspace.
- Incoming Slack chat becomes visible on the Minecraft server.
- The ignore list does not hide a player from `!list` or prevent them receiving Slack chat.
- Slack status commands do not grant console access.
- Messages and user-cache entries are held in memory. There is no message archive or replay after restart.
- Slack credentials stay in server configuration. The build and automated tests do not need them.

See [architecture.md](../architecture.md#data-and-network-boundaries) for the complete data boundary.

## Outside the scope

- Multiple Slack channels or workspaces.
- Remote console commands, moderation actions or account linking.
- Persistent delivery and historical message synchronization.
- Older Minecraft versions and Folia.

## Acceptance checks

Run these checks on a staging Spigot/Paper 26.2 server with Java 25 and a Slack app configured through [setup.md](../setup.md). Use test content. These checks describe required observations, not completed results.

1. Start the server. Check command registration, the Socket Mode connection log and the Slack online notification.
2. Send chat in both directions. Check names, known and unknown mentions, links, escaped text and formatting.
3. Send a message in another Slack channel and a message from a bot. Check that neither appears in Minecraft.
4. Join, quit, die and complete an announced advancement. Check the corresponding Slack notifications. Check that recipe unlocks do not create advancement notifications.
5. Cancel chat through another plugin. Check that it does not reach Slack.
6. With command forwarding disabled, issue a harmless command. Check that nothing is forwarded. Enable forwarding on the test server, restart, and issue a command with dummy arguments. Check that Slack receives only its name.
7. Add a test player to the ignore list. Check that the player's chat and events stop reaching Slack. Check that the player still receives Slack messages and remains visible to `!list`.
8. Restart the server. Check that the ignore list persists. Remove the player and check that relay resumes.
9. Use `!list`, `!status` and `!tps`. Check their results and the unavailable TPS message on Spigot.
10. Try the ignore commands without administrator permission. Check that access is refused.
11. Stop the server. Check resource cleanup and the best-effort offline notification. Restart and check that one connection resumes normal relay.

Automated tests cover cases such as queue capacity, failed cache refreshes, channel pagination, message conversion and scheduled callbacks after shutdown. Run them with `mise run verify`.

## Open questions

No additional feature decision is required for this scope. Changes to delivery guarantees, channel access or supported platforms need a new proposal.

## Status

- The feature exists in the development code.
- Local regression and packaged-JAR checks pass for the modernisation work.
- Live acceptance checks remain unverified.
- This document does not establish a deployment or public release.
