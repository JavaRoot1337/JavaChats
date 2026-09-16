package ru.javaroot.javachats.api;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ModerationService {
    CompletionStage<ModerationResult> moderate(UUID playerId, String message);

    boolean isEnabled();
}
