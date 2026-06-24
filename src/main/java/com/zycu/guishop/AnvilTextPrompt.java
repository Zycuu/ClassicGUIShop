package com.zycu.guishop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

public final class AnvilTextPrompt {
    private AnvilTextPrompt() {}

    public static void open(ServerPlayer player, String title, String initialText, Consumer<String> callback) {
        player.openMenu(new SimpleMenuProvider(
            (containerId, inventory, ignored) -> new PromptMenu(containerId, inventory, player, initialText, callback),
            Component.literal(title)
        ));
    }

    private static final class PromptMenu extends AnvilMenu {
        private final ServerPlayer owner;
        private final Consumer<String> callback;
        private String currentText;
        private boolean submitted;

        PromptMenu(
            int containerId,
            Inventory inventory,
            ServerPlayer owner,
            String initialText,
            Consumer<String> callback
        ) {
            super(containerId, inventory, ContainerLevelAccess.NULL);
            this.owner = owner;
            this.callback = callback;
            this.currentText = initialText == null ? "" : initialText;

            ItemStack input = new ItemStack(Items.PAPER);
            input.set(DataComponents.CUSTOM_NAME, Component.literal(this.currentText));
            this.inputSlots.setItem(0, input);
            createResult();
        }

        @Override
        public boolean setItemName(String name) {
            this.currentText = name == null ? "" : name.trim();
            createResult();
            return true;
        }

        @Override
        public void createResult() {
            String text = currentText;
            if (text == null && !this.inputSlots.getItem(0).isEmpty()) {
                text = this.inputSlots.getItem(0).getHoverName().getString();
            }
            ItemStack result = new ItemStack(Items.LIME_DYE);
            result.set(DataComponents.CUSTOM_NAME, Component.literal(
                text == null || text.isBlank() ? "Enter a value, then click here" : "Confirm: " + text
            ));
            this.resultSlots.setItem(0, result);
            this.broadcastChanges();
        }

        @Override
        protected boolean mayPickup(Player player, boolean hasItem) {
            return hasItem;
        }

        @Override
        protected void onTake(Player player, ItemStack carried) {
            submit();
        }

        @Override
        public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
            if (slotIndex == 2 && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
                submit();
                return;
            }
            if (slotIndex >= 0 && slotIndex < 3) return;
            super.clicked(slotIndex, buttonNum, input, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index == 2) submit();
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return player == owner && owner.isAlive();
        }

        @Override
        public void removed(Player player) {
            this.inputSlots.setItem(0, ItemStack.EMPTY);
            this.inputSlots.setItem(1, ItemStack.EMPTY);
            this.resultSlots.setItem(0, ItemStack.EMPTY);
        }

        private void submit() {
            if (submitted) return;
            submitted = true;
            String value = currentText == null ? "" : currentText.trim();
            owner.closeContainer();
            callback.accept(value);
        }
    }
}
