package ru.javaroot.javachats.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.javaroot.javachats.JavaChat;
import ru.javaroot.javachats.utils.TextUtil;

public class GlavCmd implements CommandExecutor {
    private final JavaChat plugin;

    public GlavCmd(JavaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("javachats.admin")) {
            sender.sendMessage(
                    TextUtil.format(plugin.getMessageConfig().getString("messages.no-permission", "&cНет прав.")));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfigs();
            sender.sendMessage(TextUtil.format(
                    plugin.getMessageConfig().getString("messages.reload", "&aКонфиги успешно перезагружены!")));
            return true;
        }

        sender.sendMessage(TextUtil.format(plugin.getMessageConfig().getString("messages.usage-javachats", "&7Использование: &a/javachats reload")));
        return true;
    }
}
