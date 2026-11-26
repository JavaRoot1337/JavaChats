# JavaChats Architecture

## Target matrix

- Minecraft: `1.21–26.3` (`api-version: '1.21'`; `compileModernJava` verifies against Paper `26.2.build.121-stable`)
- Core: Paper and Folia (`folia-supported: true` in `paper-plugin.yml`)
- Java: `21+` (`--release 21`), native Adventure without shading
- Commands: Paper Brigadier via `LifecycleEvents.COMMANDS`

## Packages (`ru.javaroot.javachats`)

- `JavaChats` — bootstrap, permissions, Brigadier commands, listeners, API registration
- `api` — `JavaChatsApi`, `ApiProvider`, immutable `record` models
- `plat` — `Exec` (`AsyncScheduler`, `GlobalRegionScheduler`, `RegionScheduler`, `EntityScheduler`) and `Hex` (`&` / `&#RRGGBB` parser)
- `cfg` — `Cfg`, `Msg`, `LogCfg`, `LocaleMgr` (immutable snapshots validated before swap on reload)
- `hook` — isolated `LuckPerms` adapter (`LpHook`, fallback `NoopMeta`)
- `ai` — `AiSvc`, `AiRules`, `AiClient` (`java.net.http.HttpClient`), `OpenAiProv` (Mistral / Groq)
- `chat` — `ChatEng`, `ChatPing`, `PmSvcImpl`, `ChatLog` (async bounded file logger)
- `list` — `ChatList` (`AsyncChatEvent`) and `ConnList` (`PlayerJoinEvent` / `PlayerQuitEvent` cache cleanup)
- `cmd` — `JavaChatsCmd`, `MsgCmd`, `AiCmd`
- `upd` — `GhUpd` and `Ver` (`26.x > 1.21.x` version comparison)

## Folia threading model

- No `Bukkit.getScheduler()` or `BukkitRunnable` calls
- Message delivery, permission checks, local radius, sounds, titles, and lightning effects run on the target `EntityScheduler`
- AI console punishments and update checks run on `GlobalRegionScheduler`
- HTTP calls and `ChatLog` file I/O run asynchronously; `onDisable()` cancels tasks and closes pools/queues

[Русская версия](architectureru.md)
