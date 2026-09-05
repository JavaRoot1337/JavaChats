package ru.javaroot.javachats.api;

import java.util.concurrent.CompletionStage;

public interface PrivateMessageService {
    CompletionStage<PrivateMessageResult> send(PrivateMessageRequest request);
}
