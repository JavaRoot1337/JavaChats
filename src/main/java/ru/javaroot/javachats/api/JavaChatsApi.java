package ru.javaroot.javachats.api;

public interface JavaChatsApi {
    String API_VERSION = "1";

    String apiVersion();

    ChatService chat();

    PrivateMessageService privateMessages();

    ModerationService moderation();
}
