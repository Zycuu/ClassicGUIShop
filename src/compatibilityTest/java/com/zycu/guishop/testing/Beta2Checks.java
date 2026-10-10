package com.zycu.guishop.testing;
import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import java.util.*;
/** Real server regression checks for the Beta 1 feedback. */
public final class Beta2Checks {
 private Beta2Checks(){}
 public static void run(ServerPlayer viewer)throws Exception{
  var server=viewer.level().getServer();var original=GuiShop.CONFIG;var draft=AdminDocument.JSON.fromJson(AdminDocument.JSON.toJson(original),ShopConfig.class);draft.modes=new ShopModesConfig();
  var player=ModesPresetChecks.fake(viewer,"MishapTest");var online=server.getPlayerList().getPlayers();online.add(player);player.level().addNewPlayer(player);GuiShop.PLAYERS.remember(player);
  double before=GuiShop.ECONOMY.readBalance(player.getUUID());
  try{
   GuiShop.CONFIG=draft;GuiShop.ECONOMY.updateConfig(draft);var dispatcher=server.getCommands().getDispatcher();
   check(dispatcher.getRoot().getChild("bal").getChildren().isEmpty()&&dispatcher.getRoot().getChild("baltop")==null,"/bal has no player completion; old baltop removed");
   check(!dispatcher.getRoot().getChild("shopreward").canUse(viewer.createCommandSourceStack()),"Even OP players cannot see or use reward command");
   check(RewardCommand.console(server.createCommandSourceStack())&&RewardCommand.console(new net.minecraft.server.rcon.RconConsoleSource(server).createCommandSourceStack()),"Console and RCON retain reward bridge");
   check(!RewardCommand.console(server.createCommandSourceStack().withSource(net.minecraft.commands.CommandSource.NULL)),"Non-console command sources rejected");
   try{AccountOperations.reward(viewer.createCommandSourceStack(),player.getUUID().toString(),1,"vote","forbidden-"+UUID.randomUUID(),"");throw new AssertionError("OP reward bridge allowed");}catch(SecurityException expected){}
   GuiShop.ECONOMY.setBalance(player.getUUID(),100);online.remove(player);
   check(dispatcher.execute("econ bal MishapTest",server.createCommandSourceStack())==1,"Offline balance lookup resolves remembered player");
   check(PublicBalances.top(server).stream().noneMatch(row->row.uuid().equals(viewer.getUUID())),"Leaderboard honors hidden operators");
   check(dispatcher.execute("econ leaderboard",server.createCommandSourceStack())==1&&PublicBalances.top(server).size()<=5,"Leaderboard command returns at most five players");online.add(player);
   check(!draft.modes.mishapEnabled&&draft.modes.mishapAmount==50,"Death charges default off at 50");
   ShopModes.died(player,player.damageSources().generic());check(GuiShop.ECONOMY.readBalance(player.getUUID())==100,"Disabled death mode leaves balance intact");
   draft.modes.mishapEnabled=true;draft.modes.mishapPrefix="Oops Tax";
   player.setHealth(1);player.die(player.damageSources().generic());
   check(GuiShop.ECONOMY.readBalance(player.getUUID())==50,"Real player death hook charges exactly 50");
   ShopModes.died(player,player.damageSources().generic());check(GuiShop.ECONOMY.readBalance(player.getUUID())==50,"Repeated callback cannot charge death twice");
   var history=TransactionJournal.query("Oops Tax",0,false,player.getUUID().toString());check(history.entries().size()==1&&history.entries().getFirst().total()==-50&&TransactionJournal.action(history.entries().getFirst()).equals("Oops Tax"),"Death charge journal has signed amount and captured mode name");
   GuiShop.ECONOMY.setBalance(player.getUUID(),12);String clamp="clamp-"+UUID.randomUUID();check(AccountOperations.modeCharge(player,clamp,50,false,"Death fixture","Mishap Charge")&&GuiShop.ECONOMY.readBalance(player.getUUID())==0,"Insufficient funds clamp at zero");
   GuiShop.ECONOMY.setBalance(player.getUUID(),100);AccountOperations.initialize();check(!AccountOperations.modeCharge(player,clamp,50,false,"Death fixture","Mishap Charge")&&GuiShop.ECONOMY.readBalance(player.getUUID())==100,"Charge receipt survives reload without recharging");
   GuiShop.ECONOMY.setBalance(player.getUUID(),0);check(AccountOperations.modeCharge(player,"debt-"+UUID.randomUUID(),50,true,"Debt fixture","Mishap Charge")&&GuiShop.ECONOMY.readBalance(player.getUUID())==-50,"Debt is explicitly opt-in");
   var loaded=new EconomyStore(draft);check(loaded.readBalance(player.getUUID())==-50,"Death debt persists on disk");
   // The empty wallet must still consume the event reference, even if money arrives later.
   GuiShop.ECONOMY.setBalance(player.getUUID(),0);String empty="empty-"+UUID.randomUUID();AccountOperations.modeCharge(player,empty,50,false,"Empty fixture","Mishap Charge");GuiShop.ECONOMY.setBalance(player.getUUID(),100);check(!AccountOperations.modeCharge(player,empty,50,false,"Empty fixture","Mishap Charge"),"Empty-wallet death also has replay protection");
   var holder=viewer.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(net.minecraft.resources.Identifier.withDefaultNamespace("unbreaking")).orElseThrow();
   var cat=new ShopConfig.Category();cat.id="beta2_books";cat.name="Book test";
   for(int level:List.of(1,5)){var stack=EnchantmentShopService.createBook(holder,level);var item=new ShopConfig.ShopItem();item.item="minecraft:enchanted_book";item.name="Unbreaking "+level;item.listingId="beta2_book_"+level;item.stack=ItemStackData.encode(stack,viewer.registryAccess());item.buy=10;item.sell=3;cat.items.add(item);}draft.categories=new ArrayList<>(List.of(cat));
   check(BookGroups.groups(cat.items,viewer.registryAccess()).size()==1,"All exact levels share one enchantment folder");HybridShopGui.openCategory(viewer,cat.id,ShopGui.Mode.BUY,1);
   check(viewer.containerMenu.getSlot(0).getItem().getHoverName().getString().equals("Unbreaking")&&viewer.containerMenu.getSlot(1).getItem().isEmpty(),"Vanilla category shows a folder rather than a flat book list");
   viewer.containerMenu.clicked(0,0,net.minecraft.world.inventory.ContainerInput.PICKUP,viewer);check(viewer.containerMenu.getSlot(0).getItem().get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS).getLevel(holder)==1&&viewer.containerMenu.getSlot(1).getItem().get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS).getLevel(holder)==5,"Folder includes exact above-vanilla level V");viewer.closeContainer();
   var plan=ShopPresets.generate(viewer,new ShopPresets.Request("normal",.40,.25,.15));var book=plan.categories().stream().flatMap(c->c.items.stream()).filter(i->i.item.equals("minecraft:enchanted_book")&&i.buy>0).findFirst().orElseThrow();check(book.sell==SaleValuation.floor(book.buy*.25),"Default book return affects only newly generated preset");
   check(cat.items.getFirst().buy==10&&cat.items.getFirst().sell==3,"Generating new default prices leaves existing listings intact");
   System.out.println("[Beta 2 regression test] PASS: console-only rewards; offline /econ balances; leaderboard privacy; default-off death mode; real death hook; signed history; zero/debt handling; durable replay; enchantment folders and exact level V; generation-only default returns");
  }finally{GuiShop.CONFIG=original;GuiShop.ECONOMY.updateConfig(original);GuiShop.ECONOMY.setBalance(player.getUUID(),Math.max(0,before));online.remove(player);player.level().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);viewer.closeContainer();}
 }
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
