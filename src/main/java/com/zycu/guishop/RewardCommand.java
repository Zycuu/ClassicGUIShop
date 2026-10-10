package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.*;

/** External reward bridges may use the server console or RCON, never a player or command block. */
public final class RewardCommand {
    private RewardCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shopreward")
            .requires(RewardCommand::console)
            .then(Commands.argument("player", StringArgumentType.word()).suggests(ShopSuggestions.PLAYERS)
            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01, 1_000_000_000_000.0))
            .then(Commands.argument("source", StringArgumentType.string())
            .then(Commands.argument("reference", StringArgumentType.string()).executes(context -> execute(context, ""))
            .then(Commands.argument("note", StringArgumentType.greedyString()).executes(context -> execute(context, StringArgumentType.getString(context, "note")))))))));
    }
    public static boolean console(CommandSourceStack source) {
        return source.getEntity() == null && (((com.zycu.guishop.mixin.CommandSourceAccess)source).guishop$source() instanceof net.minecraft.server.MinecraftServer
            || ((com.zycu.guishop.mixin.CommandSourceAccess)source).guishop$source() instanceof net.minecraft.server.rcon.RconConsoleSource);
    }
    private static int execute(CommandContext<CommandSourceStack> context, String note) {
        try {
            var result = com.zycu.guishop.admin.AccountOperations.reward(context.getSource(), StringArgumentType.getString(context, "player"),
                DoubleArgumentType.getDouble(context, "amount"), StringArgumentType.getString(context, "source"), StringArgumentType.getString(context, "reference"), note);
            context.getSource().sendSuccess(() -> Component.literal(result.credited() ? "Reward credited to " + result.name() + ". Balance: " + GuiShop.CONFIG.money(result.balance()) : "Reward reference already processed. No additional money was credited."), false);
            return 1;
        } catch (RuntimeException error) { context.getSource().sendFailure(Component.literal(error.getMessage())); return 0; }
    }
}
