# JavaChats API v2

Публичная точка входа: `JavaChat#getApi()`.

`api.chat()` предоставляет операции `publish(ChatRequest)`, `registerFilter(ChatFilter)` и `filters()`. `ChatRequest`, `ChatDecision`, `ChatResult`, `PrivateMessageRequest`, `PrivateMessageResult` и `ModerationResult` — immutable Java 8 classes с record-style accessor-методами и фабриками. `ModerationResult.available()` показывает, был ли получен достоверный ответ провайдера.

`publish` можно вызывать из любого потока. Фильтры получают immutable request и возвращают `CompletionStage<ChatDecision>`. Bukkit-доставка выполняется через scheduler плагина; callback фильтра не должен обращаться к Bukkit state.

`api.privateMessages()` возвращает результаты `SENT`, `RECIPIENT_OFFLINE` или `UNAVAILABLE`. `api.moderation()` асинхронно использует AI providers и возвращает вероятность, правило, найденные слова и optional censored message.

Совместимость: Paper-only 1.16.5–26.2, bytecode Java 8. Команды, permissions, aliases, YAML-ключи, `plugin.yml` и LuckPerms soft dependency сохранены. Внутренние concrete-классы и mutable config facade не являются частью API v2.
