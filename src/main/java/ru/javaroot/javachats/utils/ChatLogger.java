package ru.javaroot.javachats.utils;

import ru.javaroot.JavaChat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ChatLogger {
    private final JavaChat plugin;
    private Path logFile;
    private DateTimeFormatter fileFormat;
    private DateTimeFormatter timeFormat;
    private ExecutorService writer;

    public ChatLogger(JavaChat plugin) {
        this.plugin = plugin;
    }

    public synchronized void init() {
        reload();
    }

    public synchronized void reload() {
        close();
        if (!plugin.getConfig().getBoolean("logs.chat.enabled")) {
            return;
        }

        try {
            fileFormat = DateTimeFormatter.ofPattern(plugin.getConfig().getString("logs.chat.file-name-pattern"));
            timeFormat = DateTimeFormatter.ofPattern(plugin.getConfig().getString("logs.chat.time-pattern"));
        } catch (IllegalArgumentException | NullPointerException ex) {
            plugin.getLogs().warning("chat-file-pattern", LogVars.of("error", String.valueOf(ex.getMessage())));
            return;
        }

        String folderName = plugin.getConfig().getString("logs.chat.folder");
        String extension = plugin.getConfig().getString("logs.chat.file-extension");
        if (folderName == null || folderName.isEmpty() || extension == null) {
            plugin.getLogs().warning("chat-file-config", LogVars.of(
                    "folder", String.valueOf(folderName),
                    "extension", String.valueOf(extension)));
            return;
        }

        try {
            Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
            Path folder = dataFolder.resolve(folderName).normalize();
            if (!folder.startsWith(dataFolder) || extension.indexOf('/') >= 0 || extension.indexOf('\\') >= 0) {
                plugin.getLogs().warning("chat-file-config", LogVars.of(
                        "folder", folderName, "extension", extension));
                return;
            }
            Files.createDirectories(folder);
            String fileName = fileFormat.format(LocalDateTime.now()) + extension;
            logFile = folder.resolve(fileName).normalize();
            writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<Runnable>(256), runnable -> {
                        Thread thread = new Thread(runnable, "JavaChats-chat-log");
                        thread.setDaemon(true);
                        return thread;
                    }, new ThreadPoolExecutor.AbortPolicy());
            log("startup", LogVars.of());
        } catch (IOException ex) {
            plugin.getLogs().warning("chat-file-create", LogVars.of("error", String.valueOf(ex.getMessage())));
        }
    }

    public synchronized void log(String type, Map<String, String> vars) {
        if (logFile == null || timeFormat == null) {
            return;
        }
        String message = plugin.getLogs().render("logs.chat." + type, vars);
        if (message == null || message.isEmpty()) {
            return;
        }
        String line = plugin.getLogs().render("logs.chat.line-format", LogVars.of(
                "time", timeFormat.format(LocalDateTime.now()),
                "message", message));
        if (line == null) {
            return;
        }
        ExecutorService currentWriter = writer;
        if (currentWriter == null) {
            return;
        }
        Path currentFile = logFile;
        try {
            currentWriter.execute(() -> writeLine(currentFile, line));
        } catch (RejectedExecutionException ignored) {
            plugin.getLogs().warning("chat-file-write", LogVars.of("error", "log queue is full"));
        }
    }

    private void writeLine(Path file, String line) {
        try {
            Files.write(file, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogs().warning("chat-file-write", LogVars.of("error", String.valueOf(ex.getMessage())));
        }
    }

    public synchronized void close() {
        ExecutorService currentWriter = writer;
        writer = null;
        if (currentWriter != null) {
            currentWriter.shutdown();
            try {
                if (!currentWriter.awaitTermination(2, TimeUnit.SECONDS)) {
                    currentWriter.shutdownNow();
                }
            } catch (InterruptedException ex) {
                currentWriter.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logFile = null;
        fileFormat = null;
        timeFormat = null;
    }
}
