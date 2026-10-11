package com.zycu.guishop;

import com.zycu.guishop.admin.AdminDocument;
import net.minecraft.core.RegistryAccess;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Joint inventory values and sale limits for the shipped preset prices. */
final class VanillaPresetValues {
    private VanillaPresetValues() {}
    private record Table(String name,Map<String,Double> buySeeds,Map<String,Double> values,Map<String,Double> inventoryValues) {}
    private record Store(int version,String minecraft,List<Table> tables) {}
    private static final Store STORE=load();
    private static Store load() {
        try(var stream=VanillaPresetValues.class.getResourceAsStream("/guishop/vanilla-preset-values-26.3.json")) {
            if(stream==null)throw new IllegalStateException("Missing preset values");
            var store=AdminDocument.JSON.fromJson(new String(stream.readAllBytes(),StandardCharsets.UTF_8),Store.class);
            if(store.version()!=2||!store.minecraft().equals("26.3")||store.tables().size()!=4)
                throw new IllegalStateException("Invalid preset values");
            for(var table:store.tables()) {
                if(table.buySeeds().isEmpty()||table.values().isEmpty()||table.inventoryValues().isEmpty())throw new IllegalStateException("Empty preset value witness");
                for(var map:List.of(table.values(),table.inventoryValues()))for(double value:map.values())if(!Double.isFinite(value)||value<0)throw new IllegalStateException("Invalid preset value witness");
            }
            return store;
        }catch(Exception error){throw new IllegalStateException("Could not load audited protected preset values",error);}
    }
    private static Table select(ShopConfig config,RegistryAccess access) {
        var buys=new HashMap<String,Double>();var sales=new HashMap<String,Double>();
        for(var category:config.categories)for(var item:category.items) {
            if(!VersionedListingStore.isRuntimeAvailable(item,access))continue;
            String key=VanillaPriceSafety.key(item.createStack(access));if(key==null)continue;
            if(item.buy>0)buys.merge(key,item.buy,Math::min);
            if(item.sell>0)sales.merge(key,item.sell,Math::max);
        }
        Table selected=null;double best=Double.POSITIVE_INFINITY;
        for(var table:STORE.tables()) {
            // Removing a purchase route is safe. A cheaper or new purchase
            // invalidates this witness and uses the general analysis instead.
            if(buys.entrySet().stream().anyMatch(e->!table.buySeeds().containsKey(e.getKey())||e.getValue()+.000000001<table.buySeeds().get(e.getKey())))continue;
            if(sales.keySet().stream().anyMatch(k->!table.values().containsKey(k)))continue;
            // Prefer the matching preset, rather than borrowing Easy's limits
            // simply because its buy prices happen to be lower than Normal's.
            double score=(table.buySeeds().size()-buys.size())*1000.0;
            for(var buy:buys.entrySet())score+=Math.log(buy.getValue()/table.buySeeds().get(buy.getKey()));
            if(score<best){selected=table;best=score;}
        }
        return selected;
    }
    static Map<String,VanillaPriceSafety.Bound> forConfig(ShopConfig config,RegistryAccess access) {
        var selected=select(config,access);if(selected==null)return null;
        var values=new TreeMap<String,VanillaPriceSafety.Bound>();
        for(var entry:selected.values().entrySet())values.put(entry.getKey(),new VanillaPriceSafety.Bound(entry.getValue(),
            "Audited "+selected.name()+" inventory budget and value-based villager payments"));
        return values;
    }
    static Map<String,Double> inventory(ShopConfig config,RegistryAccess access) {
        var selected=select(config,access);return selected==null?null:selected.inventoryValues();
    }
    static Map<String,Double> defaults() {return STORE.tables().getFirst().inventoryValues();}
}
