package ru.javaroot.javachats.cmd;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import ru.javaroot.javachats.cfg.Msg;
import ru.javaroot.javachats.plat.Hex;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class JavaChatsCmd {
    private JavaChatsCmd() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(BooleanSupplier reloader, Supplier<Msg> msgSupplier) {
        return Commands.literal("javachats")
                .requires(src -> src.getSender().hasPermission("javachats.admin"))
                .executes(ctx -> {
                    CommandSender snd = ctx.getSource().getSender();
                    snd.sendMessage(Hex.parse(msgSupplier.get().text("messages.usage-javachats")));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            CommandSender snd = ctx.getSource().getSender();
                            String key = reloader.getAsBoolean() ? "messages.reload" : "messages.reload-failed";
                            snd.sendMessage(Hex.parse(msgSupplier.get().text(key)));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
