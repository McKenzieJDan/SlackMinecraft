# Slack Minecraft

A lightweight Spigot plugin that bridges your Minecraft server with Slack, enabling real-time communication and event notifications between both platforms.

## Features
- 📢 Real-time game events in Slack
- 🎮 Player join/leave notifications
- 💀 Death messages
- 🏆 Achievement announcements
- ⌨️ Command execution logging (optional)
- 🎨 Custom player avatars
- 👤 Slack username mentions support
- ⚙️ Fully configurable messages
- 🔧 Debug mode for troubleshooting

## Installation
1. Download the latest release from [GitHub Releases](https://github.com/McKenzieJDan/SlackMinecraft/releases)
2. Place the .jar in your server's plugins folder
3. Restart your server
4. Configure your Slack app token in `plugins/SlackMinecraft/config.yml`

## Requirements
- Spigot/Paper 1.21.4+
- Java 17+
- Slack App Token ([Create a Slack App](https://api.slack.com/apps))
  1. Create a new app from scratch
  2. Add the `chat:write`, `channels:read`, and `users:read` scopes under "OAuth & Permissions"
  3. Install the app to your workspace
  4. Copy the "Bot User OAuth Token" (starts with `xoxb-`)

## Development
To build the plugin yourself:

1. Clone the repository
2. Run `mvn clean package`
3. Find the built jar in the `target` folder

## License

[MIT License](LICENSE)

Made with ❤️ by [McKenzieJDan](https://github.com/McKenzieJDan)