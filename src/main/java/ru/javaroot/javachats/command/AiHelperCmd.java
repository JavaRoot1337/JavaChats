package ru.javaroot.javachats.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ru.javaroot.javachats.JavaChat;
import ru.javaroot.javachats.utils.TextUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AiHelperCmd implements CommandExecutor, TabCompleter {
    private final JavaChat plugin;

    public AiHelperCmd(JavaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("javachats.admin")) {
            sender.sendMessage(
                    TextUtil.format(plugin.getMessageConfig().getString("messages.no-permission", "&cНет прав.")));
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("add")) {
            boolean plus = args[1].equalsIgnoreCase("plus");
            boolean minus = args[1].equalsIgnoreCase("minus");

            if (!plus && !minus) {
                sender.sendMessage(TextUtil.format(plugin.getMessageConfig().getString("ai-helper.usage-aihelper", "&7Использование: &a/aihelper add <plus|minus> <сообщение>")));
                return true;
            }

            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            if (plugin.getAiMod() != null) {
                boolean added = plugin.getAiMod().getRules().addTrainingMessage(plus, message);
                if (added) {
                    String successMsg = plugin.getMessageConfig().getString("ai-helper.added-message", "&aСообщение добавлено в список %list%!")
                            .replace("%list%", plus ? "trainingplus" : "trainingminus");
                    sender.sendMessage(TextUtil.format(successMsg));
                } else {
                    sender.sendMessage(TextUtil.format(plugin.getMessageConfig().getString("ai-helper.already-added", "&cЭто сообщение уже добавлено в этот список!")));
                }
            } else {
                sender.sendMessage(TextUtil.format(plugin.getMessageConfig().getString("ai-helper.disabled", "&cAIHelper отключен или не инициализирован.")));
            }
            return true;
        }

        sender.sendMessage(TextUtil.format(plugin.getMessageConfig().getString("ai-helper.usage-aihelper", "&7Использование: &a/aihelper add <plus|minus> <сообщение>")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("add");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return List.of("plus", "minus");
        }
        return new ArrayList<>();
    }
}
