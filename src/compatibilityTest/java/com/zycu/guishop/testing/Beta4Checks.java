package com.zycu.guishop.testing;

import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.PotionContents;
import java.util.*;

/** Real stack purchases and native menu navigation, plus optional real provider rules. */
public final class Beta4Checks {
    private Beta4Checks() {}
    public static void run(ServerPlayer viewer) throws Exception {
        var original=GuiShop.CONFIG;var draft=AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(original),ShopConfig.class);
        String unchanged=AdminDocument.JSON.toJson(original);double balance=GuiShop.ECONOMY.readBalance(viewer.getUUID());
        try {
            var plan=ShopPresets.generate(viewer,new ShopPresets.Request("normal",.30));
            var potions=plan.categories().stream().filter(c->c.id.equals("potions")).findFirst().orElseThrow();
            check(!potions.items.isEmpty()&&potions.items.stream().allMatch(i->ListingGroups.potion(i.item)),"Dedicated potion preset category");
            check(plan.categories().stream().filter(c->!c.id.equals("potions")).flatMap(c->c.items.stream()).noneMatch(i->ListingGroups.potion(i.item)),"Potion listings appear exactly once");
            var groups=ListingGroups.groups(potions.items,viewer.registryAccess());var swiftness=groups.get("potion:minecraft:swiftness");
            check(swiftness!=null&&swiftness.size()==9,"Regular, extended and strong potions across three delivery forms share a family");
            check(swiftness.stream().filter(i->i.name.contains("Extended")).count()==3&&swiftness.stream().filter(i->i.name.contains("Strong")).count()==3,"Variant names distinguish duration and strength without changing stack data");
            draft.categories=new ArrayList<>(List.of(potions));GuiShop.CONFIG=draft;GuiShop.ECONOMY.updateConfig(draft);GuiShop.ECONOMY.setBalance(viewer.getUUID(),100000);viewer.getInventory().clearContent();
            HybridShopGui.openCategory(viewer,potions.id,ShopGui.Mode.BUY,1);
            int slot=new ArrayList<>(groups.keySet()).indexOf("potion:minecraft:swiftness");
            check(viewer.containerMenu.getSlot(slot).getItem().getHoverName().getString().equals(ListingGroups.name("potion:minecraft:swiftness",viewer.registryAccess())),"Vanilla client sees native potion family icon");
            viewer.containerMenu.clicked(slot,0,net.minecraft.world.inventory.ContainerInput.PICKUP,viewer);
            for(int i=0;i<9;i++){
                var listing=swiftness.get(i);var exact=listing.createStack(viewer.registryAccess());
                var contents=exact.get(DataComponents.POTION_CONTENTS);
                check(contents!=null&&contents.potion().isPresent(),"Registered potion data preserved in listing");
                check(ShopService.trade(viewer,listing,ShopGui.Mode.BUY,1),"Potion variant purchase");
                check(ShopService.count(viewer,exact)==1,"Exact potion form, effects, strength and duration delivered");
            }
            viewer.closeContainer();
            var combat=plan.categories().stream().filter(c->c.id.equals("combat")).findFirst().orElseThrow();draft.categories.add(combat);
            var arrowGroups=ListingGroups.groups(combat.items,viewer.registryAccess());
            check(arrowGroups.containsKey("arrows"),"Combat contains a Tipped Arrows folder");
            var arrowFamilies=ListingGroups.groups(combat.items,viewer.registryAccess(),"arrows");
            var arrowLevels=ListingGroups.leaves(combat.items,viewer.registryAccess(),"arrow:minecraft:swiftness");
            check(arrowLevels.size()==3&&arrowFamilies.containsKey("arrow:minecraft:swiftness"),"Arrow family contains base, extended and strong levels");
            HybridShopGui.openCategory(viewer,combat.id,ShopGui.Mode.BUY,1);
            var ordered=ListingGroups.entries(combat.items.stream().filter(i->i.buy>0||i.sell>0).toList(),viewer.registryAccess(),null);
            int arrowIndex=java.util.stream.IntStream.range(0,ordered.size()).filter(i->"arrows".equals(ordered.get(i).group())).findFirst().orElseThrow();
            HybridShopGui.openCategory(viewer,combat.id,ShopGui.Mode.BUY,arrowIndex/45+1);
            viewer.containerMenu.clicked(arrowIndex%45,0,net.minecraft.world.inventory.ContainerInput.PICKUP,viewer);
            check(viewer.containerMenu.getSlot(0).getItem().getHoverName().getString().startsWith("Arrow"),"Arrow root opens family choices");
            int swift=new ArrayList<>(arrowFamilies.keySet()).indexOf("arrow:minecraft:swiftness");viewer.containerMenu.clicked(swift,0,net.minecraft.world.inventory.ContainerInput.PICKUP,viewer);
            for(var listing:arrowLevels){var exact=listing.createStack(viewer.registryAccess());check(ShopService.trade(viewer,listing,ShopGui.Mode.BUY,1)&&ShopService.count(viewer,exact)==1,"Exact tipped arrow level delivered");}
            viewer.containerMenu.clicked(45,0,net.minecraft.world.inventory.ContainerInput.PICKUP,viewer);
            check(viewer.containerMenu.getSlot(0).getItem().getHoverName().getString().startsWith("Arrow"),"Back returns to arrow family choices");viewer.closeContainer();
            // Registered non-vanilla potion holders and custom effects use exact ordinary listings too.
            int moddedPotions=0;for(var holder:viewer.registryAccess().lookupOrThrow(Registries.POTION).listElements().filter(h->!h.key().identifier().getNamespace().equals("minecraft")).toList()){
                var stack=PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,holder);var item=new ShopConfig.ShopItem();item.item="minecraft:potion";item.stack=ItemStackData.encode(stack,viewer.registryAccess());item.listingId="test_potion_"+UUID.randomUUID();item.name=stack.getHoverName().getString();item.buy=10;item.sell=0;potions.items.add(item);
                moddedPotions++;check(ListingGroups.key(item,viewer.registryAccess()).startsWith("potion:"+holder.key().identifier().getNamespace()+":"),"Modded potion remains in its own namespace");
                check(ShopService.trade(viewer,item,ShopGui.Mode.BUY,1)&&ShopService.count(viewer,stack)>0,"Modded registered potion retains data after purchase");
            }
            if(Boolean.getBoolean("guishop.moddedPotionsTest"))check(moddedPotions>=3,"Registered test mod potion variants exercised");
            if(Boolean.getBoolean("guishop.moddedPotionsTest"))check(potions.items.stream().filter(i->i.item.equals("minecraft:potion")&&i.listingId.startsWith("test_potion_")).map(i->ListingGroups.key(i,viewer.registryAccess())).distinct().count()==1,"Registered mod strength/duration variants share a family");
            check(AdminDocument.JSON.toJson(original).equals(unchanged),"Preset grouping leaves existing manual prices and layout unchanged");

