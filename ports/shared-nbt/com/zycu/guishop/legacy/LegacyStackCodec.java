package com.zycu.guishop.legacy;
import com.google.gson.JsonElement;
import com.mojang.serialization.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.*;
/** SNBT preserves numeric tag types. Incompatible modern component stacks are parked, never stripped. */
public final class LegacyStackCodec {
    private static DataResult<ItemStack> available(ItemStack stack){
        var tag=stack.getTag();
        if(tag!=null){
            if(tag.contains("Potion",8)&&!net.minecraft.core.registries.BuiltInRegistries.POTION.containsKey(new net.minecraft.resources.ResourceLocation(tag.getString("Potion"))))return DataResult.error(()->"Missing potion registry entry; exact item preserved");
            for(String key:java.util.List.of("Enchantments","StoredEnchantments")){
                var list=tag.getList(key,10);
                for(int i=0;i<list.size();i++)if(!net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.containsKey(new net.minecraft.resources.ResourceLocation(list.getCompound(i).getString("id"))))return DataResult.error(()->"Missing enchantment registry entry; exact item preserved");
            }
        }
        return DataResult.success(stack);
    }
    public static final Codec<ItemStack> CODEC=Codec.PASSTHROUGH.comapFlatMap(dynamic->{
        JsonElement data=dynamic.convert(JsonOps.INSTANCE).getValue();
        try {
            if(data.isJsonPrimitive()&&data.getAsJsonPrimitive().isString())return available(ItemStack.of(TagParser.parseTag(data.getAsString())));
            if(!data.isJsonObject()||data.getAsJsonObject().has("components")||data.getAsJsonObject().has("count"))return DataResult.error(()->"Item belongs to a different Minecraft data format; preserved in versioned listings");
            return ItemStack.CODEC.parse(JsonOps.INSTANCE,data).flatMap(LegacyStackCodec::available);
        }catch(Exception error){return DataResult.error(()->"Invalid exact item NBT: "+error.getMessage());}
    },stack->new Dynamic<>(JsonOps.INSTANCE,new com.google.gson.JsonPrimitive(stack.save(new CompoundTag()).toString())));
}
