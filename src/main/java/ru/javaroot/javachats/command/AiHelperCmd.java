package ru.javaroot.javachats.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.utils.TextUtil;

import java.util.Arrays;
import java.util.List;

public class AiHelperCmd implements CommandExecutor, TabCompleter {
    private final JavaChat plugin;

    public AiHelperCmd(JavaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("javachats.admin")) {
            sender.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("messages.no-permission")));
            return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("add")) {
            boolean plus = args[1].equalsIgnoreCase("plus");
            boolean minus = args[1].equalsIgnoreCase("minus");
            if (!plus && !minus) {
                sender.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("ai-helper.usage-aihelper")));
                return true;
            }

            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            if (plugin.getAiMod() == null) {
                sender.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("ai-helper.disabled")));
                return true;
            }

            boolean added = plugin.getAiMod().getRules().addTrainingMessage(plus, message);
            String key = added ? "ai-helper.added-message" : "ai-helper.already-added";
            String response = plugin.getMessageSnapshot().text(key);
            if (added) {
                response = response.replace("%list%", plus ? "trainingplus" : "trainingminus");
            }
            sender.sendMessage(TextUtil.format(response));
            return true;
        }

        sender.sendMessage(TextUtil.format(plugin.getMessageSnapshot().text("ai-helper.usage-aihelper")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("add");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return List.of("plus", "minus");
        }
        return List.of();
    }
}
