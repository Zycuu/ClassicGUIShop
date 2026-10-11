package com.zycu.guishop;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.*;
import java.util.*;
import java.util.stream.Stream;

/** Registry naming bridge, including statically registered mod enchantments on 1.20. */
public final class LegacyRegistry<T> {
    private final Registry<T> registry;
    private LegacyRegistry(Registry<T> registry){this.registry=registry;}
    @SuppressWarnings("unchecked")
    public static <T> LegacyRegistry<T> of(RegistryAccess access,ResourceKey<? extends Registry<T>> key){
        Registry<T> value=access.registry(key).orElseGet(()->(Registry<T>)BuiltInRegistries.REGISTRY.get(key.location()));
        if(value==null)throw new IllegalArgumentException("Missing registry "+key.location());
        return new LegacyRegistry<>(value);
    }
    public boolean containsKey(ResourceLocation id){return registry.containsKey(id);}
    public Optional<Holder.Reference<T>> get(ResourceLocation id){return registry.getHolder(ResourceKey.create(registry.key(),id));}
    public Optional<Holder.Reference<T>> get(ResourceKey<T> key){return registry.getHolder(key);}
    public Holder.Reference<T> getOrThrow(ResourceKey<T> key){return registry.getHolderOrThrow(key);}
    public Stream<Holder.Reference<T>> listElements(){return registry.holders();}
    public Set<Map.Entry<ResourceKey<T>,T>> entrySet(){return registry.entrySet();}
}
