# Code conventions

## Repo layout

- The repo has one deployment target: a Minecraft plugin JAR.
- Production Java lives in `src/main/java/com/mckenziejdan/slackminecraft/`.
- Plugin metadata and configuration defaults live in `src/main/resources/`.
- Tests live in `src/test/java/` and mirror the production package path. Use Maven's separate test source tree.
- Generated JARs and reports live in `target/`. Do not commit them.
- Maven owns dependencies and packaging. mise pins local tools and exposes the development tasks.

## Organisation

- **Organise code by what it does.** Add packages for features when a feature needs a separate boundary. Do not group classes into `models`, `services`, `handlers` or `types` packages by default.
- **No grab-bags.** Do not create `utils`, `common` or `helpers`. Name each class and package for the behaviour it provides.
- **Avoid thin wrappers.** Repeat a simple call when a wrapper adds no meaning.
- **Add an abstraction only when it improves readability or isolates a real dependency.** Similar code does not always need a shared abstraction.
- **Do not create shared packages before they are needed.** Keep code with its feature until more than one feature needs the same behaviour.
- Keep shared code independent of feature-specific policy. Leave similar code separate when its features can change in different ways.

## Package boundaries

- Keep public interfaces narrow and descriptive.
- Use package-private classes and methods when callers outside the package do not need them.
- Add an interface or layer for a concrete reason, such as testing, readability or multiple implementations.
- Import cycles between feature packages are not allowed. Resolve a cycle by moving behaviour, merging the packages or extracting a shared responsibility.
- Keep feature packages flat until there is a reason to split them. Use files for operation roles rather than adding a folder for each role.
- Keep format conversion independent of the Minecraft server and Slack transport.
- Keep server API access in the plugin lifecycle, event handlers, command handlers and scheduled Minecraft work.

## Naming

- Name classes, methods and variables for what they do.
- Use `UpperCamelCase` for classes, `lowerCamelCase` for methods and variables, and `UPPER_SNAKE_CASE` for constants.
- Match each public class name to its Java filename.
- Use lowercase package names.
- Put the shared subject first when it makes related names easier to find, such as `SlackBot` and `SlackDirectory`.
- Do not repeat a package's context without a reason.
- Keep configuration key names stable. Document migrations when a key or its meaning changes.

## Threading and failures

- State which thread owns mutable state.
- Use immutable snapshots for state read by both server callbacks and relay workers.
- Schedule Minecraft broadcasts and player queries on the server thread when they originate in Slack callbacks.
- Keep network calls and retry waits off the server thread.
- Bound queues, retries and shutdown waits.
- Close the resources a component creates, including on partial startup failure.
- Preserve the interrupt flag when an operation is interrupted, unless cleanup explicitly consumes it.
- Do not retry a send with an uncertain result without a defined duplicate-delivery policy.
- Log enough context to identify an operation's failure. Do not log credentials, private command arguments or message bodies.

See [architecture.md](architecture.md) for the current limits and runtime sequence.

## Dependencies and configuration

- Keep the server API in Maven's `provided` scope.
- Declare dependency versions in `pom.xml`. Keep Slack SDK modules on the same version.
- Prefer released dependency versions. The Spigot API uses its upstream versioned snapshot coordinate; do not describe it as an immutable dependency lock.
- Keep bundled libraries in private namespaces. Preserve their service descriptors and notices.
- Derive the plugin version from Maven through resource filtering.
- Register commands and permissions in `plugin.yml` in the same change as their handlers.
- Keep shipped defaults in `config.yml` and key names in `ConfigConstants`.
- Preserve existing administrator settings when adding defaults.
- Document changes to scopes, configuration and server requirements in [setup.md](setup.md).

## Formatting

- `.editorconfig` sets UTF-8, LF line endings, final newlines and space indentation.
- Use four spaces for Java and XML. Use two spaces for YAML, TOML and Markdown indentation.
- Do not hard-wrap Markdown paragraphs, headings or list items. Let the editor wrap them.
- Keep unrelated formatting changes out of behaviour changes.
- No automated Java formatter or lint task is configured. Do not claim that a formatting gate runs in CI. Tool selection is an [open decision](architecture.md#open-decisions).

## Tests

- **Test what can break.** Cover behaviour rather than constructors, assignments or a copy of the implementation.
- Mirror the production package under `src/test/java/`.
- Name regression and unit test classes `*Test`. Name checks that need the packaged JAR `*IT`.
- Prefer tests across a boundary when mapping, escaping, scheduling or packaging can fail.
- Unit-test states that live checks cannot reliably reach, such as a full queue, a partial cache refresh or shutdown during a queued callback.
- Keep automated tests independent of a real Minecraft server and Slack credentials.
- Run `mise run test` during development. Run `mise run verify` before committing code or build changes.
- For documentation-only changes, check links, paths, commands and claims against the repo. Do not add tests that mirror prose.
- Use the [acceptance checks](shaping/chat-bridge.md#acceptance-checks) for live validation. Report automated, live, CI and deployment results separately.

## Writing

Follow the writing rules in [AGENTS.md](../AGENTS.md#writing). They apply to documentation, comments, log messages, descriptions and commit messages.

## Commits

- Make one commit per logical change. Do not combine unrelated work.
- Write the message as `area: subject`. Use a lowercase area and an imperative subject.
- Name the area that changes. Do not use `feat:`, `fix:` or `chore:` as the area.
- Do not add AI attribution or `Co-Authored-By` trailers.
- Verify the change before committing it. State any checks that cannot run.
- Preserve changes made by other tasks or contributors.

```text
docs: separate setup from architecture
relay: preserve user cache after failed refresh
build: verify the bundled Slack client
```
