# JavaChats

Плагин чата для **Paper и Folia 1.21–26.3**: локальный и глобальный чат, личные сообщения, `@упоминания` со звуком и титлом, а также асинхронная AI-модерация.

## Сборка

Сборка использует Gradle wrapper 9.2.1 (Kotlin DSL) и Java 21 (`--release 21`), а задача `compileModernJava` дополнительно проверяет компиляцию под Paper `26.2.build.121-stable`:

```powershell
.\gradlew.bat clean build
```

Итоговый JAR находится в `build/libs/JavaChats-2.0.jar`. Поместите его в папку `plugins/` сервера.

## Команды

- `/javachats reload` — перезагрузить конфиг и активный профиль локали (`javachats.admin`)
- `/msg <игрок> <сообщение>` (алиасы: `/tell`, `/w`) — личное сообщение (`javachats.msg`)
- `/aihelper add <plus|minus> <сообщение>` — добавить пример в обучающую выборку (`javachats.admin`)

## Конфигурация

`LuckPerms` необязателен (`paper-plugin.yml`, `required: false`). Укажите `locale: ru` или `locale: en` в корневом `config.yml` и выполните `/javachats reload`.

Профили настроек хранятся в `temp/ru` и `temp/en` (`config.yml`, `message.yml`, `AIHELPER.yml`, `AIRULES.yml`). При загрузке файлы активной локали копируются в корень папки плагина, поэтому редактируйте файлы внутри `temp/{locale}`.

Публичный API и архитектура под Folia описаны в [docs/api.md](docs/api.md) и [docs/architecture.md](docs/architecture.md).

Не храните API-ключи в репозитории — задавайте их только в локальном конфиге сервера.
