package ru.javaroot.javachats.api;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ChatFilter {
    CompletionStage<ChatDecision> inspect(ChatRequest request);
}
