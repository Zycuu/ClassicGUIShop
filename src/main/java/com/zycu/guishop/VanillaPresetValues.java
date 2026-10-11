package com.zycu.guishop;

import com.zycu.guishop.admin.AdminDocument;
import net.minecraft.core.RegistryAccess;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Audited whole-inventory value witnesses for the shipped protected presets. */
final class VanillaPresetValues {
    private VanillaPresetValues() {}
    private record Table(String name,Map<String,Double> buySeeds,Map<String,Double> values) {}
    private record Store(int version,String minecraft,String tradeProfileSha256,List<Table> tables) {}
    private static final Store STORE=load();
    private static Store load() {
        try(var stream=VanillaPresetValues.class.getResourceAsStream("/guishop/vanilla-preset-values-26.3.json");
            var trades=VanillaPresetValues.class.getResourceAsStream("/guishop/vanilla-trade-protection-26.3.json")) {
            if(stream==null||trades==null)throw new IllegalStateException("Missing protected preset values");
            var store=AdminDocument.JSON.fromJson(new String(stream.readAllBytes(),StandardCharsets.UTF_8),Store.class);
            var profile=com.google.gson.JsonParser.parseString(new String(trades.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().get("profile");
            String fingerprint=TradeLoopProtection.fingerprint(profile);
            if(store.version()!=1||!store.minecraft().equals("26.3")||!store.tradeProfileSha256().equals(fingerprint)||store.tables().size()!=4)
                throw new IllegalStateException("Preset values and villager protection do not match");
            for(var table:store.tables()) {
                if(table.buySeeds().isEmpty()||table.values().isEmpty())throw new IllegalStateException("Empty preset value witness");
                for(double value:table.values().values())if(!Double.isFinite(value)||value<0)throw new IllegalStateException("Invalid preset value witness");
            }
            return store;
        }catch(Exception error){throw new IllegalStateException("Could not load audited protected preset values",error);}
    }
    static Map<String,VanillaPriceSafety.Bound> forConfig(ShopConfig config,RegistryAccess access) {
        if(!config.villagerTradeLoopFix)return null;
        var buys=new HashMap<String,Double>();var sales=new HashMap<String,Double>();
        for(var category:config.categories)for(var item:category.items) {
            if(!VersionedListingStore.isRuntimeAvailable(item,access))continue;
            String key=VanillaPriceSafety.key(item.createStack(access));if(key==null)continue;
            if(item.buy>0)buys.merge(key,item.buy,Math::min);
            if(item.sell>0)sales.merge(key,item.sell,Math::max);
        }
        Table selected=null;double best=-1;
        for(var table:STORE.tables()) {
            // Removing a purchase route is safe. A cheaper or new purchase
            // invalidates this witness and uses the general analysis instead.
            if(buys.entrySet().stream().anyMatch(e->!table.buySeeds().containsKey(e.getKey())||e.getValue()+.000000001<table.buySeeds().get(e.getKey())))continue;
            if(sales.keySet().stream().anyMatch(k->!table.values().containsKey(k)))continue;
            double score=sales.entrySet().stream().mapToDouble(e->Math.min(e.getValue(),table.values().get(e.getKey())*.95)).sum();
            if(score>best){selected=table;best=score;}
        }
        if(selected==null)return null;
        var values=new TreeMap<String,VanillaPriceSafety.Bound>();
        for(var entry:selected.values().entrySet())values.put(entry.getKey(),new VanillaPriceSafety.Bound(entry.getValue(),
            "Audited "+selected.name()+" whole-inventory budget, including transformed joint outputs"));
        return values;
    }
}
