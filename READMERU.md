# JavaChats

Paper-only плагин чата для Paper 1.16.5–26.2: локальный и глобальный чат, ЛС, упоминания и AI-модерация.

## Сборка

Сборка использует Gradle wrapper 9.2.1 и JDK 25. Классы плагина компилируются с `--release 8` (bytecode major 52), поэтому JVM выбирается сервером: Java 16 для Paper 1.16.5 и Java 25 для Paper 26.1+.

```powershell
.\gradlew.bat clean build
```

Универсальный release JAR находится в `build/libs/JavaChats-1.0.jar`. Переместите его в папку `plugins/` сервера.

## Команды

- `/javachats reload`
- `/msg <игрок> <сообщение>`
- `/aihelper add <plus|minus> <сообщение>`

LuckPerms необязателен. Укажите `locale: ru` или `locale: en` в корневом `config.yml`, затем выполните `/javachats reload`. Рабочие настройки находятся в `temp/ru` и `temp/en`; в каждой папке лежат `config.yml`, `message.yml`, `AIHELPER.yml` и `AIRULES.yml`. Активный профиль также выгружается в корень папки плагина; редактируйте файлы в `temp/{locale}`, поскольку root-файлы являются копиями.

Публичный API и архитектура описаны в `docs/api.md` и `docs/architecture.md`. Перед выпуском нужны runtime smoke-тесты на реальных Paper 1.16.5 и 26.2.

Не добавляйте API-ключи в репозиторий. Используйте локальную конфигурацию сервера и заменяйте раскрытые ключи.
