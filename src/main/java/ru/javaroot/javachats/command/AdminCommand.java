package ru.javaroot.javachats.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.javaroot.javachats.ChatPlugin;
import ru.javaroot.javachats.utils.TextUtil;

public class AdminCommand implements CommandExecutor {
    private final ChatPlugin plugin;

    public AdminCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msgCfg = plugin.getMessageConfig();

        if (!sender.hasPermission("javachats.admin")) {
            sender.sendMessage(TextUtil.format(msgCfg.getString("messages.no-permission")));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfigs();
            sender.sendMessage(TextUtil.format(plugin.getMessageConfig().getString("messages.reload")));
            return true;
        }
        return false;
    }
}
