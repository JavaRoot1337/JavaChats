package ru.javaroot.javachats.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.ChatPinger;
import ru.javaroot.javachats.api.ApiRegistration;
import ru.javaroot.javachats.api.ChatChannel;
import ru.javaroot.javachats.api.ChatDecision;
import ru.javaroot.javachats.api.ChatFilter;
import ru.javaroot.javachats.api.ChatRequest;
import ru.javaroot.javachats.api.ChatResult;
import ru.javaroot.javachats.api.ChatService;
import ru.javaroot.javachats.api.ModerationResult;
import ru.javaroot.javachats.config.RuntimeConfig;
import ru.javaroot.javachats.integration.MetaProvider;
import ru.javaroot.javachats.runtime.ServerScheduler;
import ru.javaroot.javachats.utils.LogVars;
import ru.javaroot.javachats.utils.TextUtil;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.function.Supplier;

public class ChatList implements ChatService {
    private final JavaChat plugin;
    private final ChatPinger pinger;
    private final ServerScheduler scheduler;
    private final List<ChatFilter> filters = new CopyOnWriteArrayList<>();
    private final Map<UUID, Long> lastMsgTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLocalTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGlobalTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();

    public ChatList(JavaChat plugin, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.pinger = new ChatPinger(plugin);
        this.scheduler = scheduler;
    }

    public boolean handleChat(Player player, String message) {
        return handleChat(player.getUniqueId(), player.getName(), message);
    }

    public boolean handleChat(UUID uuid, String senderName, String message) {
        if (uuid == null || senderName == null || message == null) {
            return true;
        }
        if (message.trim().isEmpty() || message.length() > 4096) {
            send(uuid, TextUtil.format(plugin.getMessageSnapshot().text("messages.usage-msg")));
            return true;
        }

        RuntimeConfig cfg = plugin.getRuntimeConfig();
        String globalSymbol = cfg.global().symbol();
        boolean global = globalSymbol != null && !globalSymbol.isEmpty() && message.startsWith(globalSymbol);
        ChatChannel channel = global ? ChatChannel.GLOBAL : ChatChannel.LOCAL;
        if (!cfg.channel(channel).enabled()) {
            return false;
        }

        scheduler.runServer(() -> processChat(uuid, senderName, message, channel, globalSymbol));
        return true;
    }

    private void processChat(UUID uuid, String senderName, String rawMessage, ChatChannel channel,
            String globalSymbol) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        RuntimeConfig cfg = plugin.getRuntimeConfig();
        String message = rawMessage;
        long now = System.currentTimeMillis();
        boolean global = channel == ChatChannel.GLOBAL;

        RuntimeConfig.Channel channelConfig = cfg.channel(channel);
        String permission = channelConfig.sendingPermission();
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
            send(uuid, TextUtil.format(plugin.getMessageSnapshot().text("messages.no-permission")));
            return;
        }

        if (global) {
            message = message.substring(globalSymbol.length()).trim();
            if (message.isEmpty()) {
                return;
            }
        }

        if (cfg.antiRepeat() && message.equalsIgnoreCase(lastMessages.getOrDefault(uuid, ""))) {
            send(uuid, TextUtil.format(plugin.getMessageSnapshot().text("anti-repeat")));
            return;
        }

        if (cfg.antiSpam().enabled()) {
            long lastTime = lastMsgTime.getOrDefault(uuid, 0L);
            if (lastTime > 0 && now - lastTime < cfg.antiSpam().delayMs()) {
                triggerSpamPunish(uuid);
                return;
            }
        }

        Map<UUID, Long> channelTimes = global ? lastGlobalTime : lastLocalTime;
        long lastChannelTime = channelTimes.getOrDefault(uuid, 0L);
        if (channelConfig.cooldownSeconds() >= 0 && lastChannelTime > 0
                && now - lastChannelTime < channelConfig.cooldownSeconds() * 1000L) {
            send(uuid, TextUtil.format(plugin.getMessageSnapshot().text("messages.cooldown")));
            return;
        }
        if (cfg.antiSpam().enabled()) {
            lastMsgTime.put(uuid, now);
        }
        channelTimes.put(uuid, now);

        ChatRequest request = new ChatRequest(uuid, senderName, channel, message);
        publish(request);
    }

    @Override
    public CompletionStage<ChatResult> publish(ChatRequest request) {
        return onServer(() -> publishOnServer(request));
    }

    private CompletionStage<ChatResult> publishOnServer(ChatRequest request) {
        CompletableFuture<FilterState> chain = CompletableFuture.completedFuture(new FilterState(request, null));
        for (ChatFilter filter : filters) {
            chain = chain.thenCompose(state -> onServer(() -> applyFilter(state, filter)));
        }

        return chain.thenCompose(state -> onServer(() -> {
            if (state.result() != null) {
                return CompletableFuture.completedFuture(state.result());
            }
            return plugin.getAiMod().moderate(request.senderId(), state.request().message())
                    .thenCompose(moderation -> dispatch(state.request(), moderation));
        }));
    }

    private <T> CompletionStage<T> onServer(Supplier<CompletionStage<T>> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        org.bukkit.scheduler.BukkitTask task = scheduler.runServer(() -> {
            try {
                CompletionStage<T> stage = action.get();
                if (stage == null) {
                    result.completeExceptionally(new IllegalStateException("server action returned null"));
                    return;
                }
                stage.whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                });
            } catch (RuntimeException error) {
                result.completeExceptionally(error);
            }
        });
        if (task == null) {
            result.completeExceptionally(new IllegalStateException("plugin is shutting down"));
        }
        return result;
    }

    @Override
    public ApiRegistration registerFilter(ChatFilter filter) {
        filters.add(java.util.Objects.requireNonNull(filter, "filter"));
        return new ApiRegistration() {
            private volatile boolean registered = true;

            @Override
            public boolean isRegistered() {
                return registered;
            }

            @Override
            public void close() {
                if (registered) {
                    registered = false;
                    filters.remove(filter);
                }
            }
        };
    }

    @Override
    public List<ChatFilter> filters() {
        return Collections.unmodifiableList(new java.util.ArrayList<ChatFilter>(filters));
    }

    private CompletionStage<FilterState> applyFilter(FilterState state, ChatFilter filter) {
        if (state.result() != null) {
            return CompletableFuture.completedFuture(state);
        }
        CompletionStage<ChatDecision> decision;
        try {
            decision = filter.inspect(state.request());
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(new FilterState(state.request(),
                    ChatResult.unavailable(state.request(), error.getMessage())));
        }
        if (decision == null) {
            return CompletableFuture.completedFuture(new FilterState(state.request(),
                    ChatResult.unavailable(state.request(), "filter returned null")));
        }
        return decision.handle((value, error) -> {
            if (error != null || value == null) {
                return new FilterState(state.request(), ChatResult.unavailable(state.request(),
                        error == null ? "filter returned null" : error.getMessage()));
            }
            switch (value.action()) {
                case ALLOW:
                    return state;
                case BLOCK:
                    return new FilterState(state.request(), ChatResult.blocked(state.request(), value.reason()));
                case REPLACE:
                    return new FilterState(
                            new ChatRequest(state.request().senderId(), state.request().senderName(),
                                    state.request().channel(), value.message()), null);
                default:
                    return new FilterState(state.request(), ChatResult.unavailable(state.request(),
                            "unknown filter action"));
            }
        });
    }

    private CompletionStage<ChatResult> dispatch(ChatRequest request, ModerationResult moderation) {
        if (!moderation.available()
                && plugin.getRuntimeConfig().ai().failurePolicy() == RuntimeConfig.Ai.FailurePolicy.BLOCK) {
            return CompletableFuture.completedFuture(ChatResult.blocked(request, "moderation unavailable"));
        }
        if (moderation.violation() && moderation.blocking()) {
            plugin.getAiMod().punish(request.senderName(), moderation);
            return CompletableFuture.completedFuture(ChatResult.blocked(request, moderation.rule()));
        }
        String originalMessage = request.message();
        if (moderation.violation()) {
            String message = moderation.censoredMessage().orElse(request.message());
            request = new ChatRequest(request.senderId(), request.senderName(), request.channel(), message);
        }
        ChatRequest finalRequest = request;
        CompletableFuture<ChatResult> result = new CompletableFuture<>();
        org.bukkit.scheduler.BukkitTask task = scheduler.runServer(() -> {
            if (sendChat(finalRequest, moderation.violation(), originalMessage)) {
                result.complete(ChatResult.published(finalRequest, finalRequest.message()));
            } else {
                result.complete(ChatResult.unavailable(finalRequest, "sender is offline or format is invalid"));
            }
        });
        if (task == null) {
            result.complete(ChatResult.unavailable(finalRequest, "plugin is shutting down"));
        }
        return result;
    }

    private boolean sendChat(ChatRequest request, boolean censored, String originalMessage) {
        Player sender = Bukkit.getPlayer(request.senderId());
        if (sender == null || !sender.isOnline()) {
            return false;
        }

        RuntimeConfig cfg = plugin.getRuntimeConfig();
        String format = plugin.getMessageSnapshot().text("chat." + request.channel().name().toLowerCase(Locale.ROOT));
        if (format == null || format.isEmpty()) {
            return false;
        }

        if (isCaps(request.message())) {
            sender.getWorld().strikeLightningEffect(sender.getLocation());
            RuntimeConfig.AntiCaps antiCaps = cfg.antiCaps();
            Title.Times times = TextUtil.titleTimes(
                    Duration.ofMillis(antiCaps.fadeInMs()),
                    Duration.ofMillis(antiCaps.stayMs()),
                    Duration.ofMillis(antiCaps.fadeOutMs()));
            sender.showTitle(Title.title(TextUtil.format(plugin.getMessageSnapshot().text("anti-caps.title")),
                    Component.empty(), times));
        }

        MetaProvider meta = plugin.getMetaProvider();
        String prefix = meta == null ? "" : meta.prefix(request.senderId());
        String suffix = meta == null ? "" : meta.suffix(request.senderId());

        int playerIndex = format.indexOf("%player%");
        if (playerIndex < 0) {
            return false;
        }

        String beforePlayer = format.substring(0, playerIndex).replace("%prefix%", prefix);
        String afterPlayer = format.substring(playerIndex + "%player%".length());
        String[] messageParts = afterPlayer.split("%message%", 2);
        Component prefixComponent = TextUtil.format(beforePlayer);
        Component playerComponent = TextUtil.format(TextUtil.getColors(beforePlayer) + sender.getName())
                .hoverEvent(HoverEvent.showText(TextUtil.format(plugin.getMessageSnapshot().text("pm.hover"))))
                .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "));
        Component suffixComponent = TextUtil.format(messageParts[0].replace("%suffix%", suffix));
        Component tailComponent = messageParts.length < 2 ? Component.empty() : TextUtil.format(messageParts[1]);
        Component consoleMessage = prefixComponent.append(playerComponent).append(suffixComponent)
                .append(TextUtil.literal(request.message())).append(tailComponent);

        Set<Player> recipients = getRecipients(sender, request.channel());
        Set<Player> mentioned = pinger.getMentionedPlayers(request.message());
        for (Player mentionedPlayer : mentioned) {
            if (recipients.contains(mentionedPlayer)) {
                pinger.sendNotification(mentionedPlayer);
            }
        }

        RuntimeConfig.Channel channel = cfg.channel(request.channel());
        Sound sound = getSound(channel.sound());
        for (Player recipient : recipients) {
            Component displayed = mentioned.isEmpty()
                    ? TextUtil.literal(request.message())
                    : pinger.processComponentFor(request.message(), recipient);
            Component fullMessage = prefixComponent.append(playerComponent).append(suffixComponent)
                    .append(displayed).append(tailComponent);
            recipient.sendMessage(fullMessage);
            if (sound != null) {
                recipient.playSound(recipient.getLocation(), sound, (float) channel.volume(), (float) channel.pitch());
            }
        }

        Bukkit.getConsoleSender().sendMessage(consoleMessage);
        lastMessages.put(request.senderId(), request.message());
        if (plugin.getChatLogger() != null) {
            String loggedMessage = request.message();
            if (censored) {
                loggedMessage += cfg.censorSuffix() == null ? "" : cfg.censorSuffix();
            }
            plugin.getChatLogger().log(request.channel().name().toLowerCase(Locale.ROOT), LogVars.of(
                    "player", sender.getName(),
                    "original", originalMessage,
                    "message", loggedMessage));
        }
        return true;
    }

    private Set<Player> getRecipients(Player sender, ChatChannel channel) {
        RuntimeConfig.Channel cfg = plugin.getRuntimeConfig().channel(channel);
        Set<Player> recipients = new HashSet<>();
        String permission = cfg.viewingPermission();
        if (cfg.range() < 0) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (permission == null || permission.isEmpty() || online.hasPermission(permission)) {
                    recipients.add(online);
                }
            }
            return recipients;
        }

        Location location = sender.getLocation();
        double maxDistance = cfg.range() * (double) cfg.range();
        for (Player online : location.getWorld().getPlayers()) {
            if (online.getLocation().distanceSquared(location) <= maxDistance
                    && (permission == null || permission.isEmpty() || online.hasPermission(permission))) {
                recipients.add(online);
            }
        }
        return recipients;
    }

    private Sound getSound(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return ChatPinger.sound(name);
    }

    private void triggerSpamPunish(UUID uuid) {
        RuntimeConfig.AntiSpam cfg = plugin.getRuntimeConfig().antiSpam();
        String subtitle = plugin.getMessageSnapshot().text("anti-spam.subtitle");
        for (int i = 0; i < cfg.strikes(); i++) {
            scheduler.runServerLater(() -> scheduler.runForPlayer(uuid, () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    return;
                }
                player.getWorld().strikeLightningEffect(player.getLocation());
                Title.Times times = TextUtil.titleTimes(
                        Duration.ofMillis(cfg.fadeInMs()),
                        Duration.ofMillis(cfg.stayMs()),
                        Duration.ofMillis(cfg.fadeOutMs()));
                player.showTitle(Title.title(Component.empty(), TextUtil.format(subtitle), times));
            }), i * cfg.strikeDelayTicks());
        }
    }

    private boolean isCaps(String message) {
        RuntimeConfig.AntiCaps cfg = plugin.getRuntimeConfig().antiCaps();
        if (message.length() < cfg.minLength()) {
            return false;
        }
        long letters = message.codePoints().filter(Character::isLetter).count();
        if (letters == 0) {
            return false;
        }
        long caps = message.codePoints().filter(Character::isUpperCase).count();
        return (double) caps / letters * 100 > cfg.percent();
    }

    private void send(UUID uuid, Component component) {
        scheduler.runForPlayer(uuid, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(component);
            }
        });
    }

    public void cleanPlayerData(UUID uuid) {
        lastMsgTime.remove(uuid);
        lastLocalTime.remove(uuid);
        lastGlobalTime.remove(uuid);
        lastMessages.remove(uuid);
    }

    public void close() {
        filters.clear();
        lastMsgTime.clear();
        lastLocalTime.clear();
        lastGlobalTime.clear();
        lastMessages.clear();
    }

    private static final class FilterState {
        private final ChatRequest request;
        private final ChatResult result;

        private FilterState(ChatRequest request, ChatResult result) {
            this.request = request;
            this.result = result;
        }

        private ChatRequest request() {
            return request;
        }

        private ChatResult result() {
            return result;
        }
    }
}
