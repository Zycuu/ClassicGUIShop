package com.zycu.guishop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;

import java.util.ArrayList;
import java.util.List;

public final class CommandIntegrityCheck {
    private CommandIntegrityCheck() {}

    public static void verify(CommandDispatcher<CommandSourceStack> dispatcher) {
        List<String> missing = new ArrayList<>();
        CommandNode<CommandSourceStack> root = dispatcher.getRoot();

        requireCommand(root, "bal", missing);
        requireCommand(root, "shop", missing);
        requirePath(root, missing, "shop", "buy");
        requirePath(root, missing, "shop", "sell");
        requirePath(root, missing, "shop", "enchant");
        requirePath(root, missing, "shop", "balance");
        requirePath(root, missing, "shop", "pay");

        requireCommand(root, "sellhand", missing);
        requirePath(root, missing, "sellhand", "all");
        requirePath(root, missing, "sellhand", "amount");

        requireCommand(root, "worth", missing);
        requirePath(root, missing, "worth", "all");
        requirePath(root, missing, "worth", "item");

        requireCommand(root, "adminshop", missing);
        if (root.getChild("adminshop") != null && !root.getChild("adminshop").getChildren().isEmpty() || root.getChild("ident") != null) {
            throw new IllegalStateException("Legacy admin subcommands and /ident must not be registered");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("ClassicGUIShop command registration is incomplete: " + String.join(", ", missing));
        }

        System.out.println("[ClassicGUIShop] Command integrity verified: player commands and client Adminshop launcher registered; legacy admin subcommands and identification command remain absent.");
    }

    private static void requireCommand(CommandNode<CommandSourceStack> root, String name, List<String> missing) {
        CommandNode<CommandSourceStack> node = root.getChild(name);
        if (node == null) {
            missing.add("/" + name);
            return;
        }
        if (node.getCommand() == null) {
            missing.add("/" + name + " executable");
        }
    }

    private static void requirePath(CommandNode<CommandSourceStack> root, List<String> missing, String... path) {
        CommandNode<CommandSourceStack> current = root;
        StringBuilder display = new StringBuilder();
        for (String part : path) {
            display.append('/').append(part);
            current = current == null ? null : current.getChild(part);
            if (current == null) {
                missing.add(display.toString().replace('/', ' ').trim());
                return;
            }
        }
    }
}
