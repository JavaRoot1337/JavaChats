package ru.javaroot.javachats.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.JavaChats;
import ru.javaroot.javachats.utils.ColorUtils;

import java.util.Arrays;

public class MsgCommand implements CommandExecutor {

    private final JavaChats plugin;

    public MsgCommand(JavaChats plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только игроки могут использовать эту команду.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            return false;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.no-player")));
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        String senderFormat = plugin.getConfig().getString("messages.msg-format-sender");
        String receiverFormat = plugin.getConfig().getString("messages.msg-format-receiver");

        String senderMsg = senderFormat.replace("%player%", target.getName()).replace("%message%", message);
        String receiverMsg = receiverFormat.replace("%player%", player.getName()).replace("%message%", message);

        player.sendMessage(ColorUtils.colorize(senderMsg));
        target.sendMessage(ColorUtils.colorize(receiverMsg));

        return true;
    }
}
