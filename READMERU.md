# JavaChats

Плагин чата для Paper 26.2: локальный и глобальный чат, ЛС, упоминания и AI-модерация.

## Сборка

Нужна Java 25. В проекте есть Gradle wrapper 9.2.1.

```bash
./gradlew clean build
```

Готовый JAR находится в `build/libs/JavaChats-1.0.jar`. Переместите его в `plugins/`.

## Команды

- `/javachats reload`
- `/msg <игрок> <сообщение>`
- `/aihelper add <plus|minus> <сообщение>`

LuckPerms необязателен. Настройки находятся в `config.yml`, `message.yml`, `AIHELPER.yml` и `AIRULES.yml`.

Публичный API описан в `docs/api.md`. Фильтры чата вызываются асинхронно и не должны обращаться к Bukkit из callback.

Не добавляйте API-ключи в репозиторий. Уже раскрытый ключ нужно заменить.
