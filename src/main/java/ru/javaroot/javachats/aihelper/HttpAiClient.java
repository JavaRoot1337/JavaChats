package ru.javaroot.javachats.aihelper;

import ru.javaroot.JavaChat;
import ru.javaroot.javachats.utils.LogVars;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class HttpAiClient implements AutoCloseable {
    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_PENDING_REQUESTS = 32;
    private final JavaChat plugin;
    private final String key;
    private final URL endpoint;
    private final int timeoutMs;
    private final String name;
    private final Set<CompletableFuture<String>> pending = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(MAX_PENDING_REQUESTS),
            runnable -> {
                Thread thread = new Thread(runnable, "JavaChats-" + "ai-http");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    HttpAiClient(JavaChat plugin, String key, String endpoint, long timeoutSeconds, String name) {
        this.plugin = plugin;
        this.key = key;
        this.endpoint = toUrl(endpoint);
        long timeout = timeoutSeconds > Integer.MAX_VALUE / 1000L
                ? Integer.MAX_VALUE
                : Math.max(1L, timeoutSeconds * 1000L);
        this.timeoutMs = (int) timeout;
        this.name = name;
    }

    CompletableFuture<String> post(String body) {
        if (body == null) {
            return failedFuture(new IllegalArgumentException("request body is null"));
        }
        byte[] request = body.getBytes(StandardCharsets.UTF_8);
        if (request.length > MAX_REQUEST_BYTES) {
            return failedFuture(new IllegalArgumentException("request body is too large"));
        }
        CompletableFuture<String> result = new CompletableFuture<>();
        pending.add(result);
        try {
            executor.execute(() -> {
                try {
                    result.complete(send(request));
                } catch (IOException ex) {
                    plugin.getLogs().warning(name + "-request", LogVars.of("error", String.valueOf(ex.getMessage())));
                    result.completeExceptionally(ex);
                } finally {
                    pending.remove(result);
                }
            });
            return result;
        } catch (RejectedExecutionException ex) {
            pending.remove(result);
            result.completeExceptionally(new IllegalStateException("AI request queue is full", ex));
            return result;
        }
    }

    private String send(byte[] body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + key);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                plugin.getLogs().warning(name + "-http", LogVars.of("status", String.valueOf(status)));
                throw new IOException("HTTP status " + status);
            }

            int contentLength = connection.getContentLength();
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw new IOException("response is too large");
            }
            try (InputStream input = connection.getInputStream()) {
                return readResponse(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String readResponse(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read > MAX_RESPONSE_BYTES - total) {
                throw new IOException("response is too large");
            }
            result.write(buffer, 0, read);
            total += read;
        }
        return new String(result.toByteArray(), StandardCharsets.UTF_8);
    }

    private static URL toUrl(String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("endpoint is empty");
            }
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("endpoint must use HTTPS");
            }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("endpoint must contain a host without credentials or fragment");
            }
            return uri.toURL();
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid endpoint", ex);
        }
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(error);
        return future;
    }

    @Override
    public void close() {
        IllegalStateException error = new IllegalStateException("AI client is closed");
        for (CompletableFuture<String> request : pending) {
            request.completeExceptionally(error);
        }
        pending.clear();
        executor.shutdownNow();
    }
}