            System.out.println("[Beta 4 regression test] PASS: separate Potions category; family folders; nine swiftness variants; native menu; exact purchased potion components; "+moddedPotions+" registered mod potion variants; nested Combat tipped-arrow types and levels; exact arrow delivery and Back; existing shop preserved");
        } finally {GuiShop.CONFIG=original;GuiShop.ECONOMY.updateConfig(original);GuiShop.ECONOMY.setBalance(viewer.getUUID(),balance);viewer.getInventory().clearContent();viewer.closeContainer();}
    }
    public static void permissions(ServerPlayer viewer) throws Exception {
        var original=GuiShop.CONFIG;var config=AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(original),ShopConfig.class);
        try {GuiShop.CONFIG=config;LuckPermsChecks.run(viewer,config);}
        finally {GuiShop.CONFIG=original;}
    }
    private static final class LuckPermsChecks {
        static void run(ServerPlayer viewer,ShopConfig config) throws Exception {
            var api=net.luckperms.api.LuckPermsProvider.get();
            var subject=viewer;
            var user=api.getUserManager().loadUser(subject.getUUID(),subject.getGameProfile().name()).join();
            var denied=net.luckperms.api.node.Node.builder("guishop.buy").value(false).build();
            var granted=net.luckperms.api.node.Node.builder(ShopPermissions.ADMIN).value(true).build();
            try {
                check(!AdminEditorService.allowed(subject),"Unassigned non-OP admin node denied");
                user.data().add(denied);
                check(!ShopPermissions.user(subject,"guishop.buy"),"LuckPerms explicit deny overrides Everyone default");
                user.data().remove(denied);config.permissionDefaults.put("guishop.buy",4);
                check(!ShopPermissions.user(subject,"guishop.buy"),"Unassigned node respects restricted fallback");
                var buy=net.luckperms.api.node.Node.builder("guishop.buy").value(true).build();user.data().add(buy);
                check(ShopPermissions.user(subject,"guishop.buy"),"LuckPerms grant overrides Owner-only fallback");user.data().remove(buy);
                user.data().add(granted);
                check(AdminEditorService.allowed(subject),"Trusted non-OP admin grant honored");
                AdminEditorService.snapshot(subject);
                double starting=GuiShop.ECONOMY.balance(subject.getUUID());
                AccountOperations.change(subject,new AccountOperations.Change(subject.getUUID().toString(),starting,starting+1,config.startingBalance,false));
                check(GuiShop.ECONOMY.readBalance(subject.getUUID())==starting+1,"Delegated admin can edit balances through the same node");
                AccountOperations.change(subject,new AccountOperations.Change(subject.getUUID().toString(),starting+1,starting,config.startingBalance,false)); // Same guard used by writes, accounts and asynchronous reads.
                user.data().remove(granted);
                check(!AdminEditorService.allowed(subject),"Admin grant removal takes effect immediately");
                try{AdminEditorService.snapshot(subject);throw new AssertionError("Revoked admin could read editor");}catch(SecurityException expected){}
                try{AccountOperations.change(subject,null);throw new AssertionError("Revoked admin could edit balances");}catch(SecurityException expected){}
                subject.level().getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(subject.nameAndId(),net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER,false));
                user.data().add(net.luckperms.api.node.Node.builder(ShopPermissions.ADMIN).value(false).build());
                config.permissionDefaults.put(ShopPermissions.ADMIN,0);
                check(!AdminEditorService.allowed(subject),"Legacy admin Everyone fallback cannot bypass explicit deny");
                check(!RewardCommand.console(subject.createCommandSourceStack()),"Permission grants do not expose console rewards");
                System.out.println("[LuckPerms integration test] PASS: real Fabric Permissions API; explicit allow/deny; unassigned fallback; non-OP admin delegation; immediate revocation; delegated balance writes; OP deny; console-only reward isolation");
            } finally {subject.level().getServer().getPlayerList().getOps().remove(subject.nameAndId());user.data().clear();api.getUserManager().saveUser(user).join();}
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
