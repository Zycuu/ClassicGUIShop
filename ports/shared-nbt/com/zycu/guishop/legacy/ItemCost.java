package com.zycu.guishop.legacy;
import net.minecraft.world.item.*;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import java.util.Objects;
/** Immutable original merchant payment, with native NBT matching. */
public final class ItemCost {
    private final ItemStack template;
    public ItemCost(Item item){this(item,1);}
    public ItemCost(Item item,int count){template=new ItemStack(item,count);}
    public ItemCost(Holder<Item> item,int count,CompoundTag tag){template=new ItemStack(item,count);if(!tag.isEmpty())template.setTag(tag.copy());}
    public ItemCost(ItemStack stack){template=stack.copy();}
    public Holder<Item> item(){return template.getItemHolder();}
    public int count(){return template.getCount();}
    public CompoundTag components(){return template.getTag()==null?new CompoundTag():template.getTag().copy();}
    public ItemStack itemStack(){return template.copy();}
    public boolean test(ItemStack actual){return ItemStack.isSameItem(template,actual)&&(!template.hasTag()||net.minecraft.nbt.NbtUtils.compareNbt(template.getTag(),actual.getTag(),false));}
}
