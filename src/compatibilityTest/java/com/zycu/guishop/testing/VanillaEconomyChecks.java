package com.zycu.guishop.testing;

import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.phys.*;
import java.nio.file.Files;
import java.util.*;

/** Independent native mechanics and complete preset coverage on a disposable server. */
public final class VanillaEconomyChecks {
    private VanillaEconomyChecks() {}
    public static void run(ServerPlayer viewer) throws Exception {
        var access=viewer.registryAccess();var original=GuiShop.CONFIG;String before=AdminDocument.JSON.toJson(original);
        var reports=new LinkedHashMap<String,Object>();
        for(String preset:List.of("normal","hard","easy","basics")){
            var plan=ShopPresets.generate(viewer,new ShopPresets.Request(preset,.30));
            var draft=new ShopConfig();draft.categories=plan.categories();
            var review=VanillaPriceSafety.review(draft,access);var bounds=VanillaPriceSafety.bounds(draft,access);
            check(review.recipes()==2042&&review.trades()==391&&review.rules()>4000,"Complete shipped-data coverage");
            check(review.changes().isEmpty(),"No remaining unsafe sell prices: "+preset);
            var rows=new ArrayList<Map<String,Object>>();int positive=0,disabled=0;
            for(var category:draft.categories)for(var item:category.items){
                var stack=item.createStack(access);String key=VanillaPriceSafety.key(stack);
                check(key!=null||!(item.buy>0||item.sell>0),"Missing tradeable vanilla key: "+item.item+" "+item.name);
                var bound=key==null?null:bounds.get(key);
                check(!(item.sell>0)||bound!=null&&item.sell<=bound.cost()*.95+.000001,"Final sell bound: "+item.name);
                if(item.sell>0)positive++;else if(item.buy>0)disabled++;
                var row=new LinkedHashMap<String,Object>();row.put("category",category.name);row.put("name",item.name);row.put("key",key);row.put("buy",item.buy);row.put("sell",item.sell);
                if(bound!=null){row.put("costBound",bound.cost());row.put("route",bound.route());}rows.add(row);
            }
            check(find(draft,"minecraft:emerald").sell==0,"Discounted merchant multiplication has no cash outlet");
            check(find(draft,"minecraft:diamond").sell>0,"Ordinary diamond mining retains income");
            check(find(draft,"minecraft:ancient_debris").sell>0,"Ordinary debris gathering retains income");
            if(!preset.equals("basics")){
                check(find(draft,"minecraft:glass_bottle").sell==0,"Glass/bottle/emerald loop is buy-only");
                check(bounds.get("minecraft:potion|potion=minecraft:water").cost()<=bounds.get("minecraft:glass_bottle").cost(),"Water fill respects bottle cost");
                var water=draft.categories.stream().flatMap(c->c.items.stream()).filter(i->"minecraft:potion|potion=minecraft:water".equals(VanillaPriceSafety.key(i.createStack(access)))).findFirst().orElseThrow();
                check(water.buy==find(draft,"minecraft:glass_bottle").buy,"Plain water buy is empty-bottle price, independent of translated names");
                check(water.sell==0,"Filled water cannot monetize glass multiplication");
            }
            reports.put(preset,Map.of("items",plan.items(),"sellEnabled",positive,"buyOnly",disabled,"corrected",plan.capped(),"review",review,"listings",rows,"bounds",bounds));
        }
        check(before.equals(AdminDocument.JSON.toJson(original)),"Generating/reviewing all presets does not change live config");
        var unsafe=new ShopConfig();var c=new ShopConfig.Category();c.id="audit_test";c.name="Audit Test";c.icon="minecraft:glass_bottle";unsafe.categories.add(c);
        c.items.add(listing(new ItemStack(Items.GLASS_BOTTLE),"bottle",2,.6,viewer));
        c.items.add(listing(PotionContents.createItemStack(Items.POTION,Potions.WATER),"water",85,25.5,viewer));
        var custom=new ItemStack(Items.POTION);custom.set(DataComponents.CUSTOM_MODEL_DATA,new net.minecraft.world.item.component.CustomModelData(List.of(77f),List.of(),List.of(),List.of()));
        c.items.add(listing(custom,"custom_pack",100,50,viewer));
        var mod=net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet().stream().filter(id->!id.getNamespace().equals("minecraft")).findFirst();
        mod.ifPresent(id->c.items.add(listing(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id)),"modded",100,50,viewer)));
        var review=VanillaPriceSafety.review(unsafe,access);
        check(review.excluded()==(mod.isPresent()?2:1)&&review.changes().stream().anyMatch(v->v.listingId().equals("water")),"Water route found; installed mod and custom-model listings excluded");
        double customSell=unsafe.findItem("custom_pack").item().sell;
        String unsafeBefore=AdminDocument.JSON.toJson(unsafe);VanillaPriceSafety.review(unsafe,access);check(unsafeBefore.equals(AdminDocument.JSON.toJson(unsafe)),"Review is read-only");
        VanillaPriceSafety.apply(unsafe,access);
        check(unsafe.findItem("custom_pack").item().sell==customSell&&(!mod.isPresent()||unsafe.findItem("modded").item().sell==50),"Safe correction preserves modded/custom prices");
        var honey=new ShopConfig();var honeyCategory=new ShopConfig.Category();honeyCategory.id="honey_test";honeyCategory.name="Honey Test";honeyCategory.icon="minecraft:honey_bottle";honey.categories.add(honeyCategory);
        honeyCategory.items.add(listing(new ItemStack(Items.HONEY_BOTTLE),"honey",12,9,viewer));
        honeyCategory.items.add(listing(new ItemStack(Items.HONEY_BLOCK),"honey_block",100,90,viewer));
        honeyCategory.items.add(listing(new ItemStack(Items.GLASS_BOTTLE),"honey_glass",2,.6,viewer));
        honeyCategory.items.add(listing(new ItemStack(Items.SUGAR),"honey_sugar",12,9,viewer));
        var honeyBounds=VanillaPriceSafety.bounds(honey,access);
        check(honeyBounds.get("minecraft:honey_bottle").cost()>0&&honeyBounds.get("minecraft:honey_block").cost()>0,"Reversible honey/container recipes do not invent free honey");
        VanillaPriceSafety.apply(honey,access);check(find(honey,"minecraft:honey_bottle").sell>0,"Honey gathering retains positive income without a genuine merchant route");
        check(find(honey,"minecraft:sugar").sell*3+find(honey,"minecraft:glass_bottle").sell<=honeyBounds.get("minecraft:honey_bottle").cost(),"All sugar and returned bottle payouts fit the same recipe budget");
        nativeMechanics(viewer);
        var runtimeRules=VanillaPriceSafety.class.getDeclaredMethod("rules",net.minecraft.core.RegistryAccess.class);runtimeRules.setAccessible(true);
        reports.put("_runtimeRules",runtimeRules.invoke(null,access));
        Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("guishop/vanilla-economy-test-report.json"),AdminDocument.JSON.toJson(reports));
        System.out.println("[Vanilla economy test] PASS: all four preset listings covered; recipes and merchants; buy-only multiplication outputs; ordinary mining income; component-aware water prices; mod/custom exclusion; read-only review; native crafting, bottle filling, tool actions, brewing, composting, Fortune drops and merchant discounts");
    }
    private static ShopConfig.ShopItem listing(ItemStack stack,String id,double buy,double sell,ServerPlayer viewer){var item=new ShopConfig.ShopItem();item.item=ShopService.itemId(stack);item.listingId=id;item.name=stack.getHoverName().getString();item.stack=ItemStackData.encode(stack,viewer.registryAccess());item.buy=buy;item.sell=sell;item.manualPrice=true;return item;}
    private static ShopConfig.ShopItem find(ShopConfig config,String id){return config.categories.stream().flatMap(c->c.items.stream()).filter(i->i.item.equals(id)).findFirst().orElseThrow();}
    private static void nativeMechanics(ServerPlayer viewer) throws Exception {
        ServerLevel level=viewer.level();var access=viewer.registryAccess();
        var glass=new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));glass.set(0,new ItemStack(Items.GLASS));glass.set(2,new ItemStack(Items.GLASS));glass.set(4,new ItemStack(Items.GLASS));
        var craft=CraftingInput.of(3,3,glass);var recipe=level.recipeAccess().getRecipeFor(RecipeType.CRAFTING,craft,level).orElseThrow().value();
        var bottles=recipe.assemble(craft);check(bottles.is(Items.GLASS_BOTTLE)&&bottles.getCount()==3,"Actual glass recipe: 3 glass makes 3 bottles");
        var village=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);var offers=village.getOffers();offers.clear();
        var glassOffer=new MerchantOffer(new ItemCost(Items.EMERALD,1),new ItemStack(Items.GLASS,4),12,1,.05f);
        var bottleOffer=new MerchantOffer(new ItemCost(Items.GLASS_BOTTLE,9),new ItemStack(Items.EMERALD),12,1,.05f);
        var secondary=new MerchantOffer(new ItemCost(Items.EMERALD,64),Optional.of(new ItemCost(Items.BOOK,1)),new ItemStack(Items.ENCHANTED_BOOK),12,1,.2f);
        offers.add(glassOffer);offers.add(bottleOffer);offers.add(secondary);
        village.getGossips().add(viewer.getUUID(),GossipType.MAJOR_POSITIVE,20);village.getGossips().add(viewer.getUUID(),GossipType.MINOR_POSITIVE,25);village.getGossips().add(viewer.getUUID(),GossipType.TRADING,25);
        viewer.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,100,4));
        try{
            var update=Villager.class.getDeclaredMethod("updateSpecialPrices",Player.class);update.setAccessible(true);update.invoke(village,viewer);
            check(village.getPlayerReputation(viewer)==150,"Actual maximum positive reputation");
            check(glassOffer.getCostA().getCount()==1&&bottleOffer.getCostA().getCount()==1,"Actual merchant discounts enable glass multiplication");
            check(secondary.getCostA().getCount()==1&&secondary.getCostB().getCount()==1,"Secondary book payment is not discounted");
            var payment=new ItemStack(Items.GLASS_BOTTLE,4);check(bottleOffer.take(payment,ItemStack.EMPTY)&&payment.getCount()==3,"Actual offer consumes one bottle, pays one emerald");
        }finally{viewer.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);}
        BlockPos pos=new BlockPos(17,160,17);var old=level.getBlockState(pos);var above=level.getBlockState(pos.above());var held=viewer.getMainHandItem().copy();
        try{
            level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
            level.setBlockAndUpdate(pos,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,3));
            viewer.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE));
            level.getBlockState(pos).useItemOn(viewer.getMainHandItem(),level,viewer,InteractionHand.MAIN_HAND,hit);
            check(viewer.getMainHandItem().is(Items.POTION)&&viewer.getMainHandItem().get(DataComponents.POTION_CONTENTS).is(Potions.WATER),"Native bottle filling produces exact water potion");
            level.setBlockAndUpdate(pos,Blocks.COARSE_DIRT.defaultBlockState());viewer.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_HOE));
            viewer.getMainHandItem().useOn(new UseOnContext(viewer,InteractionHand.MAIN_HAND,hit));check(level.getBlockState(pos).is(Blocks.DIRT),"Native hoe converts coarse dirt to dirt");
            level.setBlockAndUpdate(pos,Blocks.OAK_LOG.defaultBlockState());viewer.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_AXE));
            viewer.getMainHandItem().useOn(new UseOnContext(viewer,InteractionHand.MAIN_HAND,hit));check(level.getBlockState(pos).is(Blocks.STRIPPED_OAK_LOG),"Native axe strips log");
            level.setBlockAndUpdate(pos,Blocks.BREWING_STAND.defaultBlockState());var stand=(BrewingStandBlockEntity)level.getBlockEntity(pos);
            for(int i=0;i<3;i++)stand.setItem(i,PotionContents.createItemStack(Items.POTION,Potions.WATER));stand.setItem(3,new ItemStack(Items.NETHER_WART));
            var brew=BrewingStandBlockEntity.class.getDeclaredMethod("doBrew",ServerLevel.class,BlockPos.class,BrewingStandBlockEntity.class);brew.setAccessible(true);brew.invoke(null,level,pos,stand);
            check(stand.getItem(3).isEmpty(),"One native brew reagent consumed");for(int i=0;i<3;i++)check(stand.getItem(i).get(DataComponents.POTION_CONTENTS).is(Potions.AWKWARD),"Three native awkward potions produced");
            for(int i=0;i<3;i++)stand.setItem(i,PotionContents.createItemStack(Items.SPLASH_POTION,Potions.SWIFTNESS));stand.setItem(3,new ItemStack(Items.DRAGON_BREATH));brew.invoke(null,level,pos,stand);
            check(stand.getItem(3).is(Items.GLASS_BOTTLE),"Native dragon breath brewing returns a bottle");for(int i=0;i<3;i++)check(stand.getItem(i).is(Items.LINGERING_POTION)&&stand.getItem(i).get(DataComponents.POTION_CONTENTS).is(Potions.SWIFTNESS),"One dragon breath produces three exact lingering potions");
            var honeyCraft=CraftingInput.of(1,1,List.of(new ItemStack(Items.HONEY_BOTTLE)));var honeyRecipe=level.recipeAccess().getRecipeFor(RecipeType.CRAFTING,honeyCraft,level).orElseThrow().value();
            check(honeyRecipe.assemble(honeyCraft).is(Items.SUGAR)&&honeyRecipe.assemble(honeyCraft).getCount()==3&&honeyRecipe.getRemainingItems(honeyCraft).getFirst().is(Items.GLASS_BOTTLE),"Native honey yields three sugar and the retained bottle");
            var composter=Blocks.COMPOSTER.defaultBlockState();level.setBlockAndUpdate(pos,composter);
            for(int i=0;i<7;i++)composter=ComposterBlock.insertItem(viewer,composter,level,new ItemStack(Items.CAKE),pos);
            check(composter.getValue(ComposterBlock.LEVEL)==7,"Seven actual successful compost inputs required");
            var fortune=new ItemStack(Items.DIAMOND_PICKAXE);fortune.enchant(access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,Identifier.parse("minecraft:fortune"))),3);
            int maximum=0;for(int i=0;i<128;i++)maximum=Math.max(maximum,Block.getDrops(Blocks.DIAMOND_ORE.defaultBlockState(),level,pos,null,viewer,fortune).stream().filter(s->s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum());
            check(maximum==4,"Actual Fortune III diamond ore can yield four diamonds");
        }finally{level.setBlockAndUpdate(pos,old);level.setBlockAndUpdate(pos.above(),above);viewer.setItemInHand(InteractionHand.MAIN_HAND,held);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
