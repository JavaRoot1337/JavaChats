# Архитектура JavaChats

## Цель совместимости

JavaChats выпускается одним Paper-only JAR для Paper 1.16.5–26.2. Исходники компилируются с `--release 8`, поэтому классы плагина имеют bytecode major 52. Версия JVM выбирается сервером: для Paper 1.16.5 нужна Java 16, для Paper 26.1+ — Java 25.

`plugin.yml` использует `api-version: '1.16'`. LuckPerms остаётся optional через `softdepend`; плагин не загружает внешние библиотеки через descriptor. Gson включён в release JAR с relocation, чтобы не зависеть от версии Gson на сервере.

## Границы доменов

- `JavaChat` — bootstrap, регистрация команд и listeners, создание зависимостей и lifecycle
- `config` — загрузка YAML и immutable snapshots; reload заменяет snapshots только после успешной сборки
- `application` — use cases чата, личных сообщений и moderation pipeline
- `domain` — immutable request/result/decision/policy-модели без Bukkit API
- `adapter.bukkit` — AsyncChatEvent, commands, recipients, player delivery и scheduler facade
- `api` — стабильные Java 8 value-классы и публичные фасады `JavaChatsApi`
- `aihelper` — очередь AI, провайдеры, цензура, таймауты и остановка worker/executor
- `runtime` — фасад над обычным `BukkitScheduler` с tracking и cancel на disable
- `integration` — optional adapter LuckPerms и пустой fallback без зависимости от его runtime
- `logging` и `utils` — асинхронный chat log, шаблоны сообщений и общие преобразования

Главный класс не содержит бизнес-правил чата. Bukkit adapter преобразует событие в immutable вход, а API и команды делегируют application-коду. Текущий переход сохраняет существующие имена классов до завершения API v2 migration.

## Потоки и владение состоянием

Bukkit/Paper API, игроки, мир, инвентари и отправка сообщений выполняются через server thread. HTTP, AI parsing и файловая запись выполняются асинхронно. `ServerScheduler` использует совместимый с 1.16.5 `BukkitScheduler`; region/entity scheduler не используется.

Долгоживущие карты используют UUID. На disable отменяются tracked tasks, останавливаются AI providers и timeout executor, закрывается logging executor и сбрасывается runtime state. Публичный API может быть вызван из любого потока, но Bukkit-доставка возвращается на scheduler плагина.

## Конфигурация и reload

Ресурсы `config.yml`, `message.yml`, `AIHELPER.yml` и `AIRULES.yml` сохраняют текущие имена и ключи. `ai-helper.failure-policy` принимает `allow` или `block`. YAML не используется как база данных. Runtime не получает mutable `FileConfiguration`: конфигурация преобразуется в snapshots и проверяется до замены. При ошибке новая конфигурация не заменяет последнюю рабочую.

## ADR-001: единый Paper JAR и compile-floor 1.16.5

Решение: нижним compile floor является Paper API `1.16.5-R0.1-SNAPSHOT`, release bytecode — Java 8, а отдельная задача `compileModernJava` проверяет исходники против Paper `26.2.build.121-stable`.

Причины: descriptor с `api-version: '26.2'` блокирует старые серверы, а region scheduler отсутствует в 1.16.5. Общий BukkitScheduler и публичные Adventure API доступны на нижнем floor и сохраняют единый код.

Ограничения: runtime smoke-test должен быть выполнен на реальных Paper 1.16.5 и 26.2. Compile-check не доказывает работу всех игровых сценариев, а отсутствующая локальная серверная среда не позволяет подтвердить загрузку, chat delivery и cleanup автоматически.
