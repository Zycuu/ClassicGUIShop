package com.zycu.guishop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.*;

/** Reversible live payments calculated from shop values, not fixed trade fingerprints. */
public final class TradeLoopProtection {
    private TradeLoopProtection() {}
    public record Payment(ItemCost first, Optional<ItemCost> second, int minimum, boolean unavailable) {}
    public record Refresh(boolean enabled,int villagers,int offers,int changed,int unavailable) {
        public String description(){return enabled?"Trade Loop Fix On: "+villagers+" villagers refreshed, "+changed+" offers adjusted"+(unavailable>0?", "+unavailable+" exceed payment capacity":"")+".":"Trade Loop Fix Off: original payments restored for "+villagers+" villagers.";}
    }
    private record Market(ShopConfig config,Map<String,Double> assets,Map<String,Double> sales) {}
    private static final Map<MinecraftServer,Market> MARKETS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Refresh> REFRESHES=new WeakHashMap<>();

    private static synchronized Market market(MinecraftServer server) {
        var config=GuiShop.CONFIG;var cached=MARKETS.get(server);
        if(cached!=null&&cached.config()==config)return cached;
        var access=server.registryAccess();var selected=VanillaPresetValues.inventory(config,access);
        var assets=new TreeMap<String,Double>(selected==null?VanillaPresetValues.defaults():selected);
        if(selected==null) {
            // Existing/custom shops use their actual purchase/conversion costs.
            // Their own inconsistent prices remain visible to the read-only audit.
            for(var e:VanillaPriceSafety.conversionBounds(config,access).entrySet())assets.put(e.getKey(),e.getValue().cost());
            double currency=assets.getOrDefault("minecraft:emerald",2.0);
            currency=VanillaPriceSafety.unstackableEmeraldLimit(assets,access,currency);
            assets.put("minecraft:emerald",currency);assets.put("minecraft:emerald_block",currency*9);
        }
        var sales=new HashMap<String,Double>();
        for(var category:config.categories)for(var item:category.items)if(item.sell>0&&VersionedListingStore.isRuntimeAvailable(item,access)) {
            String key=VanillaPriceSafety.key(item.createStack(access));if(key!=null)sales.merge(key,item.sell,Math::max);
        }
        cached=new Market(config,assets,sales);MARKETS.put(server,cached);return cached;
    }
    private static double unit(ItemStack stack,Market market,boolean output) {
        String key=VanillaPriceSafety.key(stack);if(key==null)return Double.NaN;
        double value=market.assets().getOrDefault(key,Double.NaN);
        var enchants=stack.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        for(var e:enchants.entrySet())if(e.getKey().unwrapKey().isEmpty()||!e.getKey().unwrapKey().get().identifier().getNamespace().equals("minecraft"))return Double.NaN;
        if(output) {
            double sale=market.sales().getOrDefault(key,0.0);
            if(market.config().sellEnchantedEquipment&&stack.isDamageableItem())for(var e:enchants.entrySet()) {
                String book="minecraft:enchanted_book|enchantment="+e.getKey().unwrapKey().orElseThrow().identifier()+"@"+e.getIntValue();
                sale+=market.sales().getOrDefault(book,0.0);
            }
            value=Math.max(value,sale);
        }
        return value;
    }
    private static int ceiling(double value){return (int)Math.min(Integer.MAX_VALUE,Math.max(1,Math.ceil(value-1e-8)));}
    private static ItemCost count(ItemCost original,int amount){return new ItemCost(original.item(),amount,original.components());}
    private static boolean same(ItemCost a,ItemCost b){return a.item().equals(b.item())&&a.count()==b.count()&&a.components().equals(b.components());}
    private static boolean same(Optional<ItemCost> a,Optional<ItemCost> b){return a.isPresent()==b.isPresent()&&(a.isEmpty()||same(a.get(),b.get()));}
    private static ItemCost denomination(ItemCost original,int needed) {
        if(original.itemStack().is(Items.EMERALD)&&needed>64&&original.components().equals(net.minecraft.core.component.DataComponentExactPredicate.EMPTY))
            return new ItemCost(Items.EMERALD_BLOCK,ceiling(needed/9.0));
        return count(original,needed);
    }
    public static Payment payment(MerchantOffer offer) {
        var metadata=(ProtectedMerchantOffer)offer;
        if(GuiShop.CONFIG==null||!GuiShop.CONFIG.villagerTradeLoopFix||metadata.guishop$owner()==null)return null;
        var market=market(metadata.guishop$owner());if(market.sales().isEmpty())return null;
        return calculate(metadata.guishop$originalA(),metadata.guishop$originalB(),offer.getResult(),market);
    }
    private static Payment calculate(ItemCost original,Optional<ItemCost> second,ItemStack result,Market market) {
        double input=unit(original.itemStack(),market,false),output=unit(result,market,true)*result.getCount();
        double extra=second.map(c->unit(c.itemStack(),market,false)*c.count()).orElse(0.0);
        if(!(input>0)||!Double.isFinite(output)||!Double.isFinite(extra))return null;
        int needed=ceiling(Math.max(0,output-extra)/input);
        var first=denomination(original,needed);double firstValue=unit(first.itemStack(),market,false);
        int max=first.itemStack().getMaxStackSize();int floor=Math.min(max,first.count());
        int base=first.itemStack().is(original.itemStack().getItem())?Math.max(original.count(),floor):Math.max(ceiling(original.count()/9.0),floor);
        first=count(first,Math.min(max,base));
        var paymentB=second;
        if(needed*input>floor*firstValue+1e-7) {
            double remaining=output-floor*firstValue;
            ItemCost b=second.isPresent()?second.get():original.itemStack().getMaxStackSize()>1?count(first,1):new ItemCost(Items.EMERALD);
            double bValue=unit(b.itemStack(),market,false);
            if(!(bValue>0))return new Payment(first,paymentB,floor,true);
            int bNeeded=ceiling(remaining/bValue);var adjusted=denomination(b,bNeeded);
            if(!adjusted.itemStack().is(b.itemStack().getItem()))bValue=unit(adjusted.itemStack(),market,false);
            int required=Math.max(adjusted.count(),adjusted.itemStack().is(b.itemStack().getItem())?b.count():ceiling(b.count()/9.0));
            if(required>adjusted.itemStack().getMaxStackSize()||floor*firstValue+required*bValue+1e-7<output)return new Payment(first,paymentB,floor,true);
            paymentB=Optional.of(count(adjusted,required));
        }
        return new Payment(first,paymentB,floor,false);
    }
    public static List<VanillaPriceSafety.Input> inputs(Map<String,Double> selected,List<VanillaPriceSafety.Input> inputs,String output,double quantity) {
        if(selected==null||inputs.isEmpty())return inputs;
        double value=selected.getOrDefault(output,0.0)*quantity;
        double b=inputs.size()>1?inputs.get(1).choices().stream().mapToDouble(k->selected.getOrDefault(k,0.0)).min().orElse(0)*inputs.get(1).count():0;
        var a=inputs.getFirst();double unit=a.choices().stream().mapToDouble(k->selected.getOrDefault(k,0.0)).min().orElse(0);
        if(!(unit>0))return inputs;
        int required=ceiling(Math.max(0,value-b)/unit);var replacement=new ArrayList<>(inputs);
        if(a.choices().equals(List.of("minecraft:emerald"))&&required>64)replacement.set(0,new VanillaPriceSafety.Input(List.of("minecraft:emerald_block"),Math.max(ceiling(a.count()/9),ceiling(required/9.0))));
        else replacement.set(0,new VanillaPriceSafety.Input(a.choices(),Math.max(a.count(),required)));
        return replacement;
    }
    public static void generated(VillagerTrade definition,LootContext context,MerchantOffer offer) {
        var entity=context.getOptional(LootContextParams.THIS_ENTITY);
        if(offer!=null&&entity instanceof AbstractVillager merchant&&vanilla(merchant))((ProtectedMerchantOffer)offer).guishop$protection(context.getLevel().getServer());
    }
    private static boolean vanilla(AbstractVillager merchant){return merchant.getType()==EntityTypes.VILLAGER||merchant.getType()==EntityTypes.WANDERING_TRADER;}
    public static void prepare(AbstractVillager merchant,MerchantOffers offers) {
        if(offers==null||!vanilla(merchant)||!(merchant.level() instanceof ServerLevel level))return;
        for(var offer:offers)((ProtectedMerchantOffer)offer).guishop$protection(level.getServer());
    }
    public static synchronized Refresh changed(MinecraftServer server) {
        MARKETS.remove(server);int villagers=0,offers=0,changed=0,unavailable=0;
        for(var player:server.getPlayerList().getPlayers())if(player.containerMenu instanceof MerchantMenu){player.closeContainer();ShopMessages.info(player,"Villager trades changed. Reopen the trading window.");}
        for(var level:server.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof AbstractVillager merchant&&vanilla(merchant)) {
            var loaded=((VillagerOfferAccess)merchant).guishop$loadedOffers();if(loaded==null)continue;
            villagers++;prepare(merchant,loaded);
            for(var offer:loaded){offers++;var p=payment(offer);if(p!=null){if(p.unavailable())unavailable++;var m=(ProtectedMerchantOffer)offer;if(!same(p.first(),m.guishop$originalA())||!same(p.second(),m.guishop$originalB())||p.minimum()>1)changed++;}}
        }
        var result=new Refresh(GuiShop.CONFIG.villagerTradeLoopFix,villagers,offers,changed,unavailable);REFRESHES.put(server,result);
        System.out.println("[ClassicGUIShop] "+result.description());return result;
    }
    public static synchronized String lastSummary(MinecraftServer server){var r=REFRESHES.get(server);return r==null?"":r.description();}
    public static synchronized Refresh lastRefresh(MinecraftServer server){return REFRESHES.get(server);}
    public static synchronized void clear(){MARKETS.clear();REFRESHES.clear();}
}
