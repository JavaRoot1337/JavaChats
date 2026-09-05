# JavaChats

Paper 26.2 chat plugin for local/global chat, private messages, mentions and AI moderation.

## Build

Requires Java 25. Gradle wrapper 9.2.1 is included.

```bash
./gradlew clean build
```

Copy `build/libs/JavaChats-1.0.jar` to the server `plugins/` folder.

## Commands

- `/javachats reload`
- `/msg <player> <message>`
- `/aihelper add <plus|minus> <message>`

LuckPerms is optional. Settings are stored in `config.yml`, `message.yml`, `AIHELPER.yml` and `AIRULES.yml`.

The public API is documented in `docs/api.md`. API filters are asynchronous and must not touch Bukkit state from their callback.

Do not commit API keys to configuration. Use a server-local configuration and rotate any key that was exposed.
