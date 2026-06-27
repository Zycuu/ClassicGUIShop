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

        requireCommand(root, "shop", missing);
        requireChild(root, "shop", "buy", missing);
        requireChild(root, "shop", "sell", missing);
        requireChild(root, "shop", "enchant", missing);
        requireChild(root, "shop", "balance", missing);
        requireChild(root, "shop", "pay", missing);

        requireCommand(root, "sellhand", missing);
        requireChild(root, "sellhand", "all", missing);
        requireCommand(root, "worth", missing);
        requireChild(root, "worth", "all", missing);
        requireCommand(root, "ident", missing);

        requireCommand(root, "adminshop", missing);
        requireChild(root, "adminshop", "edit", missing);
        requireChild(root, "adminshop", "help", missing);
        requireChild(root, "adminshop", "reload", missing);
        requireChild(root, "adminshop", "item", missing);
        requireChild(root, "adminshop", "category", missing);
        requireChild(root, "adminshop", "enchant", missing);
        requireChild(root, "adminshop", "economy", missing);
        requireChild(root, "adminshop", "multiplier", missing);
        requireChild(root, "adminshop", "import", missing);

        if (!missing.isEmpty()) {
            throw new IllegalStateException("ClassicGUIShop command registration is incomplete: " + String.join(", ", missing));
        }

        System.out.println("[ClassicGUIShop] Command integrity verified: player, admin, import, economy, and editor commands are registered.");
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

    private static void requireChild(
        CommandNode<CommandSourceStack> root,
        String parentName,
        String childName,
        List<String> missing
    ) {
        CommandNode<CommandSourceStack> parent = root.getChild(parentName);
        if (parent == null) {
            missing.add("/" + parentName + " " + childName);
            return;
        }
        if (parent.getChild(childName) == null) {
            missing.add("/" + parentName + " " + childName);
        }
    }
}
