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
import java.util.concurrent.Executors;
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
            plugin.getLogs().warning("chat-file-pattern", Map.of("error", String.valueOf(ex.getMessage())));
            return;
        }

        String folderName = plugin.getConfig().getString("logs.chat.folder");
        String extension = plugin.getConfig().getString("logs.chat.file-extension");
        if (folderName == null || folderName.isEmpty() || extension == null) {
            plugin.getLogs().warning("chat-file-config", Map.of(
                    "folder", String.valueOf(folderName),
                    "extension", String.valueOf(extension)));
            return;
        }

        try {
            File folder = new File(plugin.getDataFolder(), folderName);
            Files.createDirectories(folder.toPath());
            String fileName = fileFormat.format(LocalDateTime.now()) + extension;
            logFile = folder.toPath().resolve(fileName);
            writer = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
            log("startup", Map.of());
        } catch (IOException ex) {
            plugin.getLogs().warning("chat-file-create", Map.of("error", String.valueOf(ex.getMessage())));
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
        String line = plugin.getLogs().render("logs.chat.line-format", Map.of(
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
        currentWriter.execute(() -> writeLine(currentFile, line));
    }

    private void writeLine(Path file, String line) {
        try {
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось записать лог чата: " + ex.getMessage());
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
