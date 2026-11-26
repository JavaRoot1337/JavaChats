# JavaChats

Плагин чата для Paper и Folia `1.21–26.3`: локальный и глобальный каналы, личные сообщения, `@упоминания` со звуком и титлом, асинхронная AI-модерация.

## Совместимость

| Параметр | Значение |
| --- | --- |
| Ядро | Paper, Folia (`folia-supported: true`) |
| Minecraft | `1.21 – 26.3` |
| Java | `21+` |
| Опционально | `LuckPerms` |

## Сборка

```powershell
.\gradlew.bat clean build
```

Итоговый JAR: `build/libs/JavaChats-2.0.jar`.

## Команды

| Команда | Действие | Право |
| --- | --- | --- |
| `/javachats reload` | Перезагрузить конфиг и профиль локали | `javachats.admin` |
| `/msg <игрок> <сообщение>` (`/tell`, `/w`) | Отправить личное сообщение | `javachats.msg` |
| `/aihelper add <plus\|minus> <сообщение>` | Добавить пример для AI-модерации | `javachats.admin` |

## Права

| Право | Назначение | По умолчанию |
| --- | --- | --- |
| `javachats.local.view` | Чтение локального чата | всем |
| `javachats.local.send` | Отправка в локальный чат | всем |
| `javachats.global.view` | Чтение глобального чата | всем |
| `javachats.global.send` | Отправка в глобальный чат | всем |
| `javachats.msg` | Использование `/msg`, `/tell`, `/w` | всем |
| `javachats.ai.log` | Просмотр логов AI-модерации | op |
| `javachats.admin` | Управление плагином и reload | op |

## Конфигурация

Укажите `locale: ru` или `locale: en` в `plugins/JavaChats/config.yml` и выполните `/javachats reload`.

Профили локалей лежат в `locate/ru` и `locate/en` (`config.yml`, `message.yml`, `AIHELPER.yml`, `AIRULES.yml`). Храните API-ключи только в локальном конфиге сервера.

## Документация

- [Публичный API](docs/apiru.md)
- [Архитектура](docs/architectureru.md)

[English documentation](README.md)
