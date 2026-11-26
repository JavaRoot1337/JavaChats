package ru.javaroot.javachats.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.ai.AiSvc;
import ru.javaroot.javachats.api.ApiReg;
import ru.javaroot.javachats.api.ChatChan;
import ru.javaroot.javachats.api.ChatDec;
import ru.javaroot.javachats.api.ChatFilter;
import ru.javaroot.javachats.api.ChatReq;
import ru.javaroot.javachats.api.ChatRes;
import ru.javaroot.javachats.api.ChatSvc;
import ru.javaroot.javachats.api.ModRes;
import ru.javaroot.javachats.cfg.Cfg;
import ru.javaroot.javachats.cfg.Msg;
import ru.javaroot.javachats.hook.MetaHook;
import ru.javaroot.javachats.plat.Exec;
import ru.javaroot.javachats.plat.Hex;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public final class ChatEng implements ChatSvc, AutoCloseable {
    private final Exec exec;
    private final Supplier<Cfg> cfgSupplier;
    private final Supplier<Msg> msgSupplier;
    private final MetaHook meta;
    private final AiSvc ai;
    private final ChatPing pinger;
    private final ChatLog chatLog;

    private final List<ChatFilter> filters = new CopyOnWriteArrayList<>();
    private final Map<UUID, Long> lastMsgAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLocalAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGlobalAt = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMsg = new ConcurrentHashMap<>();

    public ChatEng(Exec exec, Supplier<Cfg> cfgSupplier, Supplier<Msg> msgSupplier,
            MetaHook meta, AiSvc ai, ChatPing pinger, ChatLog chatLog) {
        this.exec = exec;
        this.cfgSupplier = cfgSupplier;
        this.msgSupplier = msgSupplier;
        this.meta = meta;
        this.ai = ai;
        this.pinger = pinger;
        this.chatLog = chatLog;
    }

    public boolean handleChat(UUID uuid, String senderName, String raw) {
        if (uuid == null || senderName == null || raw == null) {
            return true;
        }
        if (raw.trim().isEmpty() || raw.length() > 4096) {
            sendPlayer(uuid, Hex.parse(msgSupplier.get().text("messages.usage-msg")));
            return true;
        }
        Cfg cfg = cfgSupplier.get();
        String symbol = cfg.global().symbol();
        boolean isGlobal = symbol != null && !symbol.isEmpty() && raw.startsWith(symbol);
        ChatChan chan = isGlobal ? ChatChan.GLOBAL : ChatChan.LOCAL;
        if (!cfg.channel(chan).enabled()) {
            return false;
        }

        exec.runPlayer(uuid, () -> processOnSender(uuid, senderName, raw, chan, symbol));
        return true;
    }

    private void processOnSender(UUID uuid, String senderName, String raw, ChatChan chan, String symbol) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) {
            return;
        }
        Cfg cfg = cfgSupplier.get();
        Msg msgCfg = msgSupplier.get();
        Cfg.Channel chanCfg = cfg.channel(chan);
        String perm = chanCfg.sendingPermission();
        if (perm != null && !perm.isEmpty() && !p.hasPermission(perm)) {
            p.sendMessage(Hex.parse(msgCfg.text("messages.no-permission")));
            return;
        }

        String text = chan == ChatChan.GLOBAL ? raw.substring(symbol.length()).trim() : raw;
        if (text.isEmpty()) {
            return;
        }

        if (cfg.antiRepeat() && text.equalsIgnoreCase(lastMsg.getOrDefault(uuid, ""))) {
            p.sendMessage(Hex.parse(msgCfg.text("anti-repeat")));
            return;
        }

        long now = System.currentTimeMillis();
        if (cfg.antiSpam().enabled()) {
            long prev = lastMsgAt.getOrDefault(uuid, 0L);
            if (prev > 0 && now - prev < cfg.antiSpam().delayMs()) {
                triggerSpamPunish(p, cfg.antiSpam(), msgCfg);
                return;
            }
        }

        Map<UUID, Long> chanTimes = chan == ChatChan.GLOBAL ? lastGlobalAt : lastLocalAt;
        long prevChan = chanTimes.getOrDefault(uuid, 0L);
        if (chanCfg.cooldownSeconds() >= 0 && prevChan > 0
                && now - prevChan < chanCfg.cooldownSeconds() * 1000L) {
            p.sendMessage(Hex.parse(msgCfg.text("messages.cooldown")));
            return;
        }

        if (cfg.antiSpam().enabled()) {
            lastMsgAt.put(uuid, now);
        }
        chanTimes.put(uuid, now);

        Location origin = p.getLocation().clone();
        publishWithOrigin(new ChatReq(uuid, senderName, chan, text), origin);
    }

    @Override
    public CompletionStage<ChatRes> publish(ChatReq req) {
        Objects.requireNonNull(req, "req");
        CompletableFuture<Location> locFuture = new CompletableFuture<>();
        boolean scheduled = exec.runPlayer(req.senderId(), () -> {
            Player p = Bukkit.getPlayer(req.senderId());
            locFuture.complete(p != null && p.isOnline() ? p.getLocation().clone() : null);
        });
        if (!scheduled) {
            return CompletableFuture.completedFuture(ChatRes.unavailable(req, "отправитель оффлайн"));
        }
        return locFuture.thenCompose(origin -> {
            if (origin == null) {
                return CompletableFuture.completedFuture(ChatRes.unavailable(req, "отправитель оффлайн"));
            }
            return publishWithOrigin(req, origin);
        });
    }

    private CompletionStage<ChatRes> publishWithOrigin(ChatReq req, Location origin) {
        CompletionStage<FilterState> chain = CompletableFuture.completedFuture(new FilterState(req, null));
        for (ChatFilter filter : filters) {
            chain = chain.thenCompose(state -> applyFilter(state, filter));
        }
        return chain.thenCompose(state -> {
            if (state.result() != null) {
                return CompletableFuture.completedFuture(state.result());
            }
            return ai.moderate(state.request().senderId(), state.request().senderName(), state.request().message())
                    .thenApply(mod -> dispatch(state.request(), mod, origin));
        });
    }

    private CompletionStage<FilterState> applyFilter(FilterState state, ChatFilter filter) {
        if (state.result() != null) {
            return CompletableFuture.completedFuture(state);
        }
        CompletionStage<ChatDec> dec;
        try {
            dec = filter.inspect(state.request());
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(new FilterState(
                    state.request(), ChatRes.unavailable(state.request(), ex.getMessage())));
        }
        if (dec == null) {
            return CompletableFuture.completedFuture(new FilterState(
                    state.request(), ChatRes.unavailable(state.request(), "фильтр вернул null")));
        }
        return dec.handle((val, err) -> {
            if (err != null || val == null) {
                return new FilterState(state.request(), ChatRes.unavailable(
                        state.request(), err == null ? "фильтр вернул null" : err.getMessage()));
            }
            return switch (val.action()) {
                case ALLOW -> state;
                case BLOCK -> new FilterState(state.request(), ChatRes.blocked(state.request(), val.reason()));
                case REPLACE -> new FilterState(new ChatReq(
                        state.request().senderId(), state.request().senderName(),
                        state.request().channel(), val.message()), null);
            };
        });
    }

    private ChatRes dispatch(ChatReq req, ModRes mod, Location origin) {
        Cfg cfg = cfgSupplier.get();
        if (!mod.available() && cfg.ai().failurePolicy() == Cfg.Ai.FailurePolicy.BLOCK) {
            return ChatRes.blocked(req, "модерация недоступна");
        }
        if (mod.violation() && mod.blocking()) {
            ai.punish(req.senderName(), mod);
            return ChatRes.blocked(req, mod.rule());
        }

        String original = req.message();
        ChatReq finalReq = mod.violation()
                ? new ChatReq(req.senderId(), req.senderName(), req.channel(), mod.censoredMessage().orElse(original))
                : req;

        if (!deliver(finalReq, mod.violation(), original, origin)) {
            return ChatRes.unavailable(finalReq, "некорректный формат чата или игрок вышел");
        }
        return ChatRes.published(finalReq, finalReq.message());
    }

    private boolean deliver(ChatReq req, boolean censored, String original, Location origin) {
        Msg msgCfg = msgSupplier.get();
        String format = msgCfg.text("chat." + req.channel().name().toLowerCase(Locale.ROOT));
        if (format == null || format.isEmpty()) {
            return false;
        }
        int playerIdx = format.indexOf("%player%");
        if (playerIdx < 0) {
            return false;
        }

        Cfg cfg = cfgSupplier.get();
        if (isCaps(req.message(), cfg.antiCaps())) {
            exec.runPlayer(req.senderId(), () -> {
                Player sender = Bukkit.getPlayer(req.senderId());
                if (sender == null || !sender.isOnline()) {
                    return;
                }
                sender.getWorld().strikeLightningEffect(sender.getLocation());
                Cfg.AntiCaps caps = cfg.antiCaps();
                Title.Times times = Hex.times(caps.fadeInMs(), caps.stayMs(), caps.fadeOutMs());
                sender.showTitle(Title.title(Hex.parse(msgCfg.text("anti-caps.title")), Component.empty(), times));
            });
        }

        String prefix = meta.prefix(req.senderId());
        String suffix = meta.suffix(req.senderId());
        String beforePlayer = format.substring(0, playerIdx).replace("%prefix%", prefix);
        String afterPlayer = format.substring(playerIdx + "%player%".length());
        String[] parts = afterPlayer.split("%message%", 2);

        Component prefixPart = Hex.parse(beforePlayer);
        Component playerPart = Hex.parse(Hex.activeColors(beforePlayer) + req.senderName())
                .hoverEvent(HoverEvent.showText(Hex.parse(msgCfg.text("pm.hover"))))
                .clickEvent(ClickEvent.suggestCommand("/msg " + req.senderName() + " "));
        Component suffixPart = Hex.parse(parts[0].replace("%suffix%", suffix));
        Component tailPart = parts.length < 2 ? Component.empty() : Hex.parse(parts[1]);

        Component consoleMsg = prefixPart.append(playerPart).append(suffixPart)
                .append(Hex.literal(req.message())).append(tailPart);
        Bukkit.getConsoleSender().sendMessage(consoleMsg);

        Set<UUID> mentioned = pinger.mentionedIds(req.message());
        Cfg.Channel chanCfg = cfg.channel(req.channel());
        Sound sound = ChatPing.findSound(chanCfg.sound());
        String viewPerm = chanCfg.viewingPermission();

        if (chanCfg.range() < 0) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                deliverToPlayer(online, viewPerm, req.message(), mentioned,
                        prefixPart, playerPart, suffixPart, tailPart, sound, chanCfg, null, -1.0);
            }
        } else {
            World world = origin.getWorld();
            if (world != null) {
                double maxDistSq = chanCfg.range() * (double) chanCfg.range();
                for (Player online : world.getPlayers()) {
                    deliverToPlayer(online, viewPerm, req.message(), mentioned,
                            prefixPart, playerPart, suffixPart, tailPart, sound, chanCfg, origin, maxDistSq);
                }
            }
        }

        lastMsg.put(req.senderId(), req.message());
        String logged = censored && cfg.censorSuffix() != null ? req.message() + cfg.censorSuffix() : req.message();
        chatLog.log(req.channel().name().toLowerCase(Locale.ROOT), Map.of(
                "player", req.senderName(),
                "original", original,
                "message", logged));
        return true;
    }

    private void deliverToPlayer(Player recipient, String viewPerm, String message, Set<UUID> mentioned,
            Component prefixPart, Component playerPart, Component suffixPart, Component tailPart,
            Sound sound, Cfg.Channel chanCfg, Location origin, double maxDistSq) {
        exec.runEntity(recipient, () -> {
            if (!recipient.isOnline()) {
                return;
            }
            if (viewPerm != null && !viewPerm.isEmpty() && !recipient.hasPermission(viewPerm)) {
                return;
            }
            if (origin != null && (!recipient.getWorld().equals(origin.getWorld())
                    || recipient.getLocation().distanceSquared(origin) > maxDistSq)) {
                return;
            }
            if (mentioned.contains(recipient.getUniqueId())) {
                pinger.notifyOnEntity(recipient);
            }
            Component displayed = mentioned.isEmpty()
                    ? Hex.literal(message)
                    : pinger.highlightFor(message, recipient.getName());
            recipient.sendMessage(prefixPart.append(playerPart).append(suffixPart).append(displayed).append(tailPart));
            if (sound != null) {
                recipient.playSound(recipient.getLocation(), sound, (float) chanCfg.volume(), (float) chanCfg.pitch());
            }
        });
    }

    private void triggerSpamPunish(Player p, Cfg.AntiSpam spam, Msg msgCfg) {
        Title.Times times = Hex.times(spam.fadeInMs(), spam.stayMs(), spam.fadeOutMs());
        Component subtitle = Hex.parse(msgCfg.text("anti-spam.subtitle"));
        for (int i = 0; i < spam.strikes(); i++) {
            long delay = i * spam.strikeDelayTicks();
            exec.runEntityLater(p, () -> {
                if (!p.isOnline()) {
                    return;
                }
                p.getWorld().strikeLightningEffect(p.getLocation());
                p.showTitle(Title.title(Component.empty(), subtitle, times));
            }, delay);
        }
    }

    public static boolean isCaps(String message, Cfg.AntiCaps cfg) {
        if (message.length() < cfg.minLength()) {
            return false;
        }
        long letters = message.codePoints().filter(Character::isLetter).count();
        if (letters == 0) {
            return false;
        }
        long caps = message.codePoints().filter(Character::isUpperCase).count();
        return (double) caps / letters * 100.0 > cfg.percent();
    }

    private void sendPlayer(UUID uuid, Component component) {
        exec.runPlayer(uuid, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(component);
            }
        });
    }

    public void cleanPlayer(UUID uuid) {
        lastMsgAt.remove(uuid);
        lastLocalAt.remove(uuid);
        lastGlobalAt.remove(uuid);
        lastMsg.remove(uuid);
    }

    @Override
    public ApiReg registerFilter(ChatFilter filter) {
        Objects.requireNonNull(filter, "filter");
        filters.add(filter);
        return new ApiReg() {
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
        return List.copyOf(filters);
    }

    @Override
    public void close() {
        filters.clear();
        lastMsgAt.clear();
        lastLocalAt.clear();
        lastGlobalAt.clear();
        lastMsg.clear();
    }

    private record FilterState(ChatReq request, ChatRes result) {
    }
}
