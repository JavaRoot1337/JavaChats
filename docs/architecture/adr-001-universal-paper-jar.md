# ADR-001: единый Paper JAR

## Статус

Принято

## Решение

JavaChats собирается одним JAR для Paper 1.16.5–26.2. Compile floor — Paper API 1.16.5, bytecode — Java 8 (`--release 8`). Отдельная Gradle-задача `compileModernJava` проверяет тот же исходный код против Paper 26.2.

Для планирования и отправки Bukkit state применяется обычный `BukkitScheduler`. Paper region/entity scheduler не входит в контракт универсального JAR. AI HTTP, parsing и file logging остаются asynchronous и имеют собственные cleanup paths.

## Последствия

Descriptor использует `api-version: '1.16'`; `libraries` удалён, LuckPerms загружается только через optional adapter. Gson shaded и relocated внутри release JAR. Server JVM не фиксируется плагином: Paper 1.16.5 требует Java 16, Paper 26.1+ — Java 25.

Compile-check не заменяет runtime smoke-test. Для выпуска нужно отдельно подтвердить загрузку, chat/PM, filters, reload, AI error paths, LuckPerms optional path и disable cleanup на обеих целевых версиях.
