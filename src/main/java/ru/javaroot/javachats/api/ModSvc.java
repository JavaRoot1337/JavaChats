package ru.javaroot.javachats.api;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Асинхронный сервис AI-модерации сообщений.
 */
public interface ModSvc {
    CompletionStage<ModRes> moderate(UUID playerId, String message);

    boolean isEnabled();
}
