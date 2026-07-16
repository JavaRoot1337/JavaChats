package ru.javaroot.javachats.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.JavaChat;
import ru.javaroot.javachats.utils.TextUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MsgCmd implements CommandExecutor {
    private final JavaChat plugin;
    private final Map<UUID, UUID> lastMsg = new HashMap<>();

    public MsgCmd(JavaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var msgCfg = plugin.getMessageConfig();

        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.format(msgCfg.getString("messages.only-players", "&cТолько игроки могут писать личные сообщения.")));
            return true;
        }

        if (args.length < 2) {
            p.sendMessage(TextUtil
                    .format(msgCfg.getString("messages.usage-msg", "&7Использование: &c/msg <игрок> <сообщение>")));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            p.sendMessage(TextUtil.format(msgCfg.getString("messages.no-player", "&cИгрок не найден.")));
            return true;
        }

        if (target.equals(p)) {
            p.sendMessage(
                    TextUtil.format(msgCfg.getString("messages.cannot-msg-self", "&cНельзя писать самому себе.")));
            return true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            sb.append(args[i]).append(" ");
        }
        String msg = sb.toString().trim();

        String toFormat = msgCfg.getString("pm.sender", "&7 (&#FF8B1DВы&7) - &7(&#FF8B1D%player%&7) &f- &7%message%");
        String fromFormat = msgCfg.getString("pm.receiver", "&7 (&#FF8B1D%player%&7) - &7(&#FF8B1DВы&7) &f- &7%message%");

        Component toComp = TextUtil.format(toFormat.replace("%player%", target.getName()).replace("%message%", msg));
        Component fromComp = TextUtil.format(fromFormat.replace("%player%", p.getName()).replace("%message%", msg));

        p.sendMessage(toComp);
        target.sendMessage(fromComp);

        if (plugin.getChatLogger() != null) {
            plugin.getChatLogger().log("[PM] " + p.getName() + " -> " + target.getName() + ": " + msg);
        }

        lastMsg.put(target.getUniqueId(), p.getUniqueId());
        lastMsg.put(p.getUniqueId(), target.getUniqueId());

        return true;
    }
}
