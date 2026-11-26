# Архитектура JavaChats

## Целевая матрица

- Minecraft: `1.21–26.3` (`api-version: '1.21'`, задача `compileModernJava` проверяет Paper `26.2.build.121-stable`)
- Ядро: Paper и Folia (`folia-supported: true` в `paper-plugin.yml`)
- Java: `21+` (`--release 21`), нативный Adventure без шейдинга
- Команды: Paper Brigadier через `LifecycleEvents.COMMANDS`

## Пакеты (`ru.javaroot.javachats`)

- `JavaChats` — точка входа, права, Brigadier-команды, слушатели, регистрация API
- `api` — `JavaChatsApi`, `ApiProvider` и неизменяемые `record`-модели
- `plat` — `Exec` (`AsyncScheduler`, `GlobalRegionScheduler`, `RegionScheduler`, `EntityScheduler`) и `Hex` (парсер `&` / `&#RRGGBB`)
- `cfg` — `Cfg`, `Msg`, `LogCfg`, `LocaleMgr` (неизменяемые снимки с валидацией до замены при `reload`)
- `hook` — изолированный адаптер `LuckPerms` (`LpHook`, фолбэк `NoopMeta`)
- `ai` — `AiSvc`, `AiRules`, `AiClient` (`java.net.http.HttpClient`), единый `OpenAiProv` (Mistral / Groq)
- `chat` — `ChatEng`, `ChatPing`, `PmSvcImpl`, `ChatLog` (асинхронный ограниченный файловый логгер)
- `list` — `ChatList` (`AsyncChatEvent`) и `ConnList` (`PlayerJoinEvent` / `PlayerQuitEvent` с очисткой кэша)
- `cmd` — `JavaChatsCmd`, `MsgCmd`, `AiCmd`
- `upd` — `GhUpd` и `Ver` (сравнение версий с поддержкой `26.x > 1.21.x`)

## Модель потоков на Folia

- Нет вызовов `Bukkit.getScheduler()` и `BukkitRunnable`
- Доставка сообщений, проверка прав, локальный радиус, звуки, титлы и молнии идут через `EntityScheduler` целевого игрока
- Консольные наказания AI-модерации и проверка обновлений идут через `GlobalRegionScheduler`
- HTTP-запросы и запись `ChatLog` работают асинхронно; `onDisable()` отменяет задачи и закрывает пулы

[English version](architecture.md)
