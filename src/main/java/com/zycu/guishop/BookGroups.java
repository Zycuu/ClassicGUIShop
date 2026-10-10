package com.zycu.guishop;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.*;
/** Virtual enchantment folders keep exact ordinary listings and their prices intact. */
public final class BookGroups {
    private BookGroups(){}
    public static String key(ShopConfig.ShopItem item,RegistryAccess access){
        if(!item.item.equals("minecraft:enchanted_book")||!VersionedListingStore.isRuntimeAvailable(item,access))return null;
        var ench=item.createStack(access).getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY);
        return ench.size()==1?ench.keySet().iterator().next().unwrapKey().orElseThrow().identifier().toString():ench.size()>1?"combined":"unassigned";
    }
    public static String name(String key,RegistryAccess access){
        if(key.equals("combined"))return "Combined Enchantments";if(key.equals("unassigned"))return "Unassigned Books";
        return access.lookupOrThrow(Registries.ENCHANTMENT).get(net.minecraft.resources.Identifier.parse(key)).map(holder->holder.value().description().getString()).orElse(key);
    }
    public static Map<String,List<ShopConfig.ShopItem>> groups(List<ShopConfig.ShopItem> items,RegistryAccess access){
        var result=new LinkedHashMap<String,List<ShopConfig.ShopItem>>();for(var item:items){String key=key(item,access);if(key!=null)result.computeIfAbsent(key,ignored->new ArrayList<>()).add(item);}return result;
    }
}
