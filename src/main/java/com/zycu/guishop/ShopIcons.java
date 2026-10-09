package com.zycu.guishop;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.LinkedHashSet;
import java.util.List;

/** Decorative menu buttons have shop tooltips, never the source item's gameplay tooltip. */
public final class ShopIcons {
    private ShopIcons() {}

    public static ItemStack category(ShopConfig.Category category, RegistryAccess access, String name) {
        return display(ItemStackData.decode(category.iconStack, category.icon, access), name, category.description);
    }

    public static ItemStack display(ItemStack template, String name, String description) {
        if (template == null || template.isEmpty()) template = new ItemStack(Items.BARRIER);
        // A neutral item uses the selected model and rendering data without invoking its item's help-text hook.
        var icon = new ItemStack(Items.PAPER);
        icon.applyComponents(template.getComponents());
        icon.set(DataComponents.ITEM_MODEL, template.getOrDefault(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("paper")));
        icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, template.hasFoil());
        icon.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
        icon.set(DataComponents.LORE, new ItemLore(description == null || description.isBlank() ? List.of()
            : List.of(Component.literal(description).withStyle(net.minecraft.ChatFormatting.GRAY))));
        var hidden = new LinkedHashSet<>(icon.getComponents().keySet());
        hidden.remove(DataComponents.LORE); hidden.remove(DataComponents.CUSTOM_NAME);
        icon.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(false, hidden));
        icon.remove(DataComponents.TOOLTIP_STYLE);
        return icon;
    }
}
