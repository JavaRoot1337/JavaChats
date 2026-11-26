package ru.javaroot.javachats.chat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import ru.javaroot.javachats.cfg.LogCfg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ChatLog implements AutoCloseable {
    private final Plugin plug;
    private final LogCfg logs;
    private Path logFile;
    private DateTimeFormatter fileFmt;
    private DateTimeFormatter timeFmt;
    private ExecutorService writer;

    public ChatLog(Plugin plug, LogCfg logs) {
        this.plug = plug;
        this.logs = logs;
    }

    public synchronized void reload(FileConfiguration cfg) {
        close();
        if (!cfg.getBoolean("logs.chat.enabled")) {
            return;
        }
        try {
            fileFmt = DateTimeFormatter.ofPattern(cfg.getString("logs.chat.file-name-pattern"));
            timeFmt = DateTimeFormatter.ofPattern(cfg.getString("logs.chat.time-pattern"));
        } catch (IllegalArgumentException | NullPointerException ex) {
            logs.warning("chat-file-pattern", Map.of("error", String.valueOf(ex.getMessage())));
            return;
        }

        String folderName = cfg.getString("logs.chat.folder");
        String ext = cfg.getString("logs.chat.file-extension");
        if (folderName == null || folderName.isEmpty() || ext == null) {
            logs.warning("chat-file-config", Map.of("folder", String.valueOf(folderName), "extension", String.valueOf(ext)));
            return;
        }

        try {
            Path dataDir = plug.getDataFolder().toPath().toAbsolutePath().normalize();
            Path folder = dataDir.resolve(folderName).normalize();
            if (!folder.startsWith(dataDir) || ext.indexOf('/') >= 0 || ext.indexOf('\\') >= 0) {
                logs.warning("chat-file-config", Map.of("folder", folderName, "extension", ext));
                return;
            }
            Files.createDirectories(folder);
            logFile = folder.resolve(fileFmt.format(LocalDateTime.now()) + ext).normalize();
            writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(256), r -> {
                        Thread t = new Thread(r, "JavaChats-chat-log");
                        t.setDaemon(true);
                        return t;
                    }, new ThreadPoolExecutor.AbortPolicy());
            log("startup", Map.of());
        } catch (IOException ex) {
            logs.warning("chat-file-create", Map.of("error", String.valueOf(ex.getMessage())));
        }
    }

    public synchronized void log(String type, Map<String, String> vars) {
        if (logFile == null || timeFmt == null || writer == null) {
            return;
        }
        String text = logs.render("logs.chat." + type, vars);
        if (text == null || text.isEmpty()) {
            return;
        }
        String line = logs.render("logs.chat.line-format", Map.of(
                "time", timeFmt.format(LocalDateTime.now()),
                "message", text));
        if (line == null) {
            return;
        }
        Path target = logFile;
        try {
            writer.execute(() -> writeLine(target, line));
        } catch (RejectedExecutionException ignored) {
            logs.warning("chat-file-write", Map.of("error", "очередь записи лога переполнена"));
        }
    }

    private void writeLine(Path file, String line) {
        try {
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            logs.warning("chat-file-write", Map.of("error", String.valueOf(ex.getMessage())));
        }
    }

    @Override
    public synchronized void close() {
        ExecutorService current = writer;
        writer = null;
        if (current != null) {
            current.shutdown();
            try {
                if (!current.awaitTermination(2, TimeUnit.SECONDS)) {
                    current.shutdownNow();
                }
            } catch (InterruptedException ex) {
                current.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logFile = null;
        fileFmt = null;
        timeFmt = null;
    }
}
