# JavaChats

Paper-only chat plugin for Paper 1.16.5–26.2: local/global chat, private messages, mentions and AI moderation.

## Build

The build uses the included Gradle wrapper 9.2.1 and a JDK 25 toolchain. Plugin classes are compiled with `--release 8` (bytecode major 52), so the server JVM is selected by Paper: Java 16 for Paper 1.16.5 and Java 25 for Paper 26.1+.

```powershell
.\gradlew.bat clean build
```

The universal release JAR is `build/libs/JavaChats-1.0.jar`. Copy it to the server `plugins/` folder.

## Commands

- `/javachats reload`
- `/msg <player> <message>`
- `/aihelper add <plus|minus> <message>`

LuckPerms is optional. Settings are stored in `config.yml`, `message.yml`, `AIHELPER.yml` and `AIRULES.yml`.

The public API and architecture are documented in `docs/api.md` and `docs/architecture.md`. Runtime smoke-tests on real Paper 1.16.5 and 26.2 servers are required before release.

Do not commit API keys to configuration. Use a server-local configuration and rotate any key that was exposed.
