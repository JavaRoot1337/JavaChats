package ru.javaroot.javachats.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Точка доступа к активному {@link JavaChatsApi}.
 */
public final class ApiProvider {
    private static volatile JavaChatsApi instance;

    private ApiProvider() {
    }

    /**
     * Возвращает загруженный экземпляр API.
     *
     * @throws IllegalStateException если плагин ещё не включён или уже выгружен
     */
    public static @NotNull JavaChatsApi get() {
        JavaChatsApi api = instance;
        if (api == null) {
            throw new IllegalStateException("API JavaChats не загружен");
        }
        return api;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    @ApiStatus.Internal
    public static void register(@NotNull JavaChatsApi api) {
        instance = api;
    }

    @ApiStatus.Internal
    public static void unregister() {
        instance = null;
    }
}
