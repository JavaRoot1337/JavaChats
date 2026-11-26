# JavaChats API v2.1

Точки входа:
- `ApiProvider.get()` (проверяйте готовность через `ApiProvider.isAvailable()`)
- `Bukkit.getServicesManager().load(JavaChatsApi.class)`

## Состав API

- `api.chat()` (`ChatSvc`) — публикация сообщений `publish(ChatReq)`, регистрация фильтров `registerFilter(ChatFilter)` и просмотр списка `filters()`.
- `api.privateMessages()` (`PmSvc`) — отправка личных сообщений `send(PmReq)` с доставкой через `EntityScheduler` отправителя и получателя.
- `api.moderation()` (`ModSvc`) — асинхронная AI-проверка `moderate(UUID, String)` и статус `isEnabled()`.

Модели `ChatReq`, `ChatDec`, `ChatRes`, `PmReq`, `PmRes`, `ModRes` реализованы как неизменяемые Java 21 `record`. `ModRes.available()` отражает доступность AI-провайдеров на момент проверки.

## Контракт потоков и фильтров

`publish(ChatReq)` и `send(PmReq)` безопасны для вызова из любого потока на Paper и Folia. Каждый `ChatFilter` получает неизменяемый `ChatReq` и возвращает `CompletionStage<ChatDec>`. Исключение, `null`-результат или `null` `CompletionStage` завершают публикацию со статусом `ChatRes.Status.UNAVAILABLE`.

Совместимость: Paper и Folia `1.21–26.3`, Java 21+, `paper-plugin.yml` (`folia-supported: true`), `LuckPerms` опционален.
