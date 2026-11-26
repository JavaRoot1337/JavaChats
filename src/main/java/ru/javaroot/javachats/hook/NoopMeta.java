package ru.javaroot.javachats.hook;

import java.util.UUID;

public final class NoopMeta implements MetaHook {
    @Override
    public String prefix(UUID playerId) {
        return "";
    }

    @Override
    public String suffix(UUID playerId) {
        return "";
    }
}
