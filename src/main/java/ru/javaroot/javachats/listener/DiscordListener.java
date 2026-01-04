package ru.javaroot.javachats.listener;

import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import org.bukkit.Bukkit;
import ru.javaroot.javachats.JavaChats;
import ru.javaroot.javachats.utils.ColorUtils;

import java.util.List;

public class DiscordListener {

    private final JavaChats plugin;

    public DiscordListener(JavaChats plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onMessage(DiscordGuildMessageReceivedEvent event) {
        boolean debug = plugin.getDiscordConfig().isDebug();
        if (debug) {
            plugin.getLogger().info("[DiscordDebug] New message in channel: " + event.getChannel().getId());
        }

        if (event.getAuthor().isBot()) {
            if (debug)
                plugin.getLogger().info("[DiscordDebug] Ignored bot message.");
            return;
        }

        String techRoleId = plugin.getDiscordConfig().getTechRoleId();
        boolean isTech = false;

        List<Role> roles = event.getMessage().getMentionedRoles();
        if (debug) {
            plugin.getLogger().info("[DiscordDebug] Mentioned roles: " + roles.size());
            for (Role role : roles) {
                plugin.getLogger().info("[DiscordDebug] - " + role.getId() + " (" + role.getName() + ")");
            }
        }

        for (Role role : roles) {
            if (role.getId().equals(techRoleId)) {
                isTech = true;
                break;
            }
        }

        if (debug)
            plugin.getLogger().info("[DiscordDebug] isTech: " + isTech);

        List<String> messages;
        if (isTech) {
            messages = plugin.getConfig().getStringList("messages.discord-tech");
        } else {
            messages = plugin.getConfig().getStringList("messages.discord-new");
        }

        if (messages.isEmpty() && debug) {
            plugin.getLogger().info("[DiscordDebug] Warning: messages list is empty.");
        }

        for (String msg : messages) {
            Bukkit.broadcastMessage(ColorUtils.colorize(msg));
        }
    }
}
