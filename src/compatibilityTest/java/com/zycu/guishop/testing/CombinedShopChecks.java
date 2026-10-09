package com.zycu.guishop.testing;

import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.util.List;

public final class CombinedShopChecks {
    private CombinedShopChecks() {}
    public static void run(ServerPlayer player) throws Exception {
        var original = AdminEditorService.snapshot(player);
        try {
            var draft = original.document().copy(); draft.config.categories.clear();
            var category = new ShopConfig.Category(); category.id="combined_test"; category.name="Combined Test"; category.icon="minecraft:map";
            var map = new ItemStack(Items.MAP);
            map.set(DataComponents.LORE, new ItemLore(List.of(net.minecraft.network.chat.Component.literal("Original map lore"))));
            var plain = new ItemStack(Items.DIAMOND);
            var camp = new ItemStack(Items.ABANDONED_CAMP_MAP);
            category.items.add(listing(map,player,10,2)); category.items.add(listing(plain,player,10,2)); category.items.add(listing(camp,player,10,2));
            if (BuiltInRegistries.ITEM.containsKey(Identifier.parse("travelersbackpack:end"))) category.items.add(listing(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("travelersbackpack:end"))),player,10,2));
            draft.config.categories.add(category);
            var saved=AdminEditorService.apply(player,original.revision(),draft);
            player.getInventory().clearContent();
            HybridShopGui.openCategory(player,category.id,ShopGui.Mode.SELL,1);
            check(player.containerMenu.getSlot(53).getItem().isEmpty(),"No mode switch remains");
            var icon=player.containerMenu.getSlot(0).getItem();
            var lore=icon.getTooltipLines(Item.TooltipContext.of(player.level()),player,TooltipFlag.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList();
            check(lore.stream().anyMatch(line->line.startsWith("Buy: "))&&lore.stream().anyMatch(line->line.startsWith("Sell: ")),"Shop tooltip contains both prices");
            check(!lore.contains("Original map lore"),"Original lore hidden only on the menu copy");
            click(player,0,0,false);
            var bought=player.getInventory().getItem(0);
            check(ItemStackData.same(bought,map),"Empty map purchase retains original item type and lore");
            check(!bought.has(DataComponents.TOOLTIP_DISPLAY)||bought.get(DataComponents.TOOLTIP_DISPLAY).equals(map.get(DataComponents.TOOLTIP_DISPLAY)),"No shop tooltip mask delivered");
            check(category.items.getFirst().stack.equals(saved.document().config.category(category.id).items.getFirst().stack),"Display does not rewrite serialized source templates");
            click(player,0,1,false);check(ShopService.count(player,map)==0,"Right click sells one on the same screen");
            click(player,1,0,true);check(ShopService.count(player,plain)==64,"Shift-left buys 64");
            click(player,1,1,false);check(ShopService.count(player,plain)==63,"Right click sells one rather than the old 16");
            click(player,1,1,true);check(ShopService.count(player,plain)==0,"Shift-right sells all matching");
            click(player,2,0,false);
            var generated=player.getInventory().getItem(0).copy();
            check(generated.is(Items.ABANDONED_CAMP_MAP)&&generated.has(DataComponents.MAP_ID),"Abandoned Camp Map purchase creates map identity");
            check(MapItem.getSavedData(generated,player.level())!=null,"Generated explorer map has real saved world data");
            check(!generated.get(DataComponents.MAP_DECORATIONS).decorations().isEmpty(),"Generated explorer map has a camp destination");
            click(player,2,1,false);check(ShopService.count(player,camp)==0,"Generated map can be sold to its original generic listing");
            var origin = player.level();
            try {
                player.setServerLevel(origin.getServer().getLevel(net.minecraft.world.level.Level.NETHER));
                double balance = GuiShop.ECONOMY.balance(player.getUUID());
                check(!ShopService.trade(player, GuiShop.CONFIG.category(category.id).items.get(2), ShopGui.Mode.BUY, 1)
                    && player.getInventory().isEmpty() && GuiShop.ECONOMY.balance(player.getUUID()) == balance, "Missing camp destination in Nether refuses purchase without charging");
            } finally { player.setServerLevel(origin); }
            var missingMap = new ItemStack(Items.FILLED_MAP);
            missingMap.set(DataComponents.MAP_ID, new net.minecraft.world.level.saveddata.maps.MapId(Integer.MAX_VALUE));
            var missingOffer = listing(missingMap, player, 10, 2);
            GuiShop.CONFIG.category(category.id).items.add(missingOffer);
            try {
                double balance = GuiShop.ECONOMY.balance(player.getUUID());
                check(!ShopService.trade(player, missingOffer, ShopGui.Mode.BUY, 1) && player.getInventory().isEmpty()
                    && GuiShop.ECONOMY.balance(player.getUUID()) == balance, "Missing saved map data refuses purchase without charging");
            } finally { GuiShop.CONFIG.category(category.id).items.remove(missingOffer); }
            if(category.items.size()>3) {
                click(player,3,0,false);
                check(ItemStackData.same(player.getInventory().getItem(0),category.items.get(3).createStack(player.registryAccess())),"Purchased End backpack retains exact native components");
                click(player,3,1,false);check(player.getInventory().getItem(0).isEmpty(),"Right-click sale consumes exact backpack");
            }
            // An exact map with a cheap buy price cannot bypass the generic map sell-price guard.
            var bad=saved.document().copy();var exact=listing(generated,player,1,0);bad.config.categories.getFirst().items.add(exact);
            try {AdminEditorService.validate(bad,player);throw new AssertionError("Generated-map price exploit accepted");} catch(IllegalArgumentException expected) {}
            var cloneBad = saved.document().copy(); cloneBad.config.categories.getFirst().items.getFirst().buy = 1; cloneBad.config.categories.getFirst().items.getFirst().sell = 0;
            try {AdminEditorService.validate(cloneBad,player);throw new AssertionError("Map cloning profit accepted");} catch(IllegalArgumentException expected) {}
            var liveEmptyMap = GuiShop.CONFIG.category(category.id).items.getFirst(); liveEmptyMap.buy = 1; liveEmptyMap.sell = 0;
            player.getInventory().setItem(0, generated.copy()); double cloneBalance = GuiShop.ECONOMY.balance(player.getUUID());
            check(!ShopService.trade(player, GuiShop.CONFIG.category(category.id).items.get(2), ShopGui.Mode.SELL, 1)
                && ItemStackData.same(player.getInventory().getItem(0), generated) && GuiShop.ECONOMY.balance(player.getUUID()) == cloneBalance, "Runtime guard rejects cloning profit without consuming items");
            check(!ShopService.sellHand(player, 1, false) && !ShopService.sellHand(player, 1, true), "Held and inventory sales cannot bypass cloning guard");
            liveEmptyMap.buy = 10; liveEmptyMap.sell = 2; player.getInventory().clearContent();
            // Permission checks belong to each click, not a remembered menu mode.
            var restricted=saved.document().copy();restricted.config.permissionDefaults.put("guishop.sell",4);
            var restrictedSaved=AdminEditorService.apply(player,saved.revision(),restricted);
            HybridShopGui.openCategory(player,category.id,ShopGui.Mode.BUY,1);click(player,1,0,false);click(player,1,1,false);
            check(ShopService.count(player,plain)==1,"Buy-only user cannot sell by right-clicking");
            var sellOnly=restrictedSaved.document().copy();sellOnly.config.permissionDefaults.put("guishop.sell",0);sellOnly.config.permissionDefaults.put("guishop.buy",4);
            AdminEditorService.apply(player,restrictedSaved.revision(),sellOnly);
            HybridShopGui.openCategory(player,category.id,ShopGui.Mode.BUY,1);click(player,1,0,false);check(ShopService.count(player,plain)==1,"Sell-only user cannot buy by left-clicking");
            click(player,1,1,false);check(ShopService.count(player,plain)==0,"Sell-only user can sell without changing modes");
            System.out.println("[Combined shop test] PASS: display-only dual prices; original map lore; exact End backpack; real camp map data and destination; map price guard; left/right and bulk controls; per-side permissions");
        } finally {
            player.closeContainer();player.getInventory().clearContent();
            AdminEditorService.apply(player,AdminEditorService.snapshot(player).revision(),original.document());
        }
    }
    private static ShopConfig.ShopItem listing(ItemStack stack,ServerPlayer player,double buy,double sell) {
        var item=new ShopConfig.ShopItem();item.item=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();item.stack=ItemStackData.encode(stack,player.registryAccess());
        item.listingId=ItemStackData.listingId(stack,player.registryAccess());item.name=stack.getHoverName().getString();item.manualPrice=true;item.buy=buy;item.sell=sell;return item;
    }
    private static void click(ServerPlayer player,int slot,int button,boolean shift) {player.containerMenu.clicked(slot,button,shift?ContainerInput.QUICK_MOVE:ContainerInput.PICKUP,player);}
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
