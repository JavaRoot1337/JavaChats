package ru.javaroot.javachats.cmd;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import ru.javaroot.javachats.ai.AiSvc;
import ru.javaroot.javachats.cfg.Msg;
import ru.javaroot.javachats.plat.Exec;
import ru.javaroot.javachats.plat.Hex;

import java.util.function.Supplier;

public final class AiCmd {
    private AiCmd() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(AiSvc ai, Exec exec, Supplier<Msg> msgSupplier) {
        return Commands.literal("aihelper")
                .requires(src -> src.getSender().hasPermission("javachats.admin"))
                .executes(ctx -> sendUsage(ctx.getSource().getSender(), msgSupplier))
                .then(Commands.literal("add")
                        .executes(ctx -> sendUsage(ctx.getSource().getSender(), msgSupplier))
                        .then(Commands.literal("plus")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> addSample(ctx.getSource().getSender(), true,
                                                StringArgumentType.getString(ctx, "message"), ai, exec, msgSupplier))))
                        .then(Commands.literal("minus")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> addSample(ctx.getSource().getSender(), false,
                                                StringArgumentType.getString(ctx, "message"), ai, exec, msgSupplier)))))
                .build();
    }

    private static int sendUsage(CommandSender snd, Supplier<Msg> msgSupplier) {
        snd.sendMessage(Hex.parse(msgSupplier.get().text("ai-helper.usage-aihelper")));
        return Command.SINGLE_SUCCESS;
    }

    private static int addSample(CommandSender snd, boolean isPlus, String text,
            AiSvc ai, Exec exec, Supplier<Msg> msgSupplier) {
        if (ai == null) {
            snd.sendMessage(Hex.parse(msgSupplier.get().text("ai-helper.disabled")));
            return Command.SINGLE_SUCCESS;
        }
        ai.getRules().addTrainingAsync(isPlus, text).thenAccept(added ->
                exec.runSender(snd, () -> {
                    String key = added ? "ai-helper.added-message" : "ai-helper.already-added";
                    String raw = msgSupplier.get().text(key);
                    if (added) {
                        raw = raw.replace("%list%", isPlus ? "trainingplus" : "trainingminus");
                    }
                    snd.sendMessage(Hex.parse(raw));
                }));
        return Command.SINGLE_SUCCESS;
    }
}
