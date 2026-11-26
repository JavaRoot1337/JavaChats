package ru.javaroot.javachats.api;

/**
 * Корневой контракт публичного API плагина JavaChats.
 */
public interface JavaChatsApi {
    String API_VERSION = "2.1";

    String apiVersion();

    ChatSvc chat();

    PmSvc privateMessages();

    ModSvc moderation();
}
