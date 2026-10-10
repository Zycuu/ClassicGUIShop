package com.zycu.guishop.admin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
/** Namespace attribution plus the supplying server resource pack when available. */
public final class VariantSources {
    private VariantSources() {}
    public record Source(String name,int count) {}
    public record Catalog(List<Source> enchantments,List<Source> potions) {}
    public static Catalog detect(ServerPlayer player) {
        if(!AdminEditorService.allowed(player))throw new SecurityException(AdminEditorService.DENIED);
        var books=new TreeMap<String,Integer>();var potions=new TreeMap<String,Integer>();var resources=player.level().getServer().getResourceManager();
        for(var holder:player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()){
            var id=holder.key().identifier();String source=name(id.getNamespace());
            var file=Identifier.fromNamespaceAndPath(id.getNamespace(),"enchantment/"+id.getPath()+".json");var resource=resources.getResource(file);
            if(resource.isPresent()&&!resource.get().sourcePackId().equals("vanilla"))source="Pack: "+resource.get().sourcePackId();books.merge(source,1,Integer::sum);
        }
        for(var holder:player.registryAccess().lookupOrThrow(Registries.POTION).listElements().toList())potions.merge(name(holder.key().identifier().getNamespace()),1,Integer::sum);
        return new Catalog(books.entrySet().stream().map(e->new Source(e.getKey(),e.getValue())).toList(),potions.entrySet().stream().map(e->new Source(e.getKey(),e.getValue())).toList());
    }
    private static String name(String namespace){return namespace.equals("minecraft")?"Vanilla":FabricLoader.getInstance().getModContainer(namespace).map(mod->mod.getMetadata().getName()).orElse("Namespace: "+namespace);}
}
