# JavaChats

Chat plugin for Paper and Folia `1.21–26.3`: local and global channels, private messages, `@mentions` with sound/title alerts, and async AI moderation.

## Compatibility

| Parameter | Value |
| --- | --- |
| Core | Paper, Folia (`folia-supported: true`) |
| Minecraft | `1.21 – 26.3` |
| Java | `21+` |
| Optional | `LuckPerms` |

## Build

```powershell
.\gradlew.bat clean build
```

Output JAR: `build/libs/JavaChats-2.0.jar`.

## Commands

| Command | Action | Permission |
| --- | --- | --- |
| `/javachats reload` | Reload config and active locale | `javachats.admin` |
| `/msg <player> <message>` (`/tell`, `/w`) | Send private message | `javachats.msg` |
| `/aihelper add <plus\|minus> <message>` | Add AI training sample | `javachats.admin` |

## Permissions

| Permission | Purpose | Default |
| --- | --- | --- |
| `javachats.local.view` | Read local chat | all |
| `javachats.local.send` | Write to local chat | all |
| `javachats.global.view` | Read global chat | all |
| `javachats.global.send` | Write to global chat | all |
| `javachats.msg` | Use `/msg`, `/tell`, `/w` | all |
| `javachats.ai.log` | View AI moderation alerts | op |
| `javachats.admin` | Admin commands and reload | op |

## Configuration

Set `locale: ru` or `locale: en` in `plugins/JavaChats/config.yml`, then run `/javachats reload`.

Locale profiles live in `locate/ru` and `locate/en` (`config.yml`, `message.yml`, `AIHELPER.yml`, `AIRULES.yml`). Active profile files are loaded per locale; keep AI API keys only in server-local config.

## Documentation

- [Public API](docs/api.md)
- [Architecture](docs/architecture.md)

[Русская документация](READMERU.md)
