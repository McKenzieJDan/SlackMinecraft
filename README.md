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
⚙️ Fully configurable messages
🔧 Debug mode for troubleshooting

## Installation
1. Download the latest release from [GitHub Releases](https://github.com/McKenzieJDan/SlackMinecraft/releases)
2. Place the .jar in your server's plugins folder
3. Restart your server
4. Configure your Slack bot token in `plugins/SlackMinecraft/config.yml`

## Requirements
- Spigot/Paper 1.21.4+
- Java 17+
- Slack Bot Token ([Create a bot here](https://my.slack.com/services/new/bot))

## Development
To build the plugin yourself:

1. Clone the repository
2. Run `mvn clean package`
3. Find the built jar in the `target` folder

## License

[MIT License](LICENSE)

Made with ❤️ by [McKenzieJDan](https://github.com/McKenzieJDan)