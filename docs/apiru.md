# JavaChats API v2.1

Точки входа:
- `ApiProvider.get()` (проверка готовности через `ApiProvider.isAvailable()`)
- `Bukkit.getServicesManager().load(JavaChatsApi.class)`

## Сервисы

- `api.chat()` (`ChatSvc`) — `publish(ChatReq)`, `registerFilter(ChatFilter)`, `filters()`
- `api.privateMessages()` (`PmSvc`) — `send(PmReq)` через `EntityScheduler` отправителя и получателя
- `api.moderation()` (`ModSvc`) — асинхронная проверка `moderate(UUID, String)` и статус `isEnabled()`

Модели `ChatReq`, `ChatDec`, `ChatRes`, `PmReq`, `PmRes`, `ModRes` — неизменяемые `record` на Java 21. `ModRes.available()` отражает доступность AI-провайдеров на момент проверки.

## Контракт потоков и фильтров

`publish(ChatReq)` и `send(PmReq)` безопасны для вызова из любого потока на Paper и Folia. Каждый `ChatFilter` получает неизменяемый `ChatReq` и возвращает `CompletionStage<ChatDec>`. Исключение, `null`-результат или `null`-стадия завершают публикацию со статусом `ChatRes.Status.UNAVAILABLE`.

Совместимость: Paper и Folia `1.21–26.3`, Java `21+`, `paper-plugin.yml` (`folia-supported: true`).

[English version](api.md)
