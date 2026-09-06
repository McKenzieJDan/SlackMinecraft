# SlackMinecraft

A Minecraft Spigot plugin that relays chat messages, player events (join, quit, death, advancements), and optionally command names between a Minecraft server and a Slack channel using Socket Mode.

## Features

- 💬 Bidirectional chat relay between Minecraft & Slack, with @mention support.
- 📢 Relay game events (join, quit, death, advancements) to Slack.
- ⚙️ Configurable message formats & bot appearance.
- ⚡ Socket Mode connection with user caching.
- 🖥️ Optional command echoing to Slack, with arguments omitted.

## Installation

The current development build requires **Spigot/Paper 26.2 and Java 25**.

1. [Build the development JAR](docs/development.md#set-up), or choose a compatible release from [Spigot](https://www.spigotmc.org/resources/slackminecraft.117207/) or [GitHub Releases](https://github.com/Staticpast/SlackMinecraft/releases).
2. Stop your server, back up any existing configuration and replace the SlackMinecraft JAR in `plugins/`.
3. Follow the [Slack setup guide](docs/setup.md) to configure your app, tokens and channel, then restart the server.

Chat and events relay automatically once connected. Command echoing is off by default and never includes arguments.

## Documentation

- [Setup, commands & configuration](docs/setup.md)
- [Development & repo layout](docs/development.md)
- [Product](docs/product.md)
- [Architecture](docs/architecture.md)
- [Code conventions](docs/conventions.md)
- [Agent instructions](AGENTS.md)

## Used By

[SuegoFaults](https://suegofaults.com/) - A curated adult Minecraft community where this plugin powers Slack chat and events integration.

## Support

If you find this plugin helpful, consider [buying me a coffee](https://www.paypal.com/paypalme/mckenzio) ☕

## License

[MIT License](LICENSE.md)

Made with ❤️ by [McKenzieJDan](https://github.com/McKenzieJDan)
