# Build releases locally

- Date: 2026-09-06.
- Status: accepted.

## Context

The maintainer has reached the GitHub Actions usage limit and needs a mise command to build a JAR and create a GitHub release with notes and an attached download.

## Decision

- Run the release build and verification on the maintainer's computer.
- Use GitHub CLI to upload the JAR and create the release page.
- Accept a stable version as a command argument. Use Maven's `revision` property to set the artifact and plugin descriptor version for that build.
- Require a clean, pushed `dev` checkout before publishing. Target the tested commit explicitly.
- Use `v<version>` tags and `Build <version>` release titles.
- Use versioned Markdown notes when present. Otherwise use GitHub's generated notes.
- Provide dry-run and draft options. Do not overwrite an existing release or tag.

## Consequences

- A release does not depend on GitHub Actions availability.
- The maintainer needs the Java/Maven toolchain and authenticated GitHub CLI access.
- GitHub creates the release tag. The task does not sign release artifacts or claim build attestations.
- The task does not publish to Spigot or test an authenticated Minecraft-to-Slack connection.
- A partial upload can leave a draft to inspect and complete through GitHub.

See [the release guide](../development.md#release-from-your-computer).
