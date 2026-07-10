package ru.javaroot.javachats.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.ChatPlugin;
import ru.javaroot.javachats.utils.TextUtil;
import java.util.Arrays;

public class PMCommand implements CommandExecutor {
    private final ChatPlugin plugin;

    public PMCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msgCfg = plugin.getMessageConfig();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.format(msgCfg.getString("messages.only-players", "&cТолько игроки могут писать личные сообщения.")));
            return true;
        }

        if (args.length < 2) return false;

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(TextUtil.format(msgCfg.getString("messages.no-player")));
            return true;
        }

        String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String senderMsg = msgCfg.getString("pm.sender", "").replace("%player%", target.getName()).replace("%message%", msg);
        String receiverMsg = msgCfg.getString("pm.receiver", "").replace("%player%", player.getName()).replace("%message%", msg);

        player.sendMessage(TextUtil.format(senderMsg));
        target.sendMessage(TextUtil.format(receiverMsg));
        return true;
    }
}
