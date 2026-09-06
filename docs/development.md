# Development

## Repo layout

| Path | Contents |
| --- | --- |
| `src/main/java/com/mckenziejdan/slackminecraft/` | Plugin lifecycle, Minecraft events, Slack relay and ignore commands. |
| `src/main/resources/` | Plugin descriptor and default configuration. |
| `src/test/java/com/mckenziejdan/slackminecraft/` | Regression tests and packaged-JAR checks. |
| `docs/` | Product, architecture, conventions, setup, ADRs and feature shaping. |
| `.github/` | Build workflow and dependency updates. |
| `pom.xml` | Dependencies, compilation, tests and JAR packaging. |
| `mise.toml` | Local tool versions and development tasks. |
| `target/` | Generated JARs and test reports. Not committed. |

The repo builds one plugin JAR. The Java code uses one package. Add feature packages when the code needs them. See [docs/conventions.md](conventions.md).

## Set up

1. Install [mise](https://mise.jdx.dev/getting-started.html).
2. Install the pinned Java and Maven versions.

   ```sh
   mise install
   ```

3. Resolve the project dependencies.

   ```sh
   mise run setup
   ```

`mise run` uses the pinned tools without shell activation. Prefix direct Maven commands with `mise x --`, for example `mise x -- mvn test`.

No Minecraft server or Slack credentials are needed for the build or automated tests. To install the plugin on a server, follow [docs/setup.md](setup.md).

## Develop

| Command | Action |
| --- | --- |
| `mise run build` | Run unit tests and build the plugin JAR. |
| `mise run test` | Run unit and regression tests. |
| `mise run verify` | Clean, test, build and check the packaged JAR. |
| `mise run clean` | Remove build output. |
| `mise tasks` | List the available tasks. |

Run `mise run verify` before committing code or build changes. It runs the same Maven goals as CI. Check documentation links and facts before committing documentation changes.

The output is `target/SlackMinecraft-2.0.0-SNAPSHOT.jar`. Packaging checks do not establish a live Slack connection. Follow the [server acceptance checks](shaping/chat-bridge.md#acceptance-checks) before a production install. See [docs/architecture.md#status](architecture.md#status) for the current validation limits.

## Before you write code

- [docs/product.md](product.md) describes the users, scope and data sharing.
- [docs/conventions.md](conventions.md) states how to organise, name, test and commit code.
- [docs/architecture.md](architecture.md) states the runtime constraints, data boundaries and open decisions.
- [AGENTS.md](../AGENTS.md) gives AI agents the constraints and writing rules. [CLAUDE.md](../CLAUDE.md) points to the same instructions.
- [docs/adr/](adr/) records significant decisions. [docs/shaping/](shaping/) defines feature scope and acceptance checks.
