package ru.javaroot.javachats.api;

/**
 * Хэндл регистрации стороннего фильтра чата.
 */
public interface ApiReg extends AutoCloseable {
    boolean isRegistered();

    @Override
    void close();
}
