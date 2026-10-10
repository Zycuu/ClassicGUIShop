package com.zycu.guishop.testing;
import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.nio.file.*;
import java.time.Instant;

/** Beta financial and portability regression checks on the disposable server. */
public final class BetaChecks {
    private BetaChecks() {}
    public static void run(ServerPlayer player) throws Exception {
        var original=GuiShop.CONFIG;double originalBalance=GuiShop.ECONOMY.readBalance(player.getUUID());
        var doc=new AdminDocument();doc.config=original;var config=doc.copy().config;config.categories=new ArrayList<>();
        var category=new ShopConfig.Category();category.id="beta_checks";category.name="Beta checks";category.icon="minecraft:diamond_sword";config.categories.add(category);config.permissionDefaults.put("guishop.category.beta_checks",0);
        config.sellDamagedItems=true;config.sellEnchantedEquipment=true;
        var holder=player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse("minecraft:unbreaking")).orElseThrow();
        var sword=new ItemStack(Items.DIAMOND_SWORD);var base=listing(player,sword,100,30);category.items.add(base);
        var book=EnchantmentShopService.createBook(holder,5);var pricedBook=listing(player,book,50,15);category.items.add(pricedBook);
        try {
            GuiShop.CONFIG=config;GuiShop.ECONOMY.updateConfig(config);player.getInventory().clearContent();
            var damaged=sword.copy();damaged.setDamageValue(damaged.getMaxDamage()/2);damaged.enchant(holder,5);
            var quote=SaleValuation.quote(player,damaged);
            check(quote.base()==30&&quote.enchantments()==15&&quote.unit()==SaleValuation.floor(45*(damaged.getMaxDamage()-damaged.getDamageValue())/(double)damaged.getMaxDamage()),"Exact level V bonus and one durability discount");
            player.getInventory().setItem(0,damaged.copy());double before=GuiShop.ECONOMY.readBalance(player.getUUID());
            check(ShopService.showWorth(player,false)&&ShopService.sellHand(player,1,false)&&player.getMainHandItem().isEmpty()&&Math.abs(GuiShop.ECONOMY.readBalance(player.getUUID())-before-quote.unit())<.000001,"Worth and sellhand use same quote and remove actual damaged variant");
            var exact=listing(player,damaged,40,7);category.items.add(exact);
            check(SaleValuation.quote(player,damaged).unit()==7&&SaleValuation.quote(player,damaged).exact(),"Explicit damaged/enchanted listing wins without double counting");exact.sell=0;
            check(SaleValuation.quote(player,damaged).unit()==0,"Exact disabled variant cannot fall through to generic bonus");category.items.remove(exact);
            config.sellEnchantedEquipment=false;check(SaleValuation.quote(player,damaged).unit()==0,"Enchanted selling toggle enforced");config.sellEnchantedEquipment=true;
            var named=damaged.copy();named.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Unique quest sword"));check(SaleValuation.quote(player,named).unit()==0,"Generic matching preserves names and custom components");
            check(!TransactionJournal.variant(book,player.registryAccess()).equals(TransactionJournal.variant(EnchantmentShopService.createBook(holder,4),player.registryAccess())),"Audit keeps book levels distinct");
            var potionRegistry=player.registryAccess().lookupOrThrow(Registries.POTION);var potionOne=net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,potionRegistry.get(Identifier.parse("minecraft:healing")).orElseThrow());var potionTwo=net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,potionRegistry.get(Identifier.parse("minecraft:strong_healing")).orElseThrow());check(!TransactionJournal.variant(potionOne,player.registryAccess()).equals(TransactionJournal.variant(potionTwo,player.registryAccess())),"Audit keeps potion strength variants distinct");
            pricedBook.sell=0;check(SaleValuation.quote(player,damaged).enchantments()==0&&SaleValuation.quote(player,damaged).note().contains("Unpriced"),"Unpriced enchantment contributes zero and is explained");pricedBook.sell=15;
            config.permissionDefaults.put("guishop.category.beta_checks",4);check(SaleValuation.quote(player,damaged).unit()==0,"Quotes do not expose inaccessible item valuations");config.permissionDefaults.put("guishop.category.beta_checks",0);
            var modItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream().filter(id->!id.getNamespace().equals("minecraft")).map(id->new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id))).filter(ItemStack::isDamageableItem).findFirst();
            if(modItem.isPresent()){var mod=modItem.get();category.items.add(listing(player,mod,100,30));var wear=mod.copy();wear.setDamageValue(1);check(SaleValuation.quote(player,wear).unit()>0,"Registered modded durability uses same generic valuation");}
            var test=new ShopConfig.ShopItem();test.name="Bulk fixture";test.buy=100;test.sell=30;
            var changes=BulkPricing.preview(List.of(test),25,0,30.0);check(test.buy==100&&changes.getFirst().buy()==125&&changes.getFirst().sell()==37.5,"Bulk review does not mutate prices");BulkPricing.apply(changes);check(test.buy==125&&test.sell==37.5,"Bulk changes are written once");
            doc.config=config;var bundle=PortablePresets.export(player,"BetaRoundtrip",doc);check(bundle.schema()==1&&bundle.categories().size()==1&&!AdminDocument.JSON.toJson(bundle).contains("startingBalance"),"Portable export excludes server/account settings");
            var reviewed=PortablePresets.review(player,bundle);check(reviewed.unavailable()==0&&reviewed.bundle().categories().getFirst().items.get(1).stack.equals(pricedBook.stack),"Portable exact level V round trip");
            var missing=AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(bundle),PortablePresets.Bundle.class);var absent=new ShopConfig.ShopItem();absent.item="missing_beta_mod:relic";absent.listingId=absent.item;absent.name="Missing relic";absent.buy=100;absent.sell=30;absent.stack=com.google.gson.JsonParser.parseString("{\"id\":\"missing_beta_mod:relic\",\"count\":1}");missing.categories().getFirst().items.add(absent);
            var unavailable=PortablePresets.review(player,missing);var retained=unavailable.bundle().categories().getFirst().items.getLast();check(unavailable.unavailable()==1&&retained.buy==0&&retained.sell==0&&retained.stack.equals(absent.stack),"Missing content retained exactly and disabled");
            try{PortablePresets.review(player,new PortablePresets.Bundle(999,"bad","26.3",List.of(),List.of()));throw new AssertionError("Unsupported preset accepted");}catch(IllegalArgumentException expected){}
            config.showOperatorBalances=false;check(!PublicBalances.visible(player.level().getServer(),player.getUUID()),"Offline-capable operator UUID privacy");config.showOperatorBalances=true;check(PublicBalances.visible(player.level().getServer(),player.getUUID()),"Public operator option applies");
            var minutes=new ShopModesConfig();minutes.passiveSeconds=90;minutes.mobCooldownSeconds=5;minutes.migrateMinutes();check(minutes.passiveMinutes==1.5&&minutes.passiveInterval()==90&&minutes.mobCooldown()==5,"Legacy seconds convert exactly to fractional minutes");minutes.passivePrefix="Tea Time";minutes.validate();check(minutes.prefix("passive").equals("Tea Time"),"Editable mode prefix validation");
            var friendly=new TransactionJournal.Entry(Instant.now().toString(),player.getName().getString(),player.getUUID().toString(),"MODE_INCOME","mode.passive:stable-id","",0,0,10,10,"",null,0,"Tea Time","Played for 5 minutes",null,null);check(TransactionJournal.action(friendly).equals("Tea Time")&&TransactionJournal.activity(friendly).equals("Played for 5 minutes"),"Journal keeps payout-time mode label and activity");
            check(TransactionJournal.signedTotal(new TransactionJournal.Entry(friendly.time(),friendly.player(),friendly.uuid(),"BUY","x","x",1,10,10,0,""))==-10,"Purchase journal displays debit sign");
            trending(player);
            System.out.println("[ClassicGUIShop Beta test] PASS: shared damage/enchantment valuation; exact disabled priority; custom data and permissions; modded durability; quote/sale parity; bulk draft review; portable exact roundtrip and missing dependencies; operator privacy; minutes migration; custom mode labels; complete Trending and archived drilldown");
        } finally {GuiShop.CONFIG=original;GuiShop.ECONOMY.updateConfig(original);GuiShop.ECONOMY.setBalance(player.getUUID(),Math.max(0,originalBalance));player.getInventory().clearContent();}
    }
    private static ShopConfig.ShopItem listing(ServerPlayer player,ItemStack stack,double buy,double sell){var item=new ShopConfig.ShopItem();item.item=ShopService.itemId(stack);item.listingId=ItemStackData.listingId(stack,player.registryAccess());item.stack=ItemStackData.encode(stack,player.registryAccess());item.name=stack.getHoverName().getString();item.buy=buy;item.sell=sell;item.manualPrice=true;return item;}
    private static void trending(ServerPlayer player) throws Exception {
        var dir=TransactionJournal.directory();Files.createDirectories(dir);String now=Instant.now().minusSeconds(2).toString();String key="beta-audit-"+UUID.randomUUID();
        var journal=dir.resolve(java.time.LocalDate.now(java.time.ZoneOffset.UTC)+".jsonl");
        // Pad beyond the recent-history 2 MB tail, then put audit records BEFORE that tail.
        for(int count:List.of(64,32))TransactionJournal.appendOperation(new TransactionJournal.Entry(now,player.getName().getString(),player.getUUID().toString(),"BUY",key+"@"+UUID.randomUUID().toString().replace("-",""),"minecraft:diamond",count,1,count,0,"",key+count,0,null,null,key,"Audit diamonds"));
        for(int i=0;i<400;i++)TransactionJournal.append(new TransactionJournal.Entry(now,player.getName().getString(),player.getUUID().toString(),"AUDIT_PADDING","","",0,0,0,0,"Padding "+"x".repeat(6000)));
        TransactionJournal.appendOperation(new TransactionJournal.Entry(now,player.getName().getString(),player.getUUID().toString(),"SELL",key,"minecraft:diamond",5,1,5,0,"",key+"sold",0,null,null,key,"Audit diamonds"));
        var top=Trending.query(new Trending.Request(1,false,null,null,0,null));check(top.items().size()<=20,"Trending keeps at most twenty ranked variants");for(int i=1;i<top.items().size();i++)check(top.items().get(i-1).quantity()>=top.items().get(i).quantity(),"Trending ranks by purchased units");
        var perItem=Trending.query(new Trending.Request(1,false,key,null,0,top.request().asOf()));check(perItem.players().getFirst().quantity()==96,"Full journal drilldown reads records before the 2 MB recent tail and sums duplicate category listings");
        long generation=AccountOperations.historyGeneration(player.getUUID().toString());
        AccountOperations.change(player,new AccountOperations.Change(player.getUUID().toString(),GuiShop.ECONOMY.readBalance(player.getUUID()),0,GuiShop.CONFIG.startingBalance,true));
        check(AccountOperations.historyGeneration(player.getUUID().toString())==generation+1,"Test reset advances history generation");
        var detail=Trending.query(new Trending.Request(1,false,key,player.getUUID().toString(),0,top.request().asOf()));check(detail.entries().stream().mapToInt(TransactionJournal.Entry::quantity).sum()==96,"Archived audit transactions remain in player drilldown after reset");
        var sold=Trending.query(new Trending.Request(1,true,key,null,0,top.request().asOf()));check(sold.players().getFirst().quantity()==5,"Purchased and sold quantities stay separate");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
