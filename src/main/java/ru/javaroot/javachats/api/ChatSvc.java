package ru.javaroot.javachats.api;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Сервис публикации сообщений и управления фильтрами чата.
 */
public interface ChatSvc {
    CompletionStage<ChatRes> publish(ChatReq request);

    ApiReg registerFilter(ChatFilter filter);

    List<ChatFilter> filters();
}
