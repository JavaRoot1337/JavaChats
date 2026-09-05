package ru.javaroot.javachats.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.api.PrivateMessageRequest;
import ru.javaroot.javachats.utils.TextUtil;

import java.util.Arrays;

public class MsgCmd implements CommandExecutor {
    private final JavaChat plugin;

    public MsgCmd(JavaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("messages.only-players")));
            return true;
        }
        if (args.length < 2) {
            p.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("messages.usage-msg")));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            p.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("messages.no-player")));
            return true;
        }
        if (target.equals(p)) {
            p.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("messages.cannot-msg-self")));
            return true;
        }

        String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (msg.isEmpty()) {
            p.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("messages.usage-msg")));
            return true;
        }

        plugin.getPrivateMessages().send(new PrivateMessageRequest(
                p.getUniqueId(), p.getName(), target.getUniqueId(), target.getName(), msg));
        return true;
    }
}
