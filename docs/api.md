# JavaChats API v2

Публичная точка входа: `JavaChat#getApi()`.

`api.chat()` предоставляет операции `publish(ChatRequest)`, `registerFilter(ChatFilter)` и `filters()`. `ChatRequest`, `ChatDecision`, `ChatResult`, `PrivateMessageRequest`, `PrivateMessageResult` и `ModerationResult` — immutable Java 8 classes с record-style accessor-методами и фабриками. `ModerationResult.available()` показывает, был ли получен достоверный ответ провайдера.

`publish` можно вызывать из любого потока. Каждый вызов `ChatFilter.inspect` и запуск следующего фильтра выполняются на server thread. Фильтр получает immutable request и возвращает `CompletionStage<ChatDecision>`; его асинхронный callback не должен обращаться к Bukkit state. Исключение, `null` stage или `null` decision завершают публикацию со статусом `UNAVAILABLE`.

`api.privateMessages()` возвращает результаты `SENT`, `RECIPIENT_OFFLINE` или `UNAVAILABLE`. `api.moderation()` асинхронно использует AI providers и возвращает вероятность, правило, найденные слова и optional censored message.

Совместимость: Paper-only 1.16.5–26.2, bytecode Java 8. Команды, permissions, aliases, YAML-ключи, `plugin.yml` и LuckPerms soft dependency сохранены. Внутренние concrete-классы и mutable config facade не являются частью API v2.
