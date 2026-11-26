# JavaChats

Chat plugin for **Paper & Folia 1.21–26.3**: local and global chat, private messages, `@mentions` with sounds and titles, and async AI moderation.

## Build

The build uses Gradle wrapper 9.2.1 (Kotlin DSL) and a Java 21 toolchain (`--release 21`), with `compileModernJava` verifying compatibility against Paper `26.2.build.121-stable`:

```powershell
.\gradlew.bat clean build
```

The release JAR is `build/libs/JavaChats-2.0.jar`. Copy it to the server `plugins/` folder.

## Commands

- `/javachats reload` — reload configuration and active locale profile (`javachats.admin`)
- `/msg <player> <message>` (aliases: `/tell`, `/w`) — send a private message (`javachats.msg`)
- `/aihelper add <plus|minus> <message>` — add a training sample to `learning/` (`javachats.admin`)

## Configuration

`LuckPerms` is optional (`paper-plugin.yml`, `required: false`). Set `locale: ru` or `locale: en` in the root `config.yml`, then run `/javachats reload`.

Locale profiles are stored in `temp/ru` and `temp/en`; each folder contains `config.yml`, `message.yml`, `AIHELPER.yml`, and `AIRULES.yml`. Active profile files are copied to the plugin data root on reload, so edit files inside `temp/{locale}`.

The public API and Folia architecture are documented in [docs/api.md](docs/api.md) and [docs/architecture.md](docs/architecture.md).

Do not commit API keys to configuration. Keep keys in your server-local config and rotate any exposed key.
