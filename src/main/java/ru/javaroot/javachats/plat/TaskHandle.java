package ru.javaroot.javachats.plat;

public interface TaskHandle {
    void cancel();

    boolean isCancelled();
}
