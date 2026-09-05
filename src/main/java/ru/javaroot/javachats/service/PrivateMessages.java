package ru.javaroot.javachats.service;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.api.PrivateMessageRequest;
import ru.javaroot.javachats.api.PrivateMessageResult;
import ru.javaroot.javachats.api.PrivateMessageService;
import ru.javaroot.javachats.runtime.ServerScheduler;
import ru.javaroot.javachats.utils.TextUtil;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PrivateMessages implements PrivateMessageService {
    private final JavaChat plugin;
    private final ServerScheduler scheduler;

    public PrivateMessages(JavaChat plugin, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<PrivateMessageResult> send(PrivateMessageRequest request) {
        CompletableFuture<PrivateMessageResult> result = new CompletableFuture<>();
        scheduler.runServer(() -> {
            Player sender = Bukkit.getPlayer(request.senderId());
            Player recipient = Bukkit.getPlayer(request.recipientId());
            if (sender == null || !sender.isOnline() || recipient == null || !recipient.isOnline()) {
                result.complete(new PrivateMessageResult(PrivateMessageResult.Status.RECIPIENT_OFFLINE, request,
                        "sender or recipient is offline"));
                return;
            }

            String senderFormat = plugin.getMessageSnapshot().text("pm.sender");
            String recipientFormat = plugin.getMessageSnapshot().text("pm.receiver");
            Component toSender = TextUtil.format(senderFormat
                    .replace("%player%", recipient.getName())
                    .replace("%message%", request.message()));
            Component toRecipient = TextUtil.format(recipientFormat
                    .replace("%player%", sender.getName())
                    .replace("%message%", request.message()));
            sender.sendMessage(toSender);
            recipient.sendMessage(toRecipient);
            if (plugin.getChatLogger() != null) {
                plugin.getChatLogger().log("private", Map.of(
                        "sender", sender.getName(),
                        "target", recipient.getName(),
                        "message", request.message()));
            }
            result.complete(new PrivateMessageResult(PrivateMessageResult.Status.SENT, request, null));
        });
        return result;
    }
}
