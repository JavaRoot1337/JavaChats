package ru.javaroot.javachats.ai;

import ru.javaroot.javachats.cfg.LogCfg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class AiClient implements AutoCloseable {
    private static final int MAX_REQ_BYTES = 64 * 1024;
    private static final int MAX_RES_BYTES = 256 * 1024;
    private static final int MAX_PENDING = 32;

    private final LogCfg logs;
    private final String key;
    private final URI endpoint;
    private final Duration timeout;
    private final long cooldownMs;
    private final String name;
    private final Semaphore queue = new Semaphore(MAX_PENDING);
    private final Set<CompletableFuture<String>> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "JavaChats-ai-http");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient http;
    private long nextSlotAt;

    AiClient(LogCfg logs, String key, String endpoint, long timeoutSec, long cooldownSec, String name) {
        this.logs = logs;
        this.key = key;
        this.endpoint = parseUri(endpoint);
        this.timeout = Duration.ofSeconds(Math.max(1L, timeoutSec));
        this.cooldownMs = Math.max(0L, cooldownSec) * 1000L;
        this.name = name;
        this.http = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .executor(scheduler)
                .build();
    }

    CompletableFuture<String> post(String body) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("AI-клиент остановлен"));
        }
        if (body == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("пустое тело запроса"));
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REQ_BYTES) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("тело запроса превышает лимит"));
        }
        if (!queue.tryAcquire()) {
            return CompletableFuture.failedFuture(new IllegalStateException("очередь AI-запросов переполнена"));
        }

        CompletableFuture<String> result = new CompletableFuture<>();
        pending.add(result);
        result.whenComplete((v, err) -> {
            pending.remove(result);
            queue.release();
        });

        long delayMs = reserveDelayMs();
        if (delayMs == 0L) {
            dispatch(bytes, result);
        } else {
            scheduler.schedule(() -> {
                if (!result.isDone() && !closed.get()) {
                    dispatch(bytes, result);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
        return result;
    }

    private synchronized long reserveDelayMs() {
        if (cooldownMs == 0L) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        long startAt = Math.max(now, nextSlotAt);
        nextSlotAt = startAt + cooldownMs;
        return startAt - now;
    }

    private void dispatch(byte[] bytes, CompletableFuture<String> result) {
        HttpRequest req = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

        CompletableFuture<HttpResponse<InputStream>> response = http.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream());
        result.whenComplete((v, err) -> {
            if (result.isCancelled()) {
                response.cancel(true);
            }
        });
        response.whenComplete((res, err) -> {
            if (err != null) {
                logs.warning(name + "-request", Map.of("error", String.valueOf(err.getMessage())));
                result.completeExceptionally(err);
                return;
            }
            try (InputStream in = res.body()) {
                int status = res.statusCode();
                if (status < 200 || status >= 300) {
                    logs.warning(name + "-http", Map.of("status", String.valueOf(status)));
                    result.completeExceptionally(new IOException("HTTP " + status));
                    return;
                }
                result.complete(readBody(in));
            } catch (IOException ex) {
                logs.warning(name + "-request", Map.of("error", String.valueOf(ex.getMessage())));
                result.completeExceptionally(ex);
            }
        });
    }

    private static String readBody(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buf)) != -1) {
            if (read > MAX_RES_BYTES - total) {
                throw new IOException("ответ превышает лимит");
            }
            out.write(buf, 0, read);
            total += read;
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static URI parseUri(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("пустой endpoint");
        }
        URI uri = URI.create(raw.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("endpoint должен использовать HTTPS без учётных данных");
        }
        return uri;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IllegalStateException err = new IllegalStateException("AI-клиент остановлен");
        for (CompletableFuture<String> future : pending) {
            future.completeExceptionally(err);
        }
        pending.clear();
        scheduler.shutdownNow();
    }
}
