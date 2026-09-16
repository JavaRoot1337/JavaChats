// класс полностью спизжен и адаптирован
package ru.javaroot.javachats.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import org.bukkit.scheduler.BukkitTask;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.config.RuntimeConfig;
import ru.javaroot.javachats.runtime.ServerScheduler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class gitupdater implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final String GITHUB_OWNER = "JavaRoot1337";
    private static final String GITHUB_REPOSITORY = "JavaChats";
    private static final String API_URL = "https://api.github.com/repos/"
            + GITHUB_OWNER + "/" + GITHUB_REPOSITORY + "/releases/latest";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_RESPONSE_CHARS = 2_000_000;

    private final JavaChat plugin;
    private final ServerScheduler scheduler;
    private final Consumer<gitupdater> updateHandler;
    private final AtomicBoolean checking = new AtomicBoolean();
    private volatile boolean closed;
    private volatile BukkitTask checkTask;
    private volatile boolean updateAvailable;
    private volatile String latestVersion;
    private volatile String latestTagName;
    private volatile String releaseUrl;
    private volatile String downloadUrl;
    private volatile List<ReleaseAsset> releaseAssets = Collections.emptyList();

    public gitupdater(JavaChat plugin, ServerScheduler scheduler, Consumer<gitupdater> updateHandler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.updateHandler = updateHandler;
    }

    public void checkForUpdates() {
        RuntimeConfig config = plugin.getRuntimeConfig();
        if (closed || config == null || !config.updateCheck() || !checking.compareAndSet(false, true)) {
            return;
        }

        String currentVersion = getCurrentVersion();
        if (Version.parse(currentVersion) == null) {
            checking.set(false);
            return;
        }

        BukkitTask task = scheduler.runAsync(() -> {
            try {
                UpdateResult result = requestLatestRelease(currentVersion);
                if (result != null && !closed) {
                    scheduler.runServer(() -> applyResult(result));
                }
            } catch (RuntimeException ignored) {
            } finally {
                checking.set(false);
                checkTask = null;
            }
        });
        checkTask = task;
        if (task == null) {
            checking.set(false);
        } else if (closed) {
            task.cancel();
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getCurrentVersion() {
        return plugin.getDescription().getVersion();
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getLatestTagName() {
        return latestTagName;
    }

    public String getReleaseUrl() {
        return releaseUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public List<ReleaseAsset> getReleaseAssets() {
        return releaseAssets;
    }

    @Override
    public void close() {
        closed = true;
        BukkitTask task = checkTask;
        if (task != null) {
            task.cancel();
        }
    }

    private UpdateResult requestLatestRelease(String currentVersion) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "JavaChats/" + currentVersion);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return null;
            }

            String response = readResponse(connection.getInputStream());
            if (response == null || response.trim().isEmpty()) {
                return null;
            }
            return parseRelease(response, currentVersion);
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void applyResult(UpdateResult result) {
        if (closed) {
            return;
        }

        boolean announce = result.updateAvailable && !result.version.equals(latestVersion);
        updateAvailable = result.updateAvailable;
        latestVersion = result.version;
        latestTagName = result.tagName;
        releaseUrl = result.releaseUrl;
        downloadUrl = result.downloadUrl;
        releaseAssets = result.assets;

        if (announce && updateHandler != null) {
            updateHandler.accept(this);
        }
    }

    private static String readResponse(InputStream input) {
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            StringBuilder response = new StringBuilder();
            int length;
            while ((length = reader.read(buffer)) != -1) {
                response.append(buffer, 0, length);
                if (response.length() > MAX_RESPONSE_CHARS) {
                    return null;
                }
            }
            return response.toString();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static UpdateResult parseRelease(String response, String currentVersion) {
        JsonObject release;
        try {
            release = GSON.fromJson(response, JsonObject.class);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (release == null) {
            return null;
        }

        String tagName = optionalString(release, "tag_name");
        Version version = Version.parse(tagName);
        String htmlUrl = optionalUrl(release, "html_url");
        if (version == null || htmlUrl == null) {
            return null;
        }
        List<ReleaseAsset> assets = parseAssets(release);
        String jarUrl = null;
        for (ReleaseAsset asset : assets) {
            if (asset.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
                    && asset.getBrowserDownloadUrl() != null) {
                jarUrl = asset.getBrowserDownloadUrl();
                break;
            }
        }

        Version installed = Version.parse(currentVersion);
        if (installed == null) {
            return null;
        }
        return new UpdateResult(version.toString(), tagName, htmlUrl, assets, jarUrl,
                version.compareTo(installed) > 0);
    }

    private static List<ReleaseAsset> parseAssets(JsonObject release) {
        JsonElement assetsElement = release.get("assets");
        if (assetsElement == null || !assetsElement.isJsonArray()) {
            return Collections.emptyList();
        }

        JsonArray assetsJson = assetsElement.getAsJsonArray();
        List<ReleaseAsset> assets = new ArrayList<ReleaseAsset>(assetsJson.size());
        for (JsonElement assetElement : assetsJson) {
            if (!assetElement.isJsonObject()) {
                continue;
            }
            JsonObject asset = assetElement.getAsJsonObject();
            String name = optionalString(asset, "name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            String browserDownloadUrl = optionalUrl(asset, "browser_download_url");
            String contentType = optionalString(asset, "content_type");
            long size = optionalLong(asset, "size");
            assets.add(new ReleaseAsset(name, browserDownloadUrl, contentType, size));
        }
        return Collections.unmodifiableList(assets);
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        return value.getAsString().trim();
    }

    private static String optionalUrl(JsonObject object, String key) {
        String value = optionalString(object, key);
        return value != null && isHttpUrl(value) ? value : null;
    }

    private static long optionalLong(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return -1L;
        }
        try {
            return value.getAsLong();
        } catch (RuntimeException error) {
            return -1L;
        }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    public static final class ReleaseAsset {
        private final String name;
        private final String browserDownloadUrl;
        private final String contentType;
        private final long size;

        private ReleaseAsset(String name, String browserDownloadUrl, String contentType, long size) {
            this.name = name;
            this.browserDownloadUrl = browserDownloadUrl;
            this.contentType = contentType;
            this.size = size;
        }

        public String getName() {
            return name;
        }

        public String getBrowserDownloadUrl() {
            return browserDownloadUrl;
        }

        public String getContentType() {
            return contentType;
        }

        public long getSize() {
            return size;
        }
    }

    private static final class UpdateResult {
        private final String version;
        private final String tagName;
        private final String releaseUrl;
        private final List<ReleaseAsset> assets;
        private final String downloadUrl;
        private final boolean updateAvailable;

        private UpdateResult(String version, String tagName, String releaseUrl,
                List<ReleaseAsset> assets, String downloadUrl, boolean updateAvailable) {
            this.version = version;
            this.tagName = tagName;
            this.releaseUrl = releaseUrl;
            this.assets = assets;
            this.downloadUrl = downloadUrl;
            this.updateAvailable = updateAvailable;
        }
    }

    private static final class Version implements Comparable<Version> {
        private final List<Integer> components;

        private Version(List<Integer> components) {
            this.components = components;
        }

        private static Version parse(String raw) {
            if (raw == null) {
                return null;
            }
            String value = raw.trim();
            if (value.startsWith("v") || value.startsWith("V")) {
                value = value.substring(1);
            }
            if (value.isEmpty()) {
                return null;
            }

            String[] parts = value.split("\\.", -1);
            List<Integer> components = new ArrayList<Integer>(parts.length);
            for (String part : parts) {
                if (part.isEmpty() || (part.length() > 1 && part.charAt(0) == '0')) {
                    return null;
                }
                for (int i = 0; i < part.length(); i++) {
                    if (part.charAt(i) < '0' || part.charAt(i) > '9') {
                        return null;
                    }
                }
                try {
                    components.add(Integer.parseInt(part));
                } catch (NumberFormatException error) {
                    return null;
                }
            }
            return new Version(Collections.unmodifiableList(components));
        }

        @Override
        public int compareTo(Version other) {
            int length = Math.max(components.size(), other.components.size());
            for (int i = 0; i < length; i++) {
                int first = i < components.size() ? components.get(i) : 0;
                int second = i < other.components.size() ? other.components.get(i) : 0;
                int comparison = Integer.compare(first, second);
                if (comparison != 0) {
                    return comparison;
                }
            }
            return 0;
        }

        @Override
        public String toString() {
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < components.size(); i++) {
                if (i > 0) {
                    value.append('.');
                }
                value.append(components.get(i));
            }
            return value.toString();
        }
    }
}
