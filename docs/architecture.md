# Архитектура JavaChats

## Решения

1. Gradle wrapper и Java 25 используются как единственная build-система. Paper API берётся как `compileOnly` с линией `26.2.build.+`, LuckPerms остаётся Paper library и compile-only API.
2. `JavaChat` только собирает зависимости и управляет lifecycle. Chat, AI moderation, commands, config snapshots, scheduler и file logging разделены по доменам.
3. Runtime configuration и message data загружаются в immutable snapshots. Reload сначала строит новые snapshots, затем атомарно заменяет ссылки и перезапускает AI worker.
4. Bukkit/Paper state выполняется через Paper global/entity scheduler, HTTP и запись chat log выполняются вне игрового потока. Долгоживущие player state maps используют UUID.
5. Публичный API не возвращает `FileConfiguration`, базы или provider internals. Compatibility getter оставлен только для старых расширений.

## Границы

У проекта нет database persistence. Обучающие примеры AI и chat log являются файловым I/O. Поэтому SQLite и migration layer не добавлялись: это не существующий домен JavaChats.

Folia ранее не была заявлена в descriptor и не имела Folia-safe recipient resolution. Миграция сохраняет существующую Paper-only runtime contract; автоматический claim полной Folia-совместимости не делается.
