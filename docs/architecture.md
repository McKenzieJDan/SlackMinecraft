# Architecture

## Constraints

- **One plugin inside the server.** SlackMinecraft runs in the Spigot/Paper JVM. It must not require a separate service or database.
- **Minecraft 26.2 and Java 25.** The build targets the Spigot 26.2 API. Paper extensions must remain optional. See [ADR 0001](adr/0001-target-minecraft-26-2.md).
- **The server connects to Slack.** Socket Mode uses an outbound WebSocket connection. The plugin does not expose an HTTP endpoint.
- **Network work stays off the server thread.** Slack calls, retries and queue waits run on the relay worker. Incoming Slack handlers schedule Minecraft broadcasts and player queries on the server thread.
- **Queues and waits have limits.** A Slack failure must not create an unlimited outgoing backlog or an unlimited wait on the server thread.
- **Command arguments stay private.** Command forwarding sends only the command name. Slack requests cannot execute arbitrary server commands.
- **Credentials stay in local configuration.** Do not commit tokens or include them in plugin logs. Plugin diagnostic logs report delivery status without message bodies.

Every design must state which thread owns mutable state, where data lives and which data crosses the server boundary.

## Stack

| Component | Choice |
| --- | --- |
| Language and runtime | Java 25. |
| Server contract | Spigot API 26.2, provided by the server. |
| Slack client | Slack Java SDK, Bolt and Socket Mode. |
| WebSocket backend | Java-WebSocket. |
| Logging | Plugin logger and SLF4J through `java.util.logging`. |
| Build | Maven, with local versions and tasks in mise. |
| Tests | JUnit, Mockito, Surefire and Failsafe. |
| Packaging | Maven Shade with private dependency namespaces and merged service descriptors. |

Dependency versions live in [pom.xml](../pom.xml). Local tool versions live in [mise.toml](../mise.toml). CI provisions Java 25 and runs `mvn clean verify`.

## Code boundaries

The Java sources currently share `com.mckenziejdan.slackminecraft`. These are class responsibilities, not separate modules.

| Class | Responsibility |
| --- | --- |
| `SlackMinecraft` | Load configuration, register listeners and commands, and start or stop the relay. |
| `PlayerListener` | Select Minecraft events for relay and maintain the ignored-player snapshot. |
| `IgnoreCommand` | Check administrator permission and persist ignore-list changes. |
| `SlackBot` | Own the connection, relay worker, outgoing queue, incoming scheduling and shutdown. |
| `SlackDirectory` | Resolve channel names and publish complete user-cache snapshots. |
| `MessageFormatter` | Convert mentions, links and escaped text between Minecraft and Slack. |
| `ConfigConstants` | Name the existing configuration keys. |

Keep text conversion independent of Bukkit and network calls. Keep Slack transport details out of player event selection. Split packages by feature when the code needs a boundary. Do not add layers to match a diagram.

## Data and network boundaries

| Data | Location and movement |
| --- | --- |
| Slack tokens, channel and message settings | Stored in `plugins/SlackMinecraft/config.yml` on the server. Tokens authenticate requests to Slack. |
| Ignored player UUIDs | Stored in the same configuration file and copied into an immutable in-memory set. |
| User IDs and names | Read from Slack and cached in server memory. No cache file is written. |
| Outgoing chat and events | Held in server memory, then sent to the configured Slack channel. |
| Incoming Slack chat | Received by Socket Mode, converted and broadcast through the server. The plugin does not keep a message archive. |
| Player avatar URL | Contains the player's UUID and points to `www.mc-heads.net`. It is included in outgoing Slack messages. Slack fetches the image from that service. |
| Logs | Written through the server's logging system. The plugin does not control server log retention or Slack's retention of posted content. |

This is a cross-service relay. It does not keep all community content inside the Minecraft server's network.

## Runtime

### Startup

1. Add missing configuration defaults while keeping existing values.
2. Register the Minecraft listeners and administrator command.
3. Start the relay worker when Slack is enabled and the required token prefixes and channel are present.
4. Resolve the channel and attempt the initial user-cache load.
5. Start Socket Mode with `startAsync()` and send the online notification.

A channel ID avoids channel enumeration. Name lookup follows pagination. A failed user-cache refresh keeps the last complete snapshot. Socket startup has up to three attempts with five seconds between attempts. A terminal startup failure requires a restart after the cause is fixed.

### Outgoing messages

- Minecraft event handlers add messages to a queue with a capacity of 256.
- The worker sends at most one message per second during normal operation.
- HTTP calls have five-second call and read timeouts.
- HTTP 429 responses use `Retry-After`. Each message has at most three send attempts. Long rate-limit delays drop that message and delay later sends.
- An uncertain network delivery is not retried. Retrying could post the same message twice.
- A full queue drops new messages. Queue warnings are limited to one per minute.
- Queue contents are not persisted.

### Incoming messages

- Ignore bot messages, unsupported message subtypes and messages outside the configured channel.
- Schedule Minecraft work through Bukkit before returning the Slack acknowledgement.
- Read players and broadcast chat in the scheduled task.
- Interpret `!list`, `!status` and `!tps` as status queries. Other messages become chat.
- Translate administrator-defined color codes in the format. Do not interpret Slack users' `&` sequences as color codes.

### Shutdown

- Stop accepting messages and interrupt the worker.
- Discard queued messages and attempt an offline notification.
- Close the Socket Mode app, its underlying client and the Slack HTTP resources.
- Wait at most three seconds on the server thread. If cleanup is still running, it continues on the daemon worker and a warning is logged.
- Treat the offline notification as best effort. Process exit can interrupt cleanup.

## Packaging and validation

- The server provides Bukkit and Spigot classes. Do not bundle them in the plugin.
- The plugin bundles its Slack runtime dependencies. Shade relocates them to avoid collisions with server and plugin libraries.
- Preserve service descriptors and dependency license notices when packaging.
- `*Test` classes run under Surefire. `*IT` classes run under Failsafe after packaging.
- The packaged-JAR test uses an isolated class loader. It checks bundled client loading, not an authenticated Slack exchange.
- Live server checks are defined in [shaping/chat-bridge.md](shaping/chat-bridge.md#acceptance-checks).

## Releases

`mise run release -- <version>` builds and verifies locally, then uses GitHub CLI to create a release with its JAR and notes. It requires a clean `dev` checkout that matches `origin/dev`. The release targets that exact commit. It does not require GitHub Actions. See [the release guide](development.md#release-from-your-computer) and [ADR 0002](adr/0002-build-releases-locally.md).

## Open decisions

These decisions have no agreed policy in this repo. Resolve the relevant decision before a change depends on it. Do not expand the current scope to settle unrelated questions.

- **Release signing.** The local release command uploads the tested JAR. Artifact signing and attestations are not configured.
- **Formatting and static analysis.** EditorConfig sets editor defaults. A Java formatter and a CI formatting or static-analysis check are not selected.

Record significant decisions in [adr/](adr/). Treat durable delivery, more Slack channels and additional server platforms as new feature proposals, not implied requirements.

## Status

- This is an existing plugin under modernisation. It is not a greenfield repo.
- The development build targets Spigot/Paper 26.2 and Java 25.
- Local compilation, regression tests and packaged-client checks pass for the modernisation work.
- Live server-to-workspace behaviour remains unverified. Local checks do not establish CI, deployment or public release status.
