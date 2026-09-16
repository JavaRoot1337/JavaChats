package ru.javaroot.javachats.api;

public interface ApiRegistration extends AutoCloseable {
    boolean isRegistered();

    @Override
    void close();
}
