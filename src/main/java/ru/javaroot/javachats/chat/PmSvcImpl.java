package ru.javaroot.javachats.chat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.api.PmReq;
import ru.javaroot.javachats.api.PmRes;
import ru.javaroot.javachats.api.PmSvc;
import ru.javaroot.javachats.cfg.Msg;
import ru.javaroot.javachats.plat.Exec;
import ru.javaroot.javachats.plat.Hex;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class PmSvcImpl implements PmSvc {
    private final Exec exec;
    private final Supplier<Msg> msgSupplier;
    private final ChatLog chatLog;

    public PmSvcImpl(Exec exec, Supplier<Msg> msgSupplier, ChatLog chatLog) {
        this.exec = exec;
        this.msgSupplier = msgSupplier;
        this.chatLog = chatLog;
    }

    @Override
    public CompletableFuture<PmRes> send(PmReq req) {
        Player sender = Bukkit.getPlayer(req.senderId());
        Player recipient = Bukkit.getPlayer(req.recipientId());
        if (sender == null || !sender.isOnline() || recipient == null || !recipient.isOnline()) {
            return CompletableFuture.completedFuture(
                    new PmRes(PmRes.Status.RECIPIENT_OFFLINE, req, "отправитель или получатель оффлайн"));
        }

        Msg msg = msgSupplier.get();
        String senderName = sender.getName();
        String recipientName = recipient.getName();
        Component toSender = Hex.formatTemplate(msg.text("pm.sender").replace("%player%", recipientName), req.message());
        Component toRecipient = Hex.formatTemplate(msg.text("pm.receiver").replace("%player%", senderName), req.message());

        boolean sentSender = exec.runPlayer(req.senderId(), () -> {
            Player p = Bukkit.getPlayer(req.senderId());
            if (p != null && p.isOnline()) {
                p.sendMessage(toSender);
            }
        });
        boolean sentRecipient = exec.runPlayer(req.recipientId(), () -> {
            Player p = Bukkit.getPlayer(req.recipientId());
            if (p != null && p.isOnline()) {
                p.sendMessage(toRecipient);
            }
        });
        if (!sentSender || !sentRecipient) {
            return CompletableFuture.completedFuture(new PmRes(PmRes.Status.UNAVAILABLE, req, "плагин останавливается"));
        }

        chatLog.log("private", Map.of("sender", senderName, "target", recipientName, "message", req.message()));
        return CompletableFuture.completedFuture(new PmRes(PmRes.Status.SENT, req, null));
    }
}
