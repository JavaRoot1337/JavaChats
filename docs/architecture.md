# Архитектура JavaChats

## Целевая матрица

- **Версии Minecraft:** `1.21–26.3` (`api-version: '1.21'`, `compileModernJava` проверяет `26.2.build.121-stable`).
- **Ядро:** Paper и Folia (`folia-supported: true` в `paper-plugin.yml`).
- **Java:** Java 21 (`--release 21`), без легаси-костылей под 1.16.5 и без шейдинга Adventure/Gson.
- **Команды:** Paper Brigadier через `LifecycleEvents.COMMANDS`.

## Пакеты (`ru.javaroot.javachats`)

- `JavaChats` — точка входа `JavaPlugin`, регистрация прав, Brigadier-команд, слушателей и API.
- `api` — публичный контракт `JavaChatsApi`, `ApiProvider` и `record`-модели.
- `plat` — `Exec` (обёртка над `AsyncScheduler`, `GlobalRegionScheduler`, `RegionScheduler`, `EntityScheduler`) и `Hex` (парсер `&` и `&#RRGGBB` в Adventure `Component`).
- `cfg` — `Cfg`, `Msg`, `LogCfg`, `LocaleMgr` (неизменяемые снимки с валидацией до замены при `reload`).
- `hook` — изолированный адаптер `LuckPerms` (`LpHook`, фолбэк `NoopMeta`), загружаемый только при `isPluginEnabled("LuckPerms")`.
- `ai` — `AiSvc`, `AiRules`, `AiClient` (`java.net.http.HttpClient`), единый `OpenAiProv` для Mistral и Groq.
- `chat` — `ChatEng`, `ChatPing`, `PmSvcImpl`, `ChatLog` (асинхронный ограниченный файловый логгер).
- `list` — `ChatList` (`AsyncChatEvent`) и `ConnList` (`PlayerJoinEvent` / `PlayerQuitEvent` с очисткой кэшей).
- `cmd` — Brigadier-команды `JavaChatsCmd`, `MsgCmd`, `AiCmd`.
- `upd` — `GhUpd` и `Ver` с поддержкой годовой нумерации (`26.x > 1.21.x`).

## Модель потоков на Folia

В проекте нет вызовов `Bukkit.getScheduler()` и `BukkitRunnable`:
- доставка сообщений, проверка прав, локальный радиус чата, `playSound`, `showTitle` и `strikeLightningEffect` выполняются в `EntityScheduler` целевого игрока;
- консольные наказания AI-модерации и проверка обновлений выполняются в `GlobalRegionScheduler`;
- HTTP-запросы к AI/GitHub и запись логов чата работают асинхронно без блокировки региональных тиков;
- при `onDisable()` отменяются отслеживаемые задачи, останавливаются HTTP-пулы и закрывается очередь `ChatLog`.
