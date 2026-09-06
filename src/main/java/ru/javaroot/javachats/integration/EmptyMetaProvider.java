package ru.javaroot.javachats.integration;

import java.util.UUID;

public final class EmptyMetaProvider implements MetaProvider {
    @Override
    public String prefix(UUID playerId) {
        return "";
    }

    @Override
    public String suffix(UUID playerId) {
        return "";
    }
}
