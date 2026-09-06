# Target Minecraft 26.2 and Java 25

- Date: 2026-09-06.
- Status: accepted.

## Context

The maintainer requests an update of the existing plugin and confirms Minecraft 26.2 as the target. The previous development build declares Spigot 1.21.5 and Java 17. Its direct call to a Paper-only TPS method also prevents compilation against Spigot.

## Decision

- Compile against the Spigot 26.2 API with Java 25.
- Keep the Spigot API in Maven's `provided` scope.
- Set `plugin.yml` to API version `26.2`.
- Keep Paper-only TPS access optional. Return an unavailable result on servers without that API.
- Build one JAR for the current Spigot/Paper target.

## Consequences

- The build and server require Java 25.
- This build does not support earlier Minecraft versions or Folia.
- Automated checks compile against Spigot and test the packaged dependencies.
- Compatibility on a live Spigot or Paper server still requires the [acceptance checks](../shaping/chat-bridge.md#acceptance-checks).
- Any expansion of the supported platforms needs a separate decision and validation.

See [architecture.md](../architecture.md) for the current runtime design.
