package ru.javaroot.javachats.aihelper;

import ru.javaroot.JavaChat;
import ru.javaroot.javachats.utils.LogVars;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class HttpAiClient implements AutoCloseable {
    private final JavaChat plugin;
    private final String key;
    private final URL endpoint;
    private final int timeoutMs;
    private final String name;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
        return CompletableFuture.supplyAsync(() -> {
            try {
                return send(body);
            } catch (IOException ex) {
                plugin.getLogs().warning(name + "-request", LogVars.of("error", String.valueOf(ex.getMessage())));
                return null;
            }
        }, executor);
    }

    private String send(String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + key);
        try {
            OutputStream output = connection.getOutputStream();
            try {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            } finally {
                output.close();
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                plugin.getLogs().warning(name + "-http", LogVars.of("status", String.valueOf(status)));
                return null;
            }

            InputStream input = connection.getInputStream();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                try {
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    return result.toString();
                } finally {
                    reader.close();
                }
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static URL toUrl(String value) {
        try {
            return URI.create(value).toURL();
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid endpoint", ex);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
