# JavaChats API v1

Публичная точка входа: `JavaChat#getApi()`.

`api.chat()` сохраняет операции `publish(ChatRequest)`, `registerFilter(ChatFilter)` и `filters()`. `ChatRequest`, `ChatDecision`, `ChatResult`, `PrivateMessageRequest`, `PrivateMessageResult` и `ModerationResult` — immutable Java 8 classes с record-style accessor-методами, фабриками и проверками прежнего контракта.

`publish` можно вызывать из любого потока. Фильтры получают immutable request и возвращают `CompletionStage<ChatDecision>`. Bukkit-доставка выполняется через scheduler плагина; callback фильтра не должен обращаться к Bukkit state.

`api.privateMessages()` возвращает результаты `SENT`, `RECIPIENT_OFFLINE` или `UNAVAILABLE`. `api.moderation()` асинхронно использует AI providers и возвращает вероятность, правило, найденные слова и optional censored message.

Совместимость: Paper-only 1.16.5–26.2, bytecode Java 8. Команды, permissions, aliases, YAML-ключи, `plugin.yml`, LuckPerms soft dependency и фасады `JavaChat`, `AiMod`, `ChatPinger` сохранены. `getMessageConfig()` оставлен как compatibility facade; новый runtime использует immutable snapshots.
