package ru.javaroot.javachats.api;

import java.util.concurrent.CompletionStage;

/**
 * Фильтр сообщений чата. Вызывается асинхронно до AI-модерации и рассылки.
 */
@FunctionalInterface
public interface ChatFilter {
    CompletionStage<ChatDec> inspect(ChatReq request);
}
