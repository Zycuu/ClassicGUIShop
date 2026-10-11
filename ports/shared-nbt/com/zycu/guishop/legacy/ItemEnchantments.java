package com.zycu.guishop.legacy;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Set;
public final class ItemEnchantments {
    public static final ItemEnchantments EMPTY=new ItemEnchantments(new ListTag());
    private final ListTag tags;
    private final Object2IntOpenHashMap<Holder<Enchantment>> values=new Object2IntOpenHashMap<>();
    public ItemEnchantments(ListTag tags){this.tags=tags.copy();for(int i=0;i<tags.size();i++){var entry=tags.getCompound(i);var id=net.minecraft.resources.ResourceLocation.tryParse(entry.getString("id"));if(id!=null)BuiltInRegistries.ENCHANTMENT.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,id)).ifPresent(h->values.put(h,(int)entry.getShort("lvl")));}}
    public boolean isEmpty(){return values.isEmpty();}
    public int size(){return values.size();}
    public Set<it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>>> entrySet(){return values.object2IntEntrySet();}
    public Set<Holder<Enchantment>> keySet(){return values.keySet();}
    public int getLevel(Enchantment enchantment){return values.entrySet().stream().filter(e->e.getKey().value()==enchantment).mapToInt(e->e.getValue()).findFirst().orElse(0);}
    public ListTag tags(){return tags.copy();}
}
