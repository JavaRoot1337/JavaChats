# JavaChats API v2.1

Entry points:
- `ApiProvider.get()` (check availability via `ApiProvider.isAvailable()`)
- `Bukkit.getServicesManager().load(JavaChatsApi.class)`

## Services

- `api.chat()` (`ChatSvc`) — `publish(ChatReq)`, `registerFilter(ChatFilter)`, `filters()`
- `api.privateMessages()` (`PmSvc`) — `send(PmReq)` via `EntityScheduler` of sender and target
- `api.moderation()` (`ModSvc`) — async `moderate(UUID, String)` and `isEnabled()`

DTOs `ChatReq`, `ChatDec`, `ChatRes`, `PmReq`, `PmRes`, `ModRes` are immutable Java 21 `record` types. `ModRes.available()` reports whether AI providers were reachable during the check.

## Thread & filter contract

`publish(ChatReq)` and `send(PmReq)` are safe to call from any thread on Paper and Folia. Each `ChatFilter` receives an immutable `ChatReq` and returns `CompletionStage<ChatDec>`. Throwing, returning `null`, or returning a `null` stage completes the publish with `ChatRes.Status.UNAVAILABLE`.

Target: Paper & Folia `1.21–26.3`, Java `21+`, `paper-plugin.yml` (`folia-supported: true`).

[Русская версия](apiru.md)
