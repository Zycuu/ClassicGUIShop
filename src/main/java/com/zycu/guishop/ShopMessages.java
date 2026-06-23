package com.zycu.guishop;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class ShopMessages {
    private ShopMessages() {}

    public static Component info(String text) {
        return message(text, color(GuiShop.CONFIG == null ? null : GuiShop.CONFIG.chatInfoColor, ChatFormatting.AQUA));
    }

    public static Component success(String text) {
        return message(text, color(GuiShop.CONFIG == null ? null : GuiShop.CONFIG.chatSuccessColor, ChatFormatting.GREEN));
    }

    public static Component warning(String text) {
        return message(text, color(GuiShop.CONFIG == null ? null : GuiShop.CONFIG.chatWarningColor, ChatFormatting.YELLOW));
    }

    public static Component error(String text) {
        return message(text, color(GuiShop.CONFIG == null ? null : GuiShop.CONFIG.chatErrorColor, ChatFormatting.RED));
    }

    public static Component admin(String text) {
        return message(text, color(GuiShop.CONFIG == null ? null : GuiShop.CONFIG.chatAdminColor, ChatFormatting.LIGHT_PURPLE));
    }

    public static void info(ServerPlayer player, String text) {
        player.sendSystemMessage(info(text));
    }

    public static void success(ServerPlayer player, String text) {
        player.sendSystemMessage(success(text));
    }

    public static void warning(ServerPlayer player, String text) {
        player.sendSystemMessage(warning(text));
    }

    public static void error(ServerPlayer player, String text) {
        player.sendSystemMessage(error(text));
    }

    public static void info(CommandSourceStack source, String text, boolean broadcast) {
        source.sendSuccess(() -> info(text), broadcast);
    }

    public static void success(CommandSourceStack source, String text, boolean broadcast) {
        source.sendSuccess(() -> success(text), broadcast);
    }

    public static void warning(CommandSourceStack source, String text) {
        source.sendSuccess(() -> warning(text), false);
    }

    public static void admin(CommandSourceStack source, String text, boolean broadcast) {
        source.sendSuccess(() -> admin(text), broadcast);
    }

    public static void error(CommandSourceStack source, String text) {
        source.sendFailure(error(text));
    }

    private static Component message(String text, ChatFormatting bodyColor) {
        String configuredPrefix = GuiShop.CONFIG == null || GuiShop.CONFIG.chatPrefix == null
            ? "[ShopGUI]"
            : GuiShop.CONFIG.chatPrefix;
        ChatFormatting prefixColor = color(
            GuiShop.CONFIG == null ? null : GuiShop.CONFIG.chatPrefixColor,
            ChatFormatting.GOLD
        );

        MutableComponent prefix = Component.literal(configuredPrefix + " ")
            .withStyle(prefixColor, ChatFormatting.BOLD);
        return prefix.append(Component.literal(text).withStyle(bodyColor));
    }

    private static ChatFormatting color(String configured, ChatFormatting fallback) {
        if (configured == null || configured.isBlank()) return fallback;
        String normalized = configured.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            ChatFormatting formatting = ChatFormatting.valueOf(normalized);
            return formatting.isColor() ? formatting : fallback;
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
