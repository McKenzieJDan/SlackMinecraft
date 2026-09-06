# SlackMinecraft

SlackMinecraft connects a Spigot/Paper server with a Slack channel. Read [docs/product.md](docs/product.md) for the product scope.

## Constraints

- Target Minecraft 26.2 and Java 25.
- Build one plugin JAR. Keep the Spigot API provided by the server.
- Keep Slack network calls off the server thread. Schedule incoming Minecraft broadcasts and player queries on the server thread.
- Bound outgoing queues, retries and shutdown waits. Close resources after failure as well as normal shutdown.
- Keep command arguments out of Slack. Do not add arbitrary server command execution through Slack.
- Treat tokens as secrets. Do not commit them or log private content.
- Preserve existing configuration values and ignored UUIDs when adding defaults.

## Where things are documented

- [docs/product.md](docs/product.md): users, purpose, scope and data sharing.
- [docs/architecture.md](docs/architecture.md): constraints, class responsibilities, data boundaries, runtime and open decisions.
- [docs/conventions.md](docs/conventions.md): organisation, naming, tests and commits. Read it before changing code.
- [docs/setup.md](docs/setup.md): installation, Slack configuration and troubleshooting.
- [docs/adr/](docs/adr/): significant decisions and their reasons.
- [docs/shaping/](docs/shaping/): feature scope and acceptance checks.

Read the relevant architecture constraints before designing a change. Resolve an open decision only when the requested work depends on it. Do not treat a proposal as an approved requirement.

## Working in the repo

- Inspect the current files and diff before editing. Preserve work outside the requested change.
- Use `mise run verify` for code and build changes. It runs a clean Maven build, tests and packaged-JAR checks.
- Check links, paths and factual claims for documentation changes.
- Keep automated checks separate from live server testing, CI, deployment and release status.
- Make one commit per logical change when committing. Use `area: subject` and do not add AI attribution.
- Keep instructions in this file. `CLAUDE.md` points here so the two entry points do not diverge.

## Writing

These rules apply to documentation, code comments, log messages, prompts, descriptions and commit messages.

Use the principles of ASD-STE100 Simplified Technical English:

- Use one word for one meaning, and one meaning for one word.
- Write in the active voice and present tense.
- Keep sentences short. Give one instruction per sentence.
- Delete words that add nothing.
- State facts in a direct, matter-of-fact tone. Do not use rhetorical framing.
- Use bullets for points a reader needs to scan. Use prose when sentences need to stay connected.
- Do not hard-wrap Markdown. Write each heading, paragraph and list item on one line.
- Avoid metaphors unless they are standard industry terms.
- Choose common words. Use "use", not "utilise"; "start", not "commence"; "about", not "regarding".
- Do not check words against the formal STE dictionary.

## Current state

- The repo contains an existing plugin and its 26.2 modernisation work.
- The current feature is the Minecraft/Slack bridge described in [docs/shaping/chat-bridge.md](docs/shaping/chat-bridge.md).
- Local automated checks do not prove a live server-to-workspace connection or a public release.
- Do not describe the repo as greenfield or add unrelated product plans.
