package com.zycu.guishop.testing;

import com.mojang.serialization.JsonOps;
import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.util.*;

/** Native saved-offer, packet, purchase, tooltip, and mob payout regressions. */
public final class Beta7Checks {
    private Beta7Checks() {}
    public static void run(ServerPlayer viewer) throws Exception {
        var original=GuiShop.CONFIG;String before=AdminDocument.JSON.toJson(original);
        var config=AdminDocument.JSON.fromJson(before,ShopConfig.class);
        double balance=GuiShop.ECONOMY.readBalance(viewer.getUUID());
        GuiShop.CONFIG=config;GuiShop.ECONOMY.updateConfig(config);
        var results=new LinkedHashMap<String,Object>();
        try {
            config.villagerTradeLoopFix=true;
            config.categories=new ArrayList<>(ShopPresets.generate(viewer,new ShopPresets.Request("normal",.30)).categories());
            trades(viewer,results);potions(viewer,results);mobs(viewer,results);
            check(before.equals(AdminDocument.JSON.toJson(original)),"Regression checks preserve the live editor snapshot");
            Files.writeString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("guishop/beta7-checks.json"),AdminDocument.JSON.toJson(results));
            System.out.println("[Beta 7 test] PASS: saved native offers and progress; On/Off; actual payment and vanilla packet parity; new offers; custom offer exclusion; common item sales; arrow order and families; native effect durations; living-only mob discovery and capped defaults; exact configured payouts");
        }finally {
            viewer.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);viewer.getInventory().clearContent();
            GuiShop.CONFIG=original;GuiShop.ECONOMY.updateConfig(original);GuiShop.ECONOMY.setBalance(viewer.getUUID(),balance);
        }
    }
    private static void trades(ServerPlayer viewer,Map<String,Object> results) throws Exception {
        var server=viewer.level().getServer();var access=viewer.registryAccess();var ops=RegistryOps.create(JsonOps.INSTANCE,access);
        var village=EntityTypes.VILLAGER.create(viewer.level(),EntitySpawnReason.COMMAND);
        village.setVillagerData(village.getVillagerData().withProfession(access.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(ResourceKey.create(Registries.VILLAGER_PROFESSION,Identifier.withDefaultNamespace("librarian")))).withLevel(4));
        village.setVillagerXp(140);
        var offer=new MerchantOffer(new ItemCost(Items.EMERALD,1),Optional.empty(),new ItemStack(Items.GLASS,4),5,12,10,.05f,2);
        offer.setSpecialPriceDiff(-7);var offers=new MerchantOffers();offers.add(offer);
        var field=AbstractVillager.class.getDeclaredField("offers");field.setAccessible(true);field.set(village,offers);
        var saved=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,access);village.saveWithoutId(saved);
        var tag=saved.buildResult();var savedTerms=MerchantOffer.CODEC.encodeStart(ops,offer).getOrThrow();
        var restored=EntityTypes.VILLAGER.create(viewer.level(),EntitySpawnReason.COMMAND);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING,access,tag));
        var current=restored.getOffers().getFirst();
        check(current.getCostA().getCount()==4,"Saved vanilla offer receives safe minimum");
        check(restored.getVillagerXp()==140&&restored.getVillagerData().level()==4&&current.getUses()==5&&current.getMaxUses()==12&&current.getDemand()==2,"Existing villager and offer progress preserved");
        check(savedTerms.equals(MerchantOffer.CODEC.encodeStart(ops,current).getOrThrow()),"Protection never rewrites original saved terms");
        check(!current.take(new ItemStack(Items.EMERALD),ItemStack.EMPTY),"Unsafe original payment cannot complete a protected trade");
        var payment=new ItemStack(Items.EMERALD,7);check(current.take(payment,ItemStack.EMPTY)&&payment.getCount()==3,"Native take consumes exactly the shown four emeralds");
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),access);
        MerchantOffer.STREAM_CODEC.encode(buffer,current);var client=MerchantOffer.STREAM_CODEC.decode(buffer);buffer.release();
        check(client.getCostA().getCount()==current.getCostA().getCount()&&client.getUses()==current.getUses()&&client.getResult().getCount()==4,"Vanilla client packet displays authoritative payment and stock");
        check(current.copy().getCostA().getCount()==4,"Copied server offers preserve the protection");
        restored.getGossips().add(viewer.getUUID(),GossipType.MAJOR_POSITIVE,20);restored.getGossips().add(viewer.getUUID(),GossipType.MINOR_POSITIVE,25);restored.getGossips().add(viewer.getUUID(),GossipType.TRADING,25);
        viewer.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,1000,4));
        var discount=Villager.class.getDeclaredMethod("updateSpecialPrices",net.minecraft.world.entity.player.Player.class);discount.setAccessible(true);discount.invoke(restored,viewer);
        check(current.getCostA().getCount()==4,"Reputation and Hero V cannot remove the floor");viewer.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
        current.updateDemand();check(current.getCostA().getCount()>=4,"Restocking demand cannot remove the floor");
        var termsAfter=MerchantOffer.CODEC.encodeStart(ops,current).getOrThrow();
        GuiShop.CONFIG.villagerTradeLoopFix=false;
        check(current.getCostA().getCount()==1&&current.getUses()==5&&restored.getVillagerXp()==140,"Off restores vanilla payments without rolling back progress");
        check(termsAfter.equals(MerchantOffer.CODEC.encodeStart(ops,current).getOrThrow()),"Off needs no destructive offer replacement");
        GuiShop.CONFIG.villagerTradeLoopFix=true;
        var params=new LootParams.Builder(viewer.level()).withParameter(LootContextParams.THIS_ENTITY,restored).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED,net.minecraft.util.Unit.INSTANCE).create(LootContextParamSets.VILLAGER_TRADE);
        var context=new LootContext.Builder(params).withOptionalRandomSeed(71).create(Optional.empty());
        var definition=access.lookupOrThrow(Registries.VILLAGER_TRADE).getOrThrow(ResourceKey.create(Registries.VILLAGER_TRADE,Identifier.parse("minecraft:librarian/3/emerald_glass"))).value();
        var generated=definition.getOffer(context);check(generated.getCostA().getCount()==4,"Newly generated vanilla offers use the same protection");
        var custom=new MerchantOffer(new ItemCost(Items.EMERALD),new ItemStack(Items.GLASS,5),12,10,.05f);
        var customOffers=new MerchantOffers();customOffers.add(custom);TradeLoopProtection.prepare(restored,customOffers);check(custom.getCostA().getCount()==5,"Standard vanilla items with changed quantities receive a calculated payment");
        var named=new ItemStack(Items.GLASS,4);named.set(DataComponents.CUSTOM_NAME,Component.literal("Custom glass"));
        var customName=new MerchantOffer(new ItemCost(Items.EMERALD),named,12,10,.05f);customOffers.clear();customOffers.add(customName);TradeLoopProtection.prepare(restored,customOffers);check(customName.getCostA().getCount()==1,"Custom item components are not rewritten");
        var modified=VillagerTrade.CODEC.encodeStart(ops,definition).getOrThrow().deepCopy();modified.getAsJsonObject().getAsJsonObject("gives").addProperty("count",6);
        var customDefinition=VillagerTrade.CODEC.parse(ops,modified).getOrThrow();check(customDefinition.getOffer(context).getCostA().getCount()==6,"Vanilla-item definitions do not depend on a static fingerprint");
        results.put("savedVillager",Map.of("level",4,"xp",140,"uses",5,"protectedPayment",4,"nativePaymentWhenOff",1,"originalTermsPreserved",true,"vanillaPacketParity",true));
    }
    private static void potions(ServerPlayer viewer,Map<String,Object> results) {
        var plan=ShopPresets.generate(viewer,new ShopPresets.Request("normal",.30));
        var combat=plan.categories().stream().filter(c->c.id.equals("combat")).findFirst().orElseThrow();
        var ordered=ListingGroups.entries(combat.items,viewer.registryAccess(),null);
        int regular=-1,tipped=-1,spectral=-1;
        for(int i=0;i<ordered.size();i++){var entry=ordered.get(i);if("arrows".equals(entry.group()))tipped=i;else if(!entry.folder()){if(entry.item().item.equals("minecraft:arrow"))regular=i;if(entry.item().item.equals("minecraft:spectral_arrow"))spectral=i;}}
        check(regular>=0&&tipped==regular+1&&spectral==tipped+1,"Regular arrow, tipped-arrow folder, spectral arrow are adjacent in order");
        var speed=ListingGroups.leaves(combat.items,viewer.registryAccess(),"arrow:minecraft:swiftness");check(speed.size()==3,"All speed arrow durations and strengths share one folder");
        int effects=0;
        for(var holder:viewer.registryAccess().lookupOrThrow(Registries.POTION).listElements().filter(h->h.key().identifier().getNamespace().equals("minecraft")).toList())for(var type:List.of(Items.POTION,Items.SPLASH_POTION,Items.LINGERING_POTION,Items.TIPPED_ARROW)) {
            var template=PotionContents.createItemStack(type,holder);var copy=template.copy();var expected=new ArrayList<Component>();template.get(DataComponents.POTION_CONTENTS).addToTooltip(Item.TooltipContext.EMPTY,expected::add,TooltipFlag.NORMAL,template);
            for(var icon:List.of(ShopIcons.listing(template,template.getHoverName().getString(),"",10,3,GuiShop.CONFIG,true),ShopIcons.editor(template,template.getHoverName().getString(),"",10,3,GuiShop.CONFIG))) {
                var lore=icon.get(DataComponents.LORE).lines();check(lore.size()>expected.size()&&lore.subList(0,expected.size()).equals(expected),"Native potion effects appear before price lore: "+holder.key().identifier()+" "+type);
                check(lore.stream().anyMatch(c->c.getString().startsWith("Buy:")),"Price lore remains after effects");
            }
            check(ItemStack.isSameItemSameComponents(template,copy),"Building tooltips never mutates purchase components");effects++;
        }
        var arrow=PotionContents.createItemStack(Items.TIPPED_ARROW,Potions.LONG_SWIFTNESS);var icon=ShopIcons.listing(arrow,"Long speed arrow","",10,3,GuiShop.CONFIG,true);
        check(icon.get(DataComponents.LORE).lines().stream().anyMatch(c->c.getString().contains("1:00")),"Extended arrow shows its actual one-minute duration");
        results.put("arrowAndPotionTooltips",Map.of("variantsChecked",effects,"adjacentArrowOrder",true,"durationAndStrengthFamilies",true,"extendedSpeedArrowDuration","1:00","purchaseDataPreserved",true));
    }
    private static void mobs(ServerPlayer viewer,Map<String,Object> results) throws Exception {
        var catalog=ShopModes.catalog(viewer);var ids=catalog.entities().stream().map(ShopModes.Offer::id).collect(java.util.stream.Collectors.toSet());
        check(ids.containsAll(List.of("minecraft:zombie","minecraft:cow","minecraft:warden","minecraft:ender_dragon")),"Wild, farmable and hostile living mobs detected");
        check(Collections.disjoint(ids,List.of("minecraft:arrow","minecraft:armor_stand","minecraft:mannequin","minecraft:item","minecraft:oak_boat","minecraft:item_display")),"Objects, projectiles and decorations excluded");
        check(catalog.entities().stream().allMatch(o->o.suggested()>=0&&o.suggested()<=50)&&catalog.entities().stream().anyMatch(o->o.suggested()==50),"Suggested vanilla rewards cap at fifty");
        GuiShop.CONFIG.modes=new ShopModesConfig();GuiShop.CONFIG.modes.mobsEnabled=true;GuiShop.CONFIG.modes.allowSpawnerFarming=true;
        GuiShop.ECONOMY.setBalance(viewer.getUUID(),100);
        var zombie=EntityTypes.ZOMBIE.create(viewer.level(),EntitySpawnReason.COMMAND);ShopModes.killed(zombie,viewer);check(GuiShop.ECONOMY.readBalance(viewer.getUUID())==110,"Unconfigured vanilla zombie pays its ten-unit default");
        GuiShop.CONFIG.modes.mobPrices.put("minecraft:zombie",65.0);
        ShopModes.killed(EntityTypes.ZOMBIE.create(viewer.level(),EntitySpawnReason.COMMAND),viewer);check(GuiShop.ECONOMY.readBalance(viewer.getUUID())==175,"Explicit admin rewards above the default remain supported");
        GuiShop.CONFIG.modes.mobPrices.put("minecraft:armor_stand",500.0);
        var stand=EntityTypes.ARMOR_STAND.create(viewer.level(),EntitySpawnReason.COMMAND);ShopModes.killed(stand,viewer);check(GuiShop.ECONOMY.readBalance(viewer.getUUID())==175,"Armor stands cannot earn mob rewards even with an old configured price");
        if(PermissionRuntimeTest.BETA7_MOB!=null) {
            check(ids.contains("guishop_test:wild_mob"),"Installed test mob is detected");
            var mod=PermissionRuntimeTest.BETA7_MOB.create(viewer.level(),EntitySpawnReason.COMMAND);ShopModes.killed(mod,viewer);check(GuiShop.ECONOMY.readBalance(viewer.getUUID())==175,"Detected modded mobs stay off until configured");
            GuiShop.CONFIG.modes.mobPrices.put("guishop_test:wild_mob",13.0);ShopModes.killed(PermissionRuntimeTest.BETA7_MOB.create(viewer.level(),EntitySpawnReason.COMMAND),viewer);check(GuiShop.ECONOMY.readBalance(viewer.getUUID())==188,"Explicitly added detected mob receives its configured reward");
        }
        results.put("mobRewards",Map.of("detectedLivingMobs",ids.size(),"maximumSuggested",50,"zombieDefault",10,"nonLivingExcluded",true,"detectedMobsRequireConfiguration",true));
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
