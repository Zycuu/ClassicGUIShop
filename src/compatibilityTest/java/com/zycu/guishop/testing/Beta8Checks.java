package com.zycu.guishop.testing;

import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import java.nio.file.Files;
import java.util.*;

/** Real editor saves while native trading is open, with saved original costs. */
public final class Beta8Checks {
    private Beta8Checks() {}
    public static void run(ServerPlayer viewer) throws Exception {
        var server=viewer.level().getServer();var original=GuiShop.CONFIG;
        var registered=server.getPlayerList().getPlayers().contains(viewer);
        var villagers=new ArrayList<AbstractVillager>();
        try {
            var normal=ShopPresets.generate(viewer,new ShopPresets.Request("normal",.30));
            var hard=ShopPresets.generate(viewer,new ShopPresets.Request("hard",.30));
            var easy=ShopPresets.generate(viewer,new ShopPresets.Request("easy",.30));
            check(price(normal,"minecraft:oak_log")>=.75&&price(normal,"minecraft:birch_log")>=.75,"Normal logs retain worthwhile 30-percent returns");
            check(price(normal,"minecraft:iron_ingot")>=7&&price(normal,"minecraft:gold_ingot")>=25,"Common metal returns remain useful");
            check(price(normal,"minecraft:stick")>=.14,"Sticks are priced as a common crafted item");
            check(price(hard,"minecraft:emerald")>price(normal,"minecraft:emerald")&&price(easy,"minecraft:emerald")>0,"Emerald payouts scale with the preset");
            for(var plan:List.of(normal,hard,easy))for(var category:plan.categories())for(var item:category.items)check(!(item.buy>0)||item.sell>0,"Purchasable default vanilla items remain sellable");
            var snapshot=AdminEditorService.snapshot(viewer);var draft=snapshot.document().copy();draft.config.categories=new ArrayList<>(normal.categories());draft.config.villagerTradeLoopFix=true;
            AdminEditorService.apply(viewer,snapshot.revision(),draft);
            var village=EntityTypes.VILLAGER.create(viewer.level(),EntitySpawnReason.COMMAND);
            var profession=viewer.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(ResourceKey.create(Registries.VILLAGER_PROFESSION,Identifier.withDefaultNamespace("librarian")));
            village.setVillagerData(village.getVillagerData().withProfession(profession).withLevel(4));village.setVillagerXp(140);village.setNoAi(true);village.setPos(4,160,4);
            var offers=new MerchantOffers();var glass=new MerchantOffer(new ItemCost(Items.EMERALD,1),Optional.empty(),new ItemStack(Items.GLASS,4),5,15,3,.05f,2);offers.add(glass);
            var armor=new MerchantOffer(new ItemCost(Items.EMERALD,1),new ItemStack(Items.DIAMOND_CHESTPLATE),12,10,.05f);offers.add(armor);
            var field=AbstractVillager.class.getDeclaredField("offers");field.setAccessible(true);field.set(village,offers);
            check(viewer.level().addFreshEntity(village),"Loaded legacy-like villager added");villagers.add(village);village.getOffers();
            var trader=EntityTypes.WANDERING_TRADER.create(viewer.level(),EntitySpawnReason.COMMAND);trader.setNoAi(true);trader.setPos(6,160,4);var traderOffers=new MerchantOffers();var logs=new MerchantOffer(new ItemCost(Items.EMERALD),new ItemStack(Items.OAK_LOG,8),12,1,0);traderOffers.add(logs);field.set(trader,traderOffers);check(viewer.level().addFreshEntity(trader),"Wandering trader added");villagers.add(trader);trader.getOffers();
            check(glass.getCostA().getCount()==4,"Stock/XP differences do not silently skip a vanilla glass payment");
            check(logs.getCostA().getCount()>1,"Wandering-trader log supply has an actual increased payment");
            check(armor.getCostA().is(Items.EMERALD_BLOCK)&&armor.getCostA().getCount()<=64&&!armor.isOutOfStock(),"Expensive equipment uses a viable emerald-block denomination");
            var encoded=MerchantOffer.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,viewer.registryAccess()),armor).getOrThrow().getAsJsonObject();
            check(encoded.getAsJsonObject("buy").get("id").getAsString().equals("minecraft:emerald")&&encoded.getAsJsonObject("buy").get("count").getAsInt()==1,"Saved native costs stay original even when the displayed currency changes");
            packet(armor,viewer);packet(glass,viewer);
            if(!registered)server.getPlayerList().getPlayers().add(viewer);
            village.setTradingPlayer(viewer);
            viewer.openMenu(new SimpleMenuProvider((id,inventory,player)->new MerchantMenu(id,inventory,village),Component.literal("Trade session fixture")));
            check(viewer.containerMenu instanceof MerchantMenu,"Native trading menu is open");
            var off=AdminEditorService.snapshot(viewer);var offDraft=off.document().copy();offDraft.config.villagerTradeLoopFix=false;
            AdminEditorService.apply(viewer,off.revision(),offDraft);
            check(viewer.containerMenu==viewer.inventoryMenu,"Saving Off closes a stale trading menu during the same session");
            check(glass.getCostA().getCount()==1&&armor.getCostA().is(Items.EMERALD)&&armor.getCostA().getCount()==1&&logs.getCostA().getCount()==1,"Off immediately restores every original payment and denomination");
            check(village.getVillagerXp()==140&&village.getVillagerData().level()==4&&glass.getUses()==5&&glass.getMaxUses()==15,"Save/refresh preserves level, XP and stock");
            var on=AdminEditorService.snapshot(viewer);var onDraft=on.document().copy();onDraft.config.villagerTradeLoopFix=true;
            AdminEditorService.apply(viewer,on.revision(),onDraft);
            check(glass.getCostA().getCount()==4&&logs.getCostA().getCount()>1&&armor.getCostA().is(Items.EMERALD_BLOCK),"Saving On recalculates already-loaded villagers without a restart");
            var report=TradeLoopProtection.lastRefresh(server);check(report!=null&&report.villagers()>=2&&report.changed()>=2&&report.unavailable()==0,"Live refresh reports affected villagers and offers");
            check(!armor.take(new ItemStack(Items.EMERALD,64),ItemStack.EMPTY),"A loose-emerald payment cannot bypass an emerald-block offer");
            var paid=armor.getCostA().copy();var second=armor.getCostB().copy();check(armor.take(paid,second)&&paid.isEmpty()&&second.isEmpty(),"Native take consumes exactly the displayed payment");
            var results=Map.of("liveSaveOnOff",true,"activeTradingMenuClosed",true,"originalCostsRetained",true,"progressRetained",true,"emeraldBlocksOnExpensiveOffers",true,"vanillaPacketParity",true,"loadedVillagers",report.villagers(),"adjustedOffers",report.changed(),"normalLogSell",price(normal,"minecraft:oak_log"),"normalIronSell",price(normal,"minecraft:iron_ingot"));
            Files.writeString(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("guishop/beta8-checks.json"),AdminDocument.JSON.toJson(results));
            System.out.println("[Beta 8 test] PASS: actual editor saves; loaded villagers and traders; active menu closure; native payment; block denominations; packet parity; original costs; retained XP/stock; usable preset resource prices");
        }finally {
            viewer.closeContainer();if(!registered)server.getPlayerList().getPlayers().remove(viewer);
            for(var entity:villagers)entity.discard();
            GuiShop.CONFIG=original;GuiShop.ECONOMY.updateConfig(original);original.saveChecked();AdminEditorService.rememberDiskState();TradeLoopProtection.changed(server);
        }
    }
    private static double price(ShopPresets.Plan plan,String item){return plan.categories().stream().flatMap(c->c.items.stream()).filter(i->i.item.equals(item)).findFirst().orElseThrow().sell;}
    private static void packet(MerchantOffer offer,ServerPlayer viewer){var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),viewer.registryAccess());MerchantOffer.STREAM_CODEC.encode(buffer,offer);var client=MerchantOffer.STREAM_CODEC.decode(buffer);buffer.release();check(ItemStack.isSameItemSameComponents(client.getCostA(),offer.getCostA())&&client.getCostA().getCount()==offer.getCostA().getCount()&&ItemStack.isSameItemSameComponents(client.getCostB(),offer.getCostB())&&client.getCostB().getCount()==offer.getCostB().getCount(),"Unmodified client displays exactly the server's payment");}
    private static void check(boolean condition,String description){if(!condition)throw new AssertionError(description);}
}
