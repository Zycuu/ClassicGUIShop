package com.zycu.guishop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.LinkedHashMap;

/** Old versions represent every explorer map as a filled map with native world NBT. */
public final class ShopMaps {
    private ShopMaps(){}
    private record Key(net.minecraft.server.level.ServerLevel world,String identity,int regionX,int regionZ){}
    private static final LinkedHashMap<Key,ItemStack> CACHE=new LinkedHashMap<>();
    public static void clear(){CACHE.clear();}
    public static boolean nativeMap(ItemStack stack){return stack.is(Items.FILLED_MAP);}
    public static boolean dynamic(ItemStack stack){return nativeMap(stack)&&(stack.getTag()==null||!stack.getTag().contains("map"));}
    public static ItemStack prepare(ServerPlayer player,ItemStack template){
        if(!nativeMap(template))return template.copyWithCount(1);
        if(!dynamic(template)){if(MapItem.getSavedData(template,player.serverLevel())==null)throw new IllegalArgumentException("This map's saved data is missing from this world. Import a working map with Add held item.");return template.copyWithCount(1);}
        var key=new Key(player.serverLevel(),ItemStackData.listingId(template,player.serverLevel().registryAccess()),player.blockPosition().getX()>>8,player.blockPosition().getZ()>>8);
        var cached=CACHE.get(key);if(cached!=null&&MapItem.getSavedData(cached,player.serverLevel())!=null)return cached.copyWithCount(1);
        var generated=MapItem.create(player.serverLevel(),player.blockPosition().getX(),player.blockPosition().getZ(),(byte)0,true,false);
        var result=template.copyWithCount(1);result.getOrCreateTag().putInt("map",generated.getOrCreateTag().getInt("map"));
        if(CACHE.size()>=256)CACHE.remove(CACHE.keySet().iterator().next());CACHE.put(key,result.copy());return result;
    }
    public static boolean matches(ItemStack template,ItemStack actual){
        if(!dynamic(template))return ItemStackData.same(template,actual);
        if(actual.isEmpty()||actual.getItem()!=template.getItem())return false;
        var normalized=actual.copyWithCount(1);normalized.removeTagKey("map");normalized.removeTagKey("Decorations");
        if(template.getTag()!=null&&template.getTag().contains("Decorations"))normalized.getOrCreateTag().put("Decorations",template.getTag().get("Decorations").copy());
        if(normalized.getTag()!=null&&normalized.getTag().isEmpty())normalized.setTag(null);return ItemStackData.same(template,normalized);
    }
    public static ItemStack economicIdentity(ItemStack stack){if(!nativeMap(stack))return stack;var copy=stack.copyWithCount(1);copy.removeTagKey("map");copy.removeTagKey("Decorations");if(copy.getTag()!=null&&copy.getTag().isEmpty())copy.setTag(null);return copy;}
    public static double cheapestEmptyMap(ShopConfig config,net.minecraft.core.RegistryAccess access){double minimum=Double.POSITIVE_INFINITY;for(var category:config.categories)for(var item:category.items)if(item.buy>0&&item.item.equals("minecraft:map")&&VersionedListingStore.isRuntimeAvailable(item,access))minimum=Math.min(minimum,item.buy);return minimum;}
}
