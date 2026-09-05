package ru.javaroot.javachats.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.utils.TextUtil;

public class GlavCmd implements CommandExecutor {
    private final JavaChat plugin;

    public GlavCmd(JavaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("javachats.admin")) {
            sender.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("messages.no-permission")));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfigs();
            sender.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("messages.reload")));
            return true;
        }

        sender.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("messages.usage-javachats")));
        return true;
    }
}
