package ru.javaroot.javachats.integration;

import java.util.UUID;

public interface MetaProvider {
    String prefix(UUID playerId);
    String suffix(UUID playerId);
}
