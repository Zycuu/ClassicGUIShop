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

        requireCommand(root, "ident", missing);

        requireCommand(root, "adminshop", missing);
        requirePath(root, missing, "adminshop", "edit");
        requirePath(root, missing, "adminshop", "help");
        requirePath(root, missing, "adminshop", "reload");
        requirePath(root, missing, "adminshop", "multiplier");

        requirePath(root, missing, "adminshop", "item");
        requirePath(root, missing, "adminshop", "item", "add");
        requirePath(root, missing, "adminshop", "item", "remove");
        requirePath(root, missing, "adminshop", "item", "price");
        requirePath(root, missing, "adminshop", "item", "move");
        requirePath(root, missing, "adminshop", "item", "list");

        requirePath(root, missing, "adminshop", "category");
        requirePath(root, missing, "adminshop", "category", "add");
        requirePath(root, missing, "adminshop", "category", "remove");
        requirePath(root, missing, "adminshop", "category", "list");

        requirePath(root, missing, "adminshop", "enchant");
        requirePath(root, missing, "adminshop", "enchant", "set");
        requirePath(root, missing, "adminshop", "enchant", "remove");
        requirePath(root, missing, "adminshop", "enchant", "list");
        requirePath(root, missing, "adminshop", "enchant", "defaultprice");
        requirePath(root, missing, "adminshop", "enchant", "enabled");

        requirePath(root, missing, "adminshop", "economy");
        requirePath(root, missing, "adminshop", "economy", "get");
        requirePath(root, missing, "adminshop", "economy", "set");
        requirePath(root, missing, "adminshop", "economy", "add");
        requirePath(root, missing, "adminshop", "economy", "take");

        requirePath(root, missing, "adminshop", "import");
        requirePath(root, missing, "adminshop", "import", "scan");
        requirePath(root, missing, "adminshop", "import", "mod");
        requirePath(root, missing, "adminshop", "import", "namespace");
        requirePath(root, missing, "adminshop", "import", "datapack");
        requirePath(root, missing, "adminshop", "import", "held");
        requirePath(root, missing, "adminshop", "import", "price");

        if (!missing.isEmpty()) {
            throw new IllegalStateException("ClassicGUIShop command registration is incomplete: " + String.join(", ", missing));
        }

        System.out.println("[ClassicGUIShop] Command integrity verified: player, admin, import, economy, enchanted-book, and editor commands are registered.");
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
