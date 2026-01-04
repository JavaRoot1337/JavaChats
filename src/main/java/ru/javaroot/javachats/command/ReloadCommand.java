package ru.javaroot.javachats.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.javaroot.javachats.JavaChats;
import ru.javaroot.javachats.utils.ColorUtils;

public class ReloadCommand implements CommandExecutor {

    private final JavaChats plugin;

    public ReloadCommand(JavaChats plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("javachats.reload")) { // Although set in plugin.yml as javachats.admin, explicit
                                                         // check is good
            sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.getDiscordConfig().reload();
            sender.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.reload")));
            return true;
        }

        // If main command is just /javachats reload
        return false;
    }
}
