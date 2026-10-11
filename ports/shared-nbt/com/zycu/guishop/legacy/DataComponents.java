package com.zycu.guishop.legacy;

import net.minecraft.world.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Native NBT bridge. Templates stay intact; display changes only affect disposable copies. */
public final class DataComponents {
    public record Key<T>(String name) {}
    public static final Key<Integer> DAMAGE=new Key<>("Damage"),MAP_ID=new Key<>("map");
    public static final Key<ItemEnchantments> ENCHANTMENTS=new Key<>("Enchantments"),STORED_ENCHANTMENTS=new Key<>("StoredEnchantments");
    public static final Key<ItemLore> LORE=new Key<>("Lore");
    public static final Key<Component> CUSTOM_NAME=new Key<>("Name");
    public static final Key<PotionContents> POTION_CONTENTS=new Key<>("Potion");
    public static final Key<MapDecorations> MAP_DECORATIONS=new Key<>("Decorations");
    public static final Key<Object> CUSTOM_DATA=new Key<>("custom"),CUSTOM_MODEL_DATA=new Key<>("CustomModelData");
    private static final Set<String> VANILLA=Set.of("Damage","RepairCost","Enchantments","StoredEnchantments","Potion","CustomPotionColor","CustomPotionEffects","HideFlags","Unbreakable","Trim","display","map","Decorations","AttributeModifiers","Charged","ChargedProjectiles","BlockStateTag","BlockEntityTag","SkullOwner","EntityTag","Fireworks","Explosion","pages","author","title","generation","resolved","LodestonePos","LodestoneDimension","LodestoneTracked","instrument");
    @SuppressWarnings("unchecked") public static <T>T get(ItemStack stack,Key<T> key){
        var tag=stack.getTag();if(key==POTION_CONTENTS)return stack.is(Items.POTION)||stack.is(Items.SPLASH_POTION)||stack.is(Items.LINGERING_POTION)||stack.is(Items.TIPPED_ARROW)?(T)new PotionContents(stack):null;
        if(tag==null)return null;
        if(key==CUSTOM_DATA)return tag.getAllKeys().stream().anyMatch(k->!VANILLA.contains(k))?(T)tag:null;
        if(key==CUSTOM_NAME)return stack.hasCustomHoverName()?(T)stack.getHoverName():null;
        if(key==LORE){var display=stack.getTagElement("display");if(display==null)return null;var list=display.getList("Lore",8);var lines=new ArrayList<Component>();for(int i=0;i<list.size();i++){var line=Component.Serializer.fromJson(list.getString(i));if(line!=null)lines.add(line);}return (T)new ItemLore(lines);}
        if(!tag.contains(key.name()))return null;
        if(key==ENCHANTMENTS||key==STORED_ENCHANTMENTS)return (T)new ItemEnchantments(tag.getList(key.name(),10));
        if(key==MAP_DECORATIONS)return (T)new MapDecorations(tag.getList(key.name(),10).copy());
        if(key==DAMAGE||key==MAP_ID)return (T)Integer.valueOf(tag.getInt(key.name()));
        return (T)tag.get(key.name());
    }
    public static <T>T getOrDefault(ItemStack stack,Key<T> key,T fallback){T value=get(stack,key);return value==null?fallback:value;}
    public static boolean has(ItemStack stack,Key<?> key){return get(stack,key)!=null;}
    public static <T>void set(ItemStack stack,Key<T> key,T value){
        if(key==CUSTOM_NAME){stack.setHoverName((Component)value);return;}
        if(key==LORE){var list=new ListTag();for(var line:((ItemLore)value).lines())list.add(StringTag.valueOf(Component.Serializer.toJson(line)));if(list.isEmpty()){var display=stack.getTagElement("display");if(display!=null){display.remove("Lore");if(display.isEmpty())stack.removeTagKey("display");}}else stack.getOrCreateTagElement("display").put("Lore",list);return;}
        if(key==ENCHANTMENTS||key==STORED_ENCHANTMENTS){var tags=((ItemEnchantments)value).tags();if(tags.isEmpty())remove(stack,key);else stack.getOrCreateTag().put(key.name(),tags);return;}
        if(key==MAP_DECORATIONS){var tags=((MapDecorations)value).value();if(tags.isEmpty())remove(stack,key);else stack.getOrCreateTag().put(key.name(),tags.copy());return;}
        if(key==DAMAGE){stack.setDamageValue((Integer)value);return;}
        if(key==MAP_ID){stack.getOrCreateTag().putInt(key.name(),(Integer)value);return;}
        throw new IllegalArgumentException("Unsupported legacy item field "+key.name());
    }
    public static void remove(ItemStack stack,Key<?> key){stack.removeTagKey(key.name());if(stack.getTag()!=null&&stack.getTag().isEmpty())stack.setTag(null);}
    public static int hash(ItemStack stack){return Objects.hash(stack.getItem(),stack.getTag());}
}
