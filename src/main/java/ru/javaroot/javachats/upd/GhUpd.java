package ru.javaroot.javachats.upd;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.plugin.Plugin;
import ru.javaroot.javachats.cfg.Cfg;
import ru.javaroot.javachats.plat.Exec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class GhUpd implements AutoCloseable {
    private static final URI API_URI = URI.create("https://api.github.com/repos/JavaRoot1337/JavaChats/releases/latest");
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Plugin plug;
    private final Exec exec;
    private final Supplier<Cfg> cfgSupplier;
    private final Consumer<GhUpd> onUpdate;
    private final AtomicBoolean checking = new AtomicBoolean();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private volatile boolean closed;
    private volatile String latestVersion;
    private volatile String releaseUrl;

    public GhUpd(Plugin plug, Exec exec, Supplier<Cfg> cfgSupplier, Consumer<GhUpd> onUpdate) {
        this.plug = plug;
        this.exec = exec;
        this.cfgSupplier = cfgSupplier;
        this.onUpdate = onUpdate;
    }

    public void checkForUpdates() {
        Cfg cfg = cfgSupplier.get();
        if (closed || cfg == null || !cfg.updateCheck() || !checking.compareAndSet(false, true)) {
            return;
        }
        String current = getCurrentVersion();
        if (Ver.parse(current) == null) {
            checking.set(false);
            return;
        }

        exec.runAsync(() -> {
            try {
                Result res = fetchLatest(current);
                if (res != null && !closed) {
                    exec.runGlobal(() -> apply(res));
                }
            } finally {
                checking.set(false);
            }
        });
    }

    public String getCurrentVersion() {
        return plug.getPluginMeta().getVersion();
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getReleaseUrl() {
        return releaseUrl;
    }

    @Override
    public void close() {
        closed = true;
    }

    private Result fetchLatest(String current) {
        HttpRequest req = HttpRequest.newBuilder(API_URI)
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "JavaChats/" + current)
                .GET()
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300 || res.body() == null) {
                return null;
            }
            return parseRelease(res.body(), current);
        } catch (IOException | RuntimeException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Result parseRelease(String raw, String current) {
        JsonObject json;
        try {
            json = GSON.fromJson(raw, JsonObject.class);
        } catch (RuntimeException ex) {
            return null;
        }
        if (json == null) {
            return null;
        }
        String tag = str(json, "tag_name");
        String url = str(json, "html_url");
        Ver latest = Ver.parse(tag);
        Ver installed = Ver.parse(current);
        if (latest == null || installed == null || !isHttpUrl(url)) {
            return null;
        }
        return new Result(latest.toString(), url, latest.compareTo(installed) > 0);
    }

    private void apply(Result res) {
        if (closed) {
            return;
        }
        boolean notify = res.updateAvailable() && !res.version().equals(latestVersion);
        latestVersion = res.version();
        releaseUrl = res.url();
        if (notify && onUpdate != null) {
            onUpdate.accept(this);
        }
    }

    private static String str(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()
                ? el.getAsString().trim()
                : null;
    }

    private static boolean isHttpUrl(String raw) {
        if (raw == null) {
            return false;
        }
        try {
            URI uri = URI.create(raw);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private record Result(String version, String url, boolean updateAvailable) {
    }
}
