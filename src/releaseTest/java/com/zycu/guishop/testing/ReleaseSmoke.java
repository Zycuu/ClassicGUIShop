package com.zycu.guishop.testing;

import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.*;
import java.nio.file.*;
import java.util.*;

/** Disposable-server release checks. This addon is never bundled in the release. */
public final class ReleaseSmoke implements ModInitializer {
    private boolean ran;
    public void onInitialize(){
        if(!Boolean.getBoolean("guishop.disposableReleaseTest"))throw new IllegalStateException("Release test requires a disposable fixture");
        ServerTickEvents.END_SERVER_TICK.register(server->{if(ran||server.getTickCount()<4)return;ran=true;try{run(server);}catch(Throwable error){error.printStackTrace();System.out.println("[Release smoke] FAIL: "+error);}});
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void run(net.minecraft.server.MinecraftServer server)throws Exception{
        var level=server.overworld();level.getChunk(0,0);
        var player=new ServerPlayer(server,level,new GameProfile(UUID.fromString("a34bf098-dc2f-4e33-9cc2-d129238fdb68"),"ReleaseTest"),ClientInformation.createDefault());
        player.setPos(8,128,8);
        player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false));
        check(!AdminEditorService.allowed(player),"Unprivileged player cannot access Adminshop");
        var generator=com.zycu.guishop.admin.ShopPresets.class.getDeclaredMethod("generate",net.minecraft.server.MinecraftServer.class,com.zycu.guishop.admin.ShopPresets.Request.class);generator.setAccessible(true);
        var nativePrices=new java.util.LinkedHashMap<String,Object>();
        for(String preset:List.of("normal","hard","easy","basics")){
            var plan=(com.zycu.guishop.admin.ShopPresets.Plan)generator.invoke(null,server,new com.zycu.guishop.admin.ShopPresets.Request(preset,.30));
            var prices=new ArrayList<Object>();
            for(var category:plan.categories())for(var listing:category.items){String key=VanillaPriceSafety.key(listing.createStack(server.registryAccess()));if(key!=null)prices.add(Map.of("key",key,"buy",listing.buy,"sell",listing.sell));}
            nativePrices.put(preset,prices);
        }
        Files.writeString(Path.of(System.getProperty("guishop.releaseReport")+".prices.json"),AdminDocument.JSON.toJson(nativePrices));
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0"))PermissionChecks.verify(player);
        var unavailable=new ShopConfig.ShopItem();unavailable.item="minecraft:enchanted_book";
        unavailable.stack=new com.google.gson.JsonPrimitive("{id:\"minecraft:enchanted_book\",Count:1b,tag:{StoredEnchantments:[{id:\"missingmod:no_such_enchant\",lvl:1s}]}}");
        check(!VersionedListingStore.isRuntimeAvailable(unavailable,server.registryAccess()),"Missing enchantment is preserved instead of becoming a plain book");
        unavailable.item="minecraft:potion";unavailable.stack=com.google.gson.JsonParser.parseString("{\"id\":\"minecraft:potion\",\"count\":1,\"components\":{\"minecraft:potion_contents\":{\"potion\":\"missingmod:no_such_potion\"}}}");
        check(!VersionedListingStore.isRuntimeAvailable(unavailable,server.registryAccess()),"Missing or incompatible potion data is preserved");
        var original=GuiShop.CONFIG;
        int trades=0,variants=0;
        var stacks=new ArrayList<ItemStack>();
        for(var item:BuiltInRegistries.ITEM)if(item!=Items.AIR&&!ShopMaps.nativeMap(new ItemStack(item)))stacks.add(new ItemStack(item));
        var access=server.registryAccess();
        for(var h:access.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList())for(int i=1;i<=h.value().getMaxLevel();i++){
            var book=EnchantmentShopService.createBook(h,i);
            check(book.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY).getLevel(h)==i,"Usable stored enchantment level "+h.key()+" "+i);
            stacks.add(book);variants++;
        }
        for(var h:access.lookupOrThrow(Registries.POTION).listElements().toList())for(var item:List.of(Items.POTION,Items.SPLASH_POTION,Items.LINGERING_POTION,Items.TIPPED_ARROW)){stacks.add(PotionContents.createItemStack(item,h));variants++;}
        try{
            var config=new ShopConfig();config.categories.clear();var category=new ShopConfig.Category();category.id="release_test";category.name="Release test";config.categories.add(category);GuiShop.CONFIG=config;
            for(var stack:stacks){
                var item=new ShopConfig.ShopItem();item.item=ShopService.itemId(stack);item.listingId=ItemStackData.listingId(stack,access);item.stack=ItemStackData.encode(stack,access);item.name=stack.getHoverName().getString();item.buy=10;item.sell=2;category.items.clear();category.items.add(item);
                var before=item.stack.deepCopy();ShopIcons.listing(stack,item.name,"",10,2,config,true);ShopIcons.display(stack,"Category","");
                check(before.equals(ItemStackData.encode(stack,access)),"Display preserves template "+item.listingId);
                check(ItemStackData.same(stack,ItemStackData.decode(before,item.item,access)),"Lossless item codec "+item.listingId);
                player.getInventory().clearContent();GuiShop.ECONOMY.setBalance(player.getUUID(),1000);
                check(ShopService.trade(player,item,ShopGui.Mode.BUY,1),"Purchase "+item.listingId);
                check(ItemStackData.same(stack,player.getInventory().getItem(0)),"Purchase preserves data "+item.listingId);
                check(ShopService.trade(player,item,ShopGui.Mode.SELL,1),"Sale "+item.listingId);
                check(player.getInventory().isEmpty()&&GuiShop.ECONOMY.balance(player.getUUID())==992,"Exact quantity and account result "+item.listingId);trades++;
            }
            var book=EnchantmentShopService.createBook(access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("looting"))),5);
            check(book.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY).getLevel(access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("looting"))))==5,"Above-vanilla stored level preserved");
            var map=ShopMaps.prepare(player,new ItemStack(Items.FILLED_MAP));check(map.getItem()==Items.FILLED_MAP&&MapItem.getSavedData(map,level)!=null,"World-backed filled map works");
            var empty=ShopMaps.prepare(player,new ItemStack(Items.MAP));check(empty.is(Items.MAP),"Empty map remains an empty map");
            GuiShop.CONFIG=original;
            var villager=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);var offers=villager.getOffers();offers.clear();var glass=new MerchantOffer(new ItemCost(Items.EMERALD),new ItemStack(Items.GLASS,4),12,1,0);offers.add(glass);villager.getOffers();
            check(glass.getCostA().getCount()>1,"Existing villager cost protected");glass.setSpecialPriceDiff(-999);check(glass.getCostA().getCount()>1,"Discount cannot bypass protected minimum");
            original.villagerTradeLoopFix=false;check(glass.getCostA().getCount()==1,"Turning off restores vanilla payment");original.villagerTradeLoopFix=true;check(glass.getCostA().getCount()>1,"Turning on updates existing offer immediately");
            var message="x".repeat(25000)+"💎".repeat(1000);var parts=AdminPayloads.chunks("release","apply","revision",message);var assembly=new AdminPayloads.Assembly(parts.getFirst());for(var part:parts)assembly.add(part);check(assembly.body().equals(message),"Bounded packet assembly preserves Unicode");
            for(String command:List.of("shop","adminshop","bal","econ","worth","sellhand","shopreward"))check(server.getCommands().getDispatcher().getRoot().getChild(command)!=null,"Command registered "+command);
            var report=Map.of("passed",true,"minecraft",net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString(),"purchaseSalePairs",trades,"enchantmentPotionVariants",variants,"checks",List.of("native item identity and data","real purchases and sales","account and inventory totals","world map initialization","above-vanilla enchantments","live reversible villager costs and discounts","admin permission denial","bounded Unicode packets","command registration"));
            Files.writeString(Path.of(System.getProperty("guishop.releaseReport")),AdminDocument.JSON.toJson(report));System.out.println("[Release smoke] PASS: "+trades+" purchase/sale pairs; "+variants+" variants");
        }finally{GuiShop.CONFIG=original;player.getInventory().clearContent();TradeLoopProtection.clear();}
    }
    private static final class PermissionChecks {
        static void verify(ServerPlayer player){
            me.lucko.fabric.api.permissions.v0.PermissionCheckEvent.EVENT.register((source,node)->node.equals("guishop.release_allow")?net.fabricmc.fabric.api.util.TriState.TRUE:node.equals("guishop.release_deny")?net.fabricmc.fabric.api.util.TriState.FALSE:net.fabricmc.fabric.api.util.TriState.DEFAULT);
            check(ShopPermissions.check(player,"guishop.release_allow",4),"Explicit provider grant overrides fallback");
            check(!ShopPermissions.check(player,"guishop.release_deny",0),"Explicit provider denial overrides fallback");
        }
    }

}
