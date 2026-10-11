package com.zycu.guishop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.zycu.guishop.admin.AdminDocument;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Audited minimum payments for recognized, unmodified vanilla merchant offers. */
public final class TradeLoopProtection {
    private TradeLoopProtection() {}
    private record Rule(int minimum, int base, JsonElement wants, JsonElement gives, JsonElement additional_wants,
                        int maxUses, int xp, float discount, String fingerprint) {}
    private record Profile(int version, String minecraft, Map<String,Rule> profile) {}
    private record Match(String name, Rule rule, ItemCost wants, ItemStack gives) {}
    private record Cache(Object resources, Map<String,Rule> valid, Map<String,List<Match>> matches) {}
    private static final Profile PROFILE=load();
    private static final Map<MinecraftServer,Cache> CACHES=new WeakHashMap<>();
    private static Profile load() {
        try(var stream=TradeLoopProtection.class.getResourceAsStream("/guishop/vanilla-trade-protection-26.3.json")) {
            if(stream==null)throw new IllegalStateException("Missing vanilla trade profile");
            var profile=AdminDocument.JSON.fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Profile.class);
            if(profile.version()!=1||!profile.minecraft().equals("26.3")||profile.profile().isEmpty())throw new IllegalStateException("Invalid vanilla trade profile");
            for(var rule:profile.profile().values())if(rule.minimum()<1||rule.minimum()>64||rule.fingerprint()==null)throw new IllegalStateException("Invalid vanilla trade payment");
            return profile;
        }catch(Exception error){throw new IllegalStateException("Could not load vanilla trade protection",error);}
    }
    public static int minimum(String definition) {
        var rule=PROFILE.profile().get(definition.startsWith("minecraft:")?definition.substring(10):definition);
        return rule==null?0:rule.minimum();
    }
    // Canonical native-codec fingerprints keep overwritten datapack definitions
    // and modded trade providers outside this vanilla-only compatibility path.
    private static JsonElement sorted(JsonElement value) {
        if(value.isJsonObject()){var result=new JsonObject();value.getAsJsonObject().keySet().stream().sorted().forEach(k->result.add(k,sorted(value.getAsJsonObject().get(k))));return result;}
        if(value.isJsonArray()){var result=new com.google.gson.JsonArray();value.getAsJsonArray().forEach(v->result.add(sorted(v)));return result;}
        return value;
    }
    static String fingerprint(JsonElement value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sorted(value).toString().getBytes(StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    private static Cache cache(MinecraftServer server) {
        var cached=CACHES.get(server);
        if(cached!=null&&cached.resources()==server.getResourceManager())return cached;
        var valid=new HashMap<String,Rule>();var matches=new HashMap<String,List<Match>>();
        var ops=RegistryOps.create(JsonOps.INSTANCE,server.registryAccess());
        var registry=server.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
        for(var entry:PROFILE.profile().entrySet()) {
            var holder=registry.get(Identifier.withDefaultNamespace(entry.getKey()));if(holder.isEmpty())continue;
            var rule=entry.getValue();
            var encoded=VillagerTrade.CODEC.encodeStart(ops,holder.get().value()).result().orElse(null);
            if(encoded==null||!rule.fingerprint().equals(fingerprint(encoded)))continue;
            var wants=ItemCost.CODEC.parse(ops,rule.wants()).getOrThrow();
            var gives=ItemStack.CODEC.parse(ops,rule.gives()).getOrThrow();
            if(rule.minimum()>wants.itemStack().getMaxStackSize())throw new IllegalStateException("Trade profile exceeds input stack size: "+entry.getKey());
            valid.put(entry.getKey(),rule);
            matches.computeIfAbsent(pair(wants.itemStack(),gives),ignored->new ArrayList<>()).add(new Match(entry.getKey(),rule,wants,gives));
        }
        cached=new Cache(server.getResourceManager(),valid,matches);CACHES.put(server,cached);return cached;
    }
    private static String pair(ItemStack wants,ItemStack gives){return ShopService.itemId(wants)+">"+ShopService.itemId(gives);}
    public static int floor(MerchantOffer offer) {
        var protectedOffer=(ProtectedMerchantOffer)offer;
        if(GuiShop.CONFIG==null||!GuiShop.CONFIG.villagerTradeLoopFix||protectedOffer.guishop$owner()==null)return 0;
        var cache=cache(protectedOffer.guishop$owner());
        var rule=cache.valid().get(protectedOffer.guishop$definition());
        if(rule==null)return 0;
        for(var match:cache.matches().getOrDefault(pair(offer.getBaseCostA(),offer.getResult()),List.of()))
            if(match.name().equals(protectedOffer.guishop$definition())&&matches(offer,match))return protectedOffer.guishop$minimum();
        return 0;
    }
    public static void generated(VillagerTrade definition,LootContext context,MerchantOffer offer) {
        if(offer==null)return;
        var entity=context.getOptional(LootContextParams.THIS_ENTITY);
        if(!(entity instanceof AbstractVillager merchant)||!vanilla(merchant))return;
        var server=context.getLevel().getServer();
        var id=server.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).getKey(definition);
        if(id==null||!id.getNamespace().equals("minecraft"))return;
        var rule=cache(server).valid().get(id.getPath());
        if(rule!=null)for(var match:cache(server).matches().getOrDefault(pair(offer.getBaseCostA(),offer.getResult()),List.of()))
            if(match.name().equals(id.getPath())&&matches(offer,match)){((ProtectedMerchantOffer)offer).guishop$protection(server,id.getPath(),rule.minimum());break;}
    }
    private static boolean vanilla(AbstractVillager merchant){return merchant.getType()==EntityTypes.VILLAGER||merchant.getType()==EntityTypes.WANDERING_TRADER;}
    private static boolean matches(MerchantOffer offer,Match match) {
        var rule=match.rule();
        return offer.getBaseCostA().getCount()==rule.base()&&ItemStack.isSameItemSameComponents(offer.getBaseCostA(),match.wants().itemStack())
            &&offer.getItemCostA().components().equals(match.wants().components())&&offer.getCostB().isEmpty()
            &&offer.getResult().getCount()==match.gives().getCount()&&ItemStack.isSameItemSameComponents(offer.getResult(),match.gives())
            &&offer.getMaxUses()==rule.maxUses()&&offer.getXp()==rule.xp()&&Float.compare(offer.getPriceMultiplier(),rule.discount())==0;
    }
    /** Reattach transient protection to saved offers. No offer terms are rewritten. */
    public static void prepare(AbstractVillager merchant,MerchantOffers offers) {
        if(offers==null||!vanilla(merchant)||!(merchant.level() instanceof ServerLevel level))return;
        var server=level.getServer();var cache=cache(server);
        for(var offer:offers) {
            var candidates=cache.matches().getOrDefault(pair(offer.getBaseCostA(),offer.getResult()),List.of());
            var protectedOffer=(ProtectedMerchantOffer)offer;
            if(protectedOffer.guishop$owner()==server&&cache.valid().containsKey(protectedOffer.guishop$definition()))continue;
            protectedOffer.guishop$protection(null,null,0);
            for(var match:candidates){
                boolean wandering=merchant.getType()==EntityTypes.WANDERING_TRADER;
                if(wandering!=match.name().startsWith("wandering_trader/"))continue;
                if(!matches(offer,match))continue;
                protectedOffer.guishop$protection(server,match.name(),match.rule().minimum());break;
            }
        }
    }
    public static void changed(MinecraftServer server) {
        // A vanilla client must not retain a preview prepared under the other
        // setting while the authoritative payment has already changed.
        for(var player:server.getPlayerList().getPlayers())if(player.containerMenu instanceof MerchantMenu){player.closeContainer();ShopMessages.info(player,"Villager trades changed. Reopen the trading window.");}
    }
    public static void clear(){CACHES.clear();}
}
