package ru.javaroot.javachats.api;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ChatService {
    CompletionStage<ChatResult> publish(ChatRequest request);

    ApiRegistration registerFilter(ChatFilter filter);

    List<ChatFilter> filters();
}
