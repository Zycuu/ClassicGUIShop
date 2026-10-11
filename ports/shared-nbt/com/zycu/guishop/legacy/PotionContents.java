package com.zycu.guishop.legacy;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;
public final class PotionContents {
    private final ItemStack stack;
    public PotionContents(ItemStack stack){this.stack=stack;}
    public Optional<Holder<Potion>> potion(){var id=BuiltInRegistries.POTION.getKey(PotionUtils.getPotion(stack));return id==null?Optional.empty():BuiltInRegistries.POTION.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.POTION,id)).map(h->h);}
    public List<net.minecraft.world.effect.MobEffectInstance> customEffects(){return PotionUtils.getCustomEffects(stack);}
    public void addPotionTooltip(java.util.function.Consumer<net.minecraft.network.chat.Component> consumer,float scale,float ticks){var lines=new ArrayList<net.minecraft.network.chat.Component>();PotionUtils.addPotionTooltip(stack,lines,scale,ticks);lines.forEach(consumer);}
    public static ItemStack createItemStack(Item item,Holder<Potion> potion){return PotionUtils.setPotion(new ItemStack(item),potion.value());}
}
