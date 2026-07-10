package ru.javaroot.javachats.listener;

import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import org.bukkit.Bukkit;
import ru.javaroot.javachats.ChatPlugin;
import ru.javaroot.javachats.utils.TextUtil;
import java.util.List;

public class DiscordHandler {
    private final ChatPlugin plugin;

    public DiscordHandler(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onMessage(DiscordGuildMessageReceivedEvent e) {
        var cfg = plugin.getConfig();
        boolean debug = cfg.getBoolean("discord.debug", false);

        if (debug) {
            plugin.getLogger().info("[DiscordDebug] New message in channel: " + e.getChannel().getId());
        }

        if (e.getAuthor().isBot()) {
            if (debug) plugin.getLogger().info("[DiscordDebug] Ignored bot message.");
            return;
        }

        String techRole = cfg.getString("discord.tech-role", "");
        boolean isTech = e.getMessage().getMentionedRoles().stream()
                .anyMatch(r -> r.getId().equals(techRole));

        if (debug) plugin.getLogger().info("[DiscordDebug] isTech: " + isTech);

        var msgCfg = plugin.getMessageConfig();
        List<String> list = msgCfg.getStringList(isTech ? "discord.tech" : "discord.new");

        for (String raw : list) {
            Bukkit.broadcast(TextUtil.format(raw));
        }
    }
}
