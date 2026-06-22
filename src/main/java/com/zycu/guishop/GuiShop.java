package com.zycu.guishop;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GuiShop implements ModInitializer {
    public static ShopConfig CONFIG;
    public static EconomyStore ECONOMY;

    @Override
    public void onInitialize() {
        CONFIG = ShopConfig.load();
        ECONOMY = new EconomyStore(CONFIG);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("shop")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ShopGui.openCategories(player, ShopGui.Mode.BUY);
                    return 1;
                })
            );

            dispatcher.register(Commands.literal("balance")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    double balance = ECONOMY.balance(player.getUUID());
                    player.sendSystemMessage(Component.literal("Balance: " + CONFIG.money(balance)));
                    return 1;
                })
            );

            dispatcher.register(Commands.literal("pay")
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(context -> {
                            ServerPlayer sender = context.getSource().getPlayerOrException();
                            String targetName = StringArgumentType.getString(context, "player");
                            double amount = DoubleArgumentType.getDouble(context, "amount");
                            ServerPlayer target = sender.level().getServer().getPlayerList().getPlayerByName(targetName);

                            if (target == null) {
                                sender.sendSystemMessage(Component.literal("That player is not online."));
                                return 0;
                            }
                            if (target.getUUID().equals(sender.getUUID())) {
                                sender.sendSystemMessage(Component.literal("You cannot pay yourself."));
                                return 0;
                            }
                            if (!ECONOMY.transfer(sender.getUUID(), target.getUUID(), amount)) {
                                sender.sendSystemMessage(Component.literal("You cannot afford " + CONFIG.money(amount) + "."));
                                return 0;
                            }

                            sender.sendSystemMessage(Component.literal("Paid " + targetName + " " + CONFIG.money(amount) + "."));
                            target.sendSystemMessage(Component.literal(sender.getName().getString() + " paid you " + CONFIG.money(amount) + "."));
                            return 1;
                        })
                    )
                )
            );
        });

        System.out.println("[GUIShop] Fabric port initialized with " + CONFIG.categories.size() + " categories.");
    }
}
