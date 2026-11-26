package ru.javaroot.javachats.cmd;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.api.PmReq;
import ru.javaroot.javachats.api.PmSvc;
import ru.javaroot.javachats.cfg.Msg;
import ru.javaroot.javachats.plat.Hex;

import java.util.Locale;
import java.util.function.Supplier;

public final class MsgCmd {
    private MsgCmd() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(PmSvc pm, Supplier<Msg> msgSupplier) {
        return Commands.literal("msg")
                .requires(src -> src.getSender().hasPermission("javachats.msg"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(Hex.parse(msgSupplier.get().text("messages.usage-msg")));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                            for (Player online : Bukkit.getOnlinePlayers()) {
                                String name = online.getName();
                                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                                    builder.suggest(name);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ctx.getSource().getSender().sendMessage(Hex.parse(msgSupplier.get().text("messages.usage-msg")));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getSender() instanceof Player p)) {
                                        ctx.getSource().getSender().sendMessage(
                                                Hex.parse(msgSupplier.get().text("messages.only-players")));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String targetName = StringArgumentType.getString(ctx, "target");
                                    Player target = Bukkit.getPlayerExact(targetName);
                                    if (target == null || !target.isOnline()) {
                                        p.sendMessage(Hex.parse(msgSupplier.get().text("messages.no-player")));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    if (target.equals(p)) {
                                        p.sendMessage(Hex.parse(msgSupplier.get().text("messages.cannot-msg-self")));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    String body = StringArgumentType.getString(ctx, "message").trim();
                                    if (body.isEmpty()) {
                                        p.sendMessage(Hex.parse(msgSupplier.get().text("messages.usage-msg")));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    try {
                                        pm.send(new PmReq(p.getUniqueId(), p.getName(),
                                                target.getUniqueId(), target.getName(), body));
                                    } catch (IllegalArgumentException ex) {
                                        p.sendMessage(Hex.parse(msgSupplier.get().text("messages.usage-msg")));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
