# JavaChats API v1

Публичная точка входа: `JavaChat#getApi()`.

## ChatService

`api.chat()` предоставляет:

- `publish(ChatRequest)` для публикации локального или глобального сообщения;
- `registerFilter(ChatFilter)` для подключения собственного фильтра;
- `filters()` для получения неизменяемого снимка зарегистрированных фильтров.

`ChatRequest` содержит UUID отправителя, его имя, тип канала и текст. Внутри API текст и имя нормализуются по краям, а null и пустые значения отклоняются.

Фильтр получает immutable request и возвращает `CompletionStage<ChatDecision>`. `ALLOW` продолжает обработку, `BLOCK` отменяет публикацию, `REPLACE` передаёт следующий фильтр изменённый текст. Ошибка или null-ответ фильтра дают `UNAVAILABLE` и не публикуют сообщение.

`publish` можно вызвать из любого потока. Доставка и обращения к Bukkit выполняются scheduler-слоем плагина. Callback фильтра должен заниматься только собственными данными и внешним I/O.

`api.privateMessages()` отправляет `PrivateMessageRequest` с UUID отправителя и получателя. Результат явно различает `SENT`, `RECIPIENT_OFFLINE` и `UNAVAILABLE`.

## ModerationService

`api.moderation()` предоставляет `moderate(UUID, String)`. HTTP-запросы к Mistral и Groq выполняются асинхронно. Результат содержит вероятность, правило, список найденных слов и optional censored message. Persistence и API-ключи через этот контракт не выдаются.

## Совместимость

Команды, имена конфигурационных файлов, YAML-ключи, `plugin.yml`, LuckPerms soft dependency и старые фасады `JavaChat`, `AiMod`, `ChatPinger` сохранены. `getMessageConfig()` оставлен как compatibility facade; новый runtime-код использует immutable snapshots.
