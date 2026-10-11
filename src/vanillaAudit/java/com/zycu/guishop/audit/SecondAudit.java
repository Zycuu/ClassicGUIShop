package com.zycu.guishop.audit;

import com.zycu.guishop.*;
import com.zycu.guishop.admin.*;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Separate audit-only addon, never part of the player/server release mod. */
public final class SecondAudit implements ModInitializer {
    record Slot(List<String> choices,int count) {}
    record Action(String source,String kind,List<Slot> inputs,Map<String,Integer> outputs) {}
    private final List<Action> actions=new ArrayList<>();
    private final List<Map<String,Object>> coverage=new ArrayList<>();
    private final Map<String,Object> details=new LinkedHashMap<>();
    private final Map<String,ItemStack> variantStacks=new TreeMap<>();
    private final Map<String,ShopConfig> presetConfigs=new LinkedHashMap<>();
    private final Map<String,List<Action>> merchantActions=new LinkedHashMap<>();
    private final Map<String,Object> merchantChecks=new LinkedHashMap<>();
    private final Map<String,List<ItemStack>> demoInputs=new LinkedHashMap<>(),demoOutputs=new LinkedHashMap<>();
    private ServerPlayer player;private ServerLevel level;private MinecraftServer server;
    private final BlockPos pos=new BlockPos(17,160,17);
    @Override public void onInitialize(){if(!Boolean.getBoolean("guishop.disposableNativeAudit"))throw new IllegalStateException("Audit addon requires an explicitly disposable fixture");ServerLifecycleEvents.SERVER_STARTED.register(s->{try{run(s);}catch(Throwable error){System.err.println("[Second audit] FAIL");error.printStackTrace();}});}
    private void run(MinecraftServer s) throws Exception {
        if(System.getProperty("guishop.tradeBlueprintsOutput")!=null){
            var blueprints=new TreeMap<String,com.google.gson.JsonElement>();
            var ops=RegistryOps.create(JsonOps.INSTANCE,s.registryAccess());
            for(var holder:s.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).listElements().toList())
                if(holder.key().identifier().getNamespace().equals("minecraft"))blueprints.put(holder.key().identifier().getPath(),VillagerTrade.CODEC.encodeStart(ops,holder.value()).getOrThrow());
            Files.writeString(Path.of(System.getProperty("guishop.tradeBlueprintsOutput")),AdminDocument.JSON.toJson(blueprints));
            System.out.println("[Second audit] PASS: native vanilla trade blueprint fingerprints exported: "+blueprints.size());return;
        }
        server=s;level=s.overworld();player=new ServerPlayer(s,level,new GameProfile(UUID.randomUUID(),"AuditOnly"),ClientInformation.createDefault());
        player.connection=new ServerGamePacketListenerImpl(s,new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false));
        s.getPlayerList().getOps().add(new ServerOpListEntry(player.nameAndId(),LevelBasedPermissionSet.GAMEMASTER,false));
        var prices=new LinkedHashMap<String,Object>();
        boolean originalProtection=GuiShop.CONFIG.villagerTradeLoopFix;
        if(Boolean.getBoolean("guishop.auditNativeTrades"))GuiShop.CONFIG.villagerTradeLoopFix=false;
        try{
            for(String name:List.of("normal","hard","easy","basics")){
                var plan=ShopPresets.generate(player,new ShopPresets.Request(name,.30));var rows=new ArrayList<Map<String,Object>>();
                for(var cat:plan.categories())for(var item:cat.items){var st=item.createStack(s.registryAccess());rows.add(Map.of("key",key(st),"name",item.name,"item",item.item,"buy",item.buy,"sell",item.sell));}
                prices.put(name,rows);
                var cfg=new ShopConfig();cfg.villagerTradeLoopFix=GuiShop.CONFIG.villagerTradeLoopFix;cfg.categories=new ArrayList<>(plan.categories());presetConfigs.put(name,cfg);
            }
            recipes();System.out.println("[Second audit] Native recipes exported: "+coverage.size()+"; actions "+actions.size());
            var original=GuiShop.CONFIG;
            try{for(var preset:presetConfigs.entrySet()){
                GuiShop.CONFIG=preset.getValue();GuiShop.CONFIG.sellEnchantedEquipment=true;
                int begin=actions.size();trades();merchantActions.put(preset.getKey(),new ArrayList<>(actions.subList(begin,actions.size())));actions.subList(begin,actions.size()).clear();
                merchantChecks.put(preset.getKey(),Map.of("draws",details.get("nativeMerchantDraws"),"uniqueOffers",details.get("uniqueNativeMerchantOffers"),"unpayable",0));
            }}finally{GuiShop.CONFIG=original;}
            System.out.println("[Second audit] Native merchant offers exported for each preset");
            details.put("merchantChecks",merchantChecks);
            interactions();blockDrops();extraWorldActions();bookActions();growthReview();replays();tradeReplays();variantSales(prices);itemInventory();equipmentEnchantments();
            details.put("itemStackLimits",BuiltInRegistries.ITEM.keySet().stream().filter(id->id.getNamespace().equals("minecraft")).collect(java.util.stream.Collectors.toMap(Object::toString,id->new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getMaxStackSize())));
            var report=new LinkedHashMap<String,Object>();report.put("method","native recipe assembly, returned inventory, per-preset actual merchant payments and whole output vectors; no first-audit model imported");report.put("minecraft","26.3");report.put("villagerTradeLoopFix",GuiShop.CONFIG.villagerTradeLoopFix);report.put("prices",prices);report.put("actions",actions);report.put("merchantActions",merchantActions);report.put("coverage",coverage);report.put("details",details);
            Path output=Path.of(System.getProperty("guishop.secondAuditOutput"));Files.writeString(output,AdminDocument.JSON.toJson(report));
            System.out.println("[Second audit] PASS: native fact export; "+actions.size()+" inventory transitions; "+coverage.size()+" source checks");
        }finally{GuiShop.CONFIG.villagerTradeLoopFix=originalProtection;s.getPlayerList().getOps().remove(player.nameAndId());}
    }
    private String key(ItemStack st){
        if(st.isEmpty())return "minecraft:air";
        String id=BuiltInRegistries.ITEM.getKey(st.getItem()).toString();var p=st.get(DataComponents.POTION_CONTENTS);
        if(p!=null&&p.potion().isPresent())return id+"|potion="+p.potion().get().unwrapKey().orElseThrow().identifier();
        var e=st.get(DataComponents.STORED_ENCHANTMENTS);
        if(e!=null&&e.size()==1){var v=e.entrySet().iterator().next();return id+"|enchantment="+v.getKey().unwrapKey().orElseThrow().identifier()+"@"+v.getIntValue();}
        var applied=st.get(DataComponents.ENCHANTMENTS);
        if(applied!=null&&!applied.isEmpty()){String variant=id+"|applied="+applied.entrySet().stream().map(v->v.getKey().unwrapKey().orElseThrow().identifier()+"@"+v.getIntValue()).sorted().toList();variantStacks.putIfAbsent(variant,st.copyWithCount(1));return variant;}
        if(id.equals("minecraft:decorated_pot")){
            var data=ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),st.copyWithCount(1)).getOrThrow().getAsJsonObject();
            var c=data.getAsJsonObject("components");var decoration=c==null?null:c.get("minecraft:pot_decorations");
            if(decoration!=null&&decoration.toString().contains("pottery_sherd"))return id+"|decorations="+decoration;
        }
        if(!ItemStack.isSameItemSameComponents(st,new ItemStack(st.getItem()))){var encoded=ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),st.copyWithCount(1)).getOrThrow().getAsJsonObject();var c=encoded.get("components");if(c!=null){String variant=id+"|components="+c;variantStacks.putIfAbsent(variant,st.copyWithCount(1));return variant;}}
        return id;
    }
    private List<String> choices(Ingredient i){return i.items().map(h->BuiltInRegistries.ITEM.getKey(h.value()).toString()).sorted().toList();}
    private ItemStack first(Ingredient i){return new ItemStack(i.items().findFirst().orElseThrow().value());}
    private void add(String name,String kind,List<Slot> inputs,Collection<ItemStack> outputs){var out=new TreeMap<String,Integer>();for(var st:outputs)if(!st.isEmpty())out.merge(key(st),st.getCount(),Integer::sum);if(!out.isEmpty())actions.add(new Action(name,kind,inputs,out));}
    private List<Slot> slots(Collection<ItemStack> in){return in.stream().filter(st->!st.isEmpty()).map(st->new Slot(List.of(key(st)),st.getCount())).toList();}
    private void mark(String source,String status){coverage.add(Map.of("source",source,"status",status));}
    private Object field(Object object,String name) throws Exception {for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass()){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(name);}
    private void craft(String name,CraftingRecipe r,List<Optional<Ingredient>> grid,int w,int h){
        var in=grid.stream().map(v->v.map(this::first).orElse(ItemStack.EMPTY)).toList();var ci=CraftingInput.of(w,h,in);
        if(!r.matches(ci,level))throw new AssertionError("Native recipe rejected extracted input: "+name);
        var outs=new ArrayList<ItemStack>();outs.add(r.assemble(ci));outs.addAll(r.getRemainingItems(ci));
        add(name,"recipe",grid.stream().flatMap(Optional::stream).map(i->new Slot(choices(i),1)).toList(),outs);
    }
    private void exactCraft(String name,CraftingRecipe r,List<ItemStack> grid){var ci=CraftingInput.of(3,3,grid);if(!r.matches(ci,level))return;var out=new ArrayList<ItemStack>();out.add(r.assemble(ci));out.addAll(r.getRemainingItems(ci));add(name,"recipe",slots(grid),out);}
    private List<ItemStack> blank(){return new ArrayList<>(Collections.nCopies(9,ItemStack.EMPTY));}
    private void recipes() throws Exception {
        var ops=RegistryOps.create(JsonOps.INSTANCE,server.registryAccess());
        for(var holder:server.getRecipeManager().getRecipes().stream().sorted(Comparator.comparing(h->h.id().identifier().toString())).toList()){
            String name=holder.id().identifier().toString();if(!name.startsWith("minecraft:"))continue;var r=holder.value();int before=actions.size();
            if(r instanceof ShapedRecipe shaped)craft(name,shaped,shaped.getIngredients(),shaped.getWidth(),shaped.getHeight());
            else if(r instanceof ShapelessRecipe shapeless){var ing=shapeless.placementInfo().ingredients().stream().map(Optional::of).toList();craft(name,shapeless,ing,ing.size(),1);}
            else if(r instanceof SingleItemRecipe single){var in=first(single.input());var input=new SingleRecipeInput(in);if(!single.matches(input,level))throw new AssertionError(name);add(name,"recipe",List.of(new Slot(choices(single.input()),1)),List.of(single.assemble(input)));}
            else if(r instanceof BrewingRecipe brew){
                List<ItemStack> ins=potionCandidates(brew.getInput()),reagents=potionCandidates(brew.getReagent());
                level.setBlockAndUpdate(pos,Blocks.BREWING_STAND.defaultBlockState());var stand=(BrewingStandBlockEntity)level.getBlockEntity(pos);
                var method=BrewingStandBlockEntity.class.getDeclaredMethod("doBrew",ServerLevel.class,BlockPos.class,BrewingStandBlockEntity.class);method.setAccessible(true);
                for(var in:ins)for(var reagent:reagents){for(int i=0;i<3;i++)stand.setItem(i,in.copy());stand.setItem(3,reagent.copy());method.invoke(null,level,pos,stand);var out=new ArrayList<ItemStack>();for(int i=0;i<4;i++)out.add(stand.getItem(i).copy());add(name,"brewing",List.of(new Slot(List.of(key(in)),3),new Slot(List.of(key(reagent)),1)),out);}
            }else if(r instanceof SmithingRecipe smith){var t=smith.templateIngredient().map(this::first).orElse(ItemStack.EMPTY);var b=first(smith.baseIngredient());var a=smith.additionIngredient().map(this::first).orElse(ItemStack.EMPTY);var input=new SmithingRecipeInput(t,b,a);if(!smith.matches(input,level))throw new AssertionError(name);var si=new ArrayList<Slot>();smith.templateIngredient().ifPresent(v->si.add(new Slot(choices(v),1)));si.add(new Slot(choices(smith.baseIngredient()),1));smith.additionIngredient().ifPresent(v->si.add(new Slot(choices(v),1)));add(name,"smithing",si,List.of(smith.assemble(input)));}
            else if(r instanceof TransmuteRecipe trans){var json=Recipe.DIRECT_CODEC.encodeStart(ops,r).getOrThrow().getAsJsonObject();var input=Ingredient.CODEC.parse(ops,json.get("input")).getOrThrow();var mat=Ingredient.CODEC.parse(ops,json.get("material")).getOrThrow();for(var choice:input.items().toList())for(int count=1;count<=8;count++){var grid=blank();grid.set(0,new ItemStack(choice.value()));for(int i=1;i<=count;i++)grid.set(i,first(mat));exactCraft(name+"/"+BuiltInRegistries.ITEM.getKey(choice.value())+"/"+count,trans,grid);}}
            else if(r instanceof ImbueRecipe imbue){for(var p:server.registryAccess().lookupOrThrow(Registries.POTION).listElements().toList()){var grid=blank();for(int i=0;i<9;i++)grid.set(i,i==4?PotionContents.createItemStack(Items.LINGERING_POTION,p):new ItemStack(Items.ARROW));exactCraft(name+"/"+p.key().identifier(),imbue,grid);}}
            else if(r instanceof CraftingRecipe craft){var json=Recipe.DIRECT_CODEC.encodeStart(ops,r).getOrThrow().getAsJsonObject();String type=json.get("type").getAsString();var grid=blank();
                if(type.equals("minecraft:crafting_decorated_pot")){grid.set(1,new ItemStack(Items.BRICK));grid.set(3,new ItemStack(Items.BRICK));grid.set(5,new ItemStack(Items.BRICK));grid.set(7,new ItemStack(Items.BRICK));exactCraft(name,craft,grid);}
                else if(type.equals("minecraft:crafting_special_firework_rocket")){grid.set(0,new ItemStack(Items.PAPER));grid.set(1,new ItemStack(Items.GUNPOWDER));exactCraft(name,craft,grid);}
                else if(type.equals("minecraft:crafting_special_firework_star")){grid.set(0,new ItemStack(Items.GUNPOWDER));grid.set(1,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_dye"))));exactCraft(name,craft,grid);}
                else if(type.equals("minecraft:crafting_dye")){var target=Ingredient.CODEC.parse(ops,json.get("target")).getOrThrow();grid.set(0,first(target));grid.set(1,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_dye"))));exactCraft(name,craft,grid);}
                else if(type.equals("minecraft:crafting_special_repairitem")){for(var item:BuiltInRegistries.ITEM){var st=new ItemStack(item);if(!st.isDamageableItem())continue;st.setDamageValue(1);grid=blank();grid.set(0,st.copy());grid.set(1,st.copy());exactCraft(name+"/"+key(st),craft,grid);}}
                else if(type.equals("minecraft:crafting_special_bannerduplicate")){var banner=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(json.get("banner").getAsString())));grid.set(0,banner.copy());var pattern=server.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).listElements().findFirst().orElseThrow();banner.set(DataComponents.BANNER_PATTERNS,new BannerPatternLayers(List.of(new BannerPatternLayers.Layer(pattern,net.minecraft.world.item.DyeColor.RED))));grid.set(1,banner);exactCraft(name,craft,grid);}
                else if(type.equals("minecraft:crafting_special_bookcloning")){var written=new ItemStack(Items.WRITTEN_BOOK);written.set(DataComponents.WRITTEN_BOOK_CONTENT,net.minecraft.world.item.component.WrittenBookContent.EMPTY);grid.set(0,written);grid.set(1,new ItemStack(Items.WRITABLE_BOOK));exactCraft(name,craft,grid);}
                else if(type.equals("minecraft:crafting_special_firework_star_fade")){var star=new ItemStack(Items.FIREWORK_STAR);grid.set(0,star);grid.set(1,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_dye"))));exactCraft(name,craft,grid);}
                else if(type.equals("minecraft:crafting_special_shielddecoration")){grid.set(0,new ItemStack(Items.SHIELD));grid.set(1,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_banner"))));exactCraft(name,craft,grid);}
                else if(type.equals("minecraft:crafting_special_mapextending")){grid=new ArrayList<>(Collections.nCopies(9,new ItemStack(Items.PAPER)));grid.set(4,MapItem.create(level,17,17,(byte)0,true,true));exactCraft(name,craft,grid);}
            }
            mark("recipe/"+name,actions.size()>before?"native assembly and complete remainder vector":"special component transform; no new ordinary-item count, separate coverage review");
        }
    }
    private List<ItemStack> potionCandidates(PotionIngredient i){var list=new ArrayList<ItemStack>();for(var item:i.ingredient().items().toList()){var bare=new ItemStack(item.value());if(i.test(bare))list.add(bare);if(i.potions().isPresent())for(var p:server.registryAccess().lookupOrThrow(Registries.POTION).listElements().toList()){var st=PotionContents.createItemStack(item.value(),p);if(i.test(st))list.add(st);}}return list;}
    private void trades() throws Exception {
        var villager=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);var wanderer=EntityTypes.WANDERING_TRADER.create(level,EntitySpawnReason.COMMAND);
        villager.getGossips().add(player.getUUID(),GossipType.MAJOR_POSITIVE,20);villager.getGossips().add(player.getUUID(),GossipType.MINOR_POSITIVE,25);villager.getGossips().add(player.getUUID(),GossipType.TRADING,25);player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,1000,4));
        var discount=Villager.class.getDeclaredMethod("updateSpecialPrices",Player.class);discount.setAccessible(true);var types=server.registryAccess().lookupOrThrow(Registries.VILLAGER_TYPE).listElements().toList();
        int sampled=0,variantOffers=0;var unique=new HashSet<String>();
        try{for(var h:server.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).listElements().toList()){
            String name=h.key().identifier().toString();if(!name.startsWith("minecraft:"))continue;var def=h.value();boolean wandering=name.contains("wandering_trader/");
            ItemStackTemplate given=(ItemStackTemplate)field(def,"gives");boolean map=BuiltInRegistries.ITEM.getKey(given.create().getItem()).getPath().contains("map");int before=actions.size();
            var merchant=wandering?wanderer:villager;
            for(int attempt=0;attempt<(map?1:given.create().is(Items.ENCHANTED_BOOK)?1200:96);attempt++){
                if(!wandering)villager.setVillagerData(villager.getVillagerData().withType(types.get(attempt%types.size())).withLevel(5));
                var params=new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY,merchant).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED,net.minecraft.util.Unit.INSTANCE).create(LootContextParamSets.VILLAGER_TRADE);
                var context=new LootContext.Builder(params).withOptionalRandomSeed(1900L+attempt).create(Optional.empty());MerchantOffer offer;
                if(map){var wants=(TradeCost)field(def,"wants");var second=(Optional<TradeCost>)field(def,"additionalWants");var reputation=(Holder<net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProvider>)field(def,"reputationDiscount");offer=new MerchantOffer(wants.toItemCost(context,0),second.map(v->v.toItemCost(context,0)),given.create(),4,1,reputation.value().getFloat(context));}
                else offer=def.getOffer(context);
                sampled++;if(offer==null)continue;((ProtectedMerchantOffer)offer).guishop$protection(server);if(!wandering){villager.getOffers().clear();villager.getOffers().add(offer);discount.invoke(villager,player);}
                if(offer.isOutOfStock()||offer.getCostA().getCount()>offer.getCostA().getMaxStackSize()||offer.getCostB().getCount()>offer.getCostB().getMaxStackSize())throw new AssertionError("Fresh native offer exceeds payment capacity: "+name);
                var in=new ArrayList<ItemStack>();in.add(offer.getCostA());if(!offer.getCostB().isEmpty())in.add(offer.getCostB());var out=List.of(offer.getResult());String identity=name+" "+slots(in)+" "+key(out.getFirst())+" "+out.getFirst().getCount();
                if(unique.add(identity)){add(name,map?"merchant-map-conservative":"merchant",slots(in),out);variantOffers++;}
            }
            mark("trade/"+name,actions.size()>before?map?"world-dependent map; native payments and conservative raw output; zero default sells":"native generated offers across biome types with actual discounts":"no offer in sampled contexts; requires coverage review");
        }}finally{player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);}
        details.put("nativeMerchantDraws",sampled);details.put("uniqueNativeMerchantOffers",variantOffers);
    }
    private void interactions() throws Exception {
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
        var silk=new ItemStack(Items.DIAMOND_PICKAXE);silk.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("silk_touch"))),1);
        level.setBlockAndUpdate(pos,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,3));var bottle=new ItemStack(Items.GLASS_BOTTLE);player.setItemInHand(InteractionHand.MAIN_HAND,bottle.copy());level.getBlockState(pos).useItemOn(player.getMainHandItem(),level,player,InteractionHand.MAIN_HAND,hit);add("native/fill-water","interaction",slots(List.of(bottle)),List.of(player.getMainHandItem().copy()));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));var cow=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);player.interactOn(cow,InteractionHand.MAIN_HAND,Vec3.ZERO);add("native/refill-milk","interaction",slots(List.of(new ItemStack(Items.BUCKET))),List.of(player.getMainHandItem().copy()));
        level.setBlockAndUpdate(pos,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,3));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));level.getBlockState(pos).useItemOn(player.getMainHandItem(),level,player,InteractionHand.MAIN_HAND,hit);add("native/fill-water-bucket","interaction",slots(List.of(new ItemStack(Items.BUCKET))),List.of(player.getMainHandItem().copy()));
        var transformerIds=server.registryAccess().lookupOrThrow(Registries.BLOCK_TRANSFORMER).listElements().toList();int toolActions=0;
        for(var bh:BuiltInRegistries.BLOCK.listElements().toList()){
            var block=bh.value();var item=block.asItem();if(item==Items.AIR)continue;var in=new ItemStack(item);
            for(var tool:List.of(Items.DIAMOND_AXE,Items.DIAMOND_HOE,Items.DIAMOND_SHOVEL)){
                level.setBlockAndUpdate(pos,block.defaultBlockState());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));var existingIds=new HashSet<Integer>();for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(4)))existingIds.add(entity.getId());
                player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));var after=level.getBlockState(pos);if(after.getBlock()==block)continue;
                var outs=new ArrayList<ItemStack>();outs.addAll(Block.getDrops(after,level,pos,level.getBlockEntity(pos),player,silk));
                for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(4)))if(!existingIds.contains(entity.getId()))outs.add(entity.getItem().copy());
                add("native/tool/"+BuiltInRegistries.BLOCK.getKey(block)+"/"+BuiltInRegistries.ITEM.getKey(tool),"interaction",slots(List.of(in)),outs);toolActions++;
            }
        }
        details.put("nativeToolTransitions",toolActions);details.put("loadedToolTransformerDefinitions",transformerIds.size());
        // Default bucket payloads are released by the native item and killed with native loot.
        int bucketSources=0;for(var it:BuiltInRegistries.ITEM){if(!(it instanceof MobBucketItem mobBucket))continue;clearSpace();var area=new AABB(pos).inflate(5);var old=new HashSet<Integer>();for(var e:level.getEntitiesOfClass(Mob.class,area))old.add(e.getId());var proto=new ItemStack(it);mobBucket.checkExtraContent(player,level,proto,pos.above());var mobs=level.getEntitiesOfClass(Mob.class,area).stream().filter(e->!old.contains(e.getId())).toList();if(mobs.size()!=1)throw new AssertionError("Native mob bucket release: "+BuiltInRegistries.ITEM.getKey(it));var mob=mobs.getFirst();var unique=new HashSet<String>();for(int seed=0;seed<512;seed++){var lootId=mob.getLootTable();var drops=lootId.isPresent()?entityLoot(mob,lootId.get().identifier().getPath(),seed):List.<ItemStack>of();var out=new ArrayList<>(drops);out.add(new ItemStack(Items.BUCKET));String signature=out.stream().map(st->key(st)+":"+st.getCount()).sorted().toList().toString();if(unique.add(signature))add("native/release/"+BuiltInRegistries.ITEM.getKey(it)+"/"+signature,"interaction",slots(List.of(proto)),out);}mob.discard();bucketSources++;}details.put("nativeMobBucketSources",bucketSources);
    }
    private void blockDrops(){
        var silk=new ItemStack(Items.DIAMOND_PICKAXE);silk.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("silk_touch"))),1);
        var fortune=new ItemStack(Items.DIAMOND_PICKAXE);fortune.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("fortune"))),3);
        int sources=0,samples=0;var seen=new HashSet<String>();
        for(var item:BuiltInRegistries.ITEM){if(!(item instanceof BlockItem bi))continue;var block=bi.getBlock();var st=block.defaultBlockState();level.setBlockAndUpdate(pos,st);var be=level.getBlockEntity(pos);if(be!=null)be.applyComponentsFromItemStack(new ItemStack(item));
            for(var tool:List.of(silk,fortune))for(int attempt=0;attempt<96;attempt++){
                var drops=Block.getDrops(st,level,pos,be,player,tool);samples++;String identity=BuiltInRegistries.ITEM.getKey(item)+" "+drops.stream().map(s->key(s)+":"+s.getCount()).sorted().toList();if(seen.add(identity))add("native/break/"+BuiltInRegistries.ITEM.getKey(item),block instanceof StemBlock||block instanceof CropBlock?"farming-excluded":"block-drop",slots(List.of(new ItemStack(item))),drops);
            }
            sources++;
        }
        details.put("nativeBlockItemSources",sources);details.put("nativeLootSamples",samples);details.put("uniqueLootVectors",seen.size());
    }

    private Item item(String id){return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));}
    private Block block(String id){return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));}
    private void clearSpace(){for(var p:BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,4,2)))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());}
    private List<ItemStack> entityLoot(net.minecraft.world.entity.LivingEntity e,String tableId,long seed){
        var sword=new ItemStack(Items.DIAMOND_SWORD);sword.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("looting"))),3);player.setItemInHand(InteractionHand.MAIN_HAND,sword);
        var params=new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY,e).withParameter(LootContextParams.ORIGIN,e.position()).withParameter(LootContextParams.DAMAGE_SOURCE,level.damageSources().playerAttack(player)).withParameter(LootContextParams.ATTACKING_ENTITY,player).withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY,player).withParameter(LootContextParams.LAST_DAMAGE_PLAYER,player).create(LootContextParamSets.ENTITY);
        return server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,Identifier.withDefaultNamespace(tableId))).getRandomItems(params,seed);
    }
    private void extraWorldActions() throws Exception {
        var silk=new ItemStack(Items.DIAMOND_PICKAXE);silk.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("silk_touch"))),1);
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        var checked=new LinkedHashMap<String,Object>();
        // Dynamic pot contents are harvested together, not treated as separate budgets.
        var pot=new ItemStack(Items.DECORATED_POT);level.setBlockAndUpdate(pos,Blocks.DECORATED_POT.defaultBlockState().setValue(DecoratedPotBlock.CRACKED,true));var be=level.getBlockEntity(pos);be.applyComponentsFromItemStack(pot);add("native/shatter/plain-pot","interaction",slots(List.of(pot)),Block.getDrops(level.getBlockState(pos),level,pos,be,player,new ItemStack(Items.DIAMOND_PICKAXE)));
        checked.put("plainPotShatter",true);
        var potRecipe=(CraftingRecipe)server.getRecipeManager().getRecipes().stream().filter(h->h.id().identifier().equals(Identifier.withDefaultNamespace("decorated_pot"))).findFirst().orElseThrow().value();int sherds=0;for(var h:BuiltInRegistries.ITEM.listElements().toList()){if(!h.key().identifier().getPath().endsWith("_pottery_sherd"))continue;var grid=blank();grid.set(1,new ItemStack(h.value()));grid.set(3,new ItemStack(Items.BRICK));grid.set(5,new ItemStack(Items.BRICK));grid.set(7,new ItemStack(Items.BRICK));exactCraft("native/pot/"+h.key().identifier(),potRecipe,grid);var input=CraftingInput.of(3,3,grid);var made=potRecipe.assemble(input);level.setBlockAndUpdate(pos,Blocks.DECORATED_POT.defaultBlockState().setValue(DecoratedPotBlock.CRACKED,true));var potEntity=level.getBlockEntity(pos);potEntity.applyComponentsFromItemStack(made);var drops=Block.getDrops(level.getBlockState(pos),level,pos,potEntity,player,new ItemStack(Items.DIAMOND_PICKAXE));add("native/shatter/"+h.key().identifier(),"interaction",slots(List.of(made)),drops);if(drops.stream().mapToInt(ItemStack::getCount).sum()!=4)throw new AssertionError("Native pot shatter did not return four pieces");sherds++;}checked.put("nativeSherdPotCycles",sherds);
        // Native consumable remainders, including honey, milk, bowls and potion bottles.
        int consumables=0;for(var it:BuiltInRegistries.ITEM){var st=new ItemStack(it);if(st.get(DataComponents.CONSUMABLE)==null)continue;var remainder=st.copy().finishUsingItem(level,player);if(!remainder.isEmpty())add("native/consume/"+BuiltInRegistries.ITEM.getKey(it),"interaction",slots(List.of(st)),List.of(remainder));consumables++;}checked.put("nativeConsumables",consumables);
        for(var p:server.registryAccess().lookupOrThrow(Registries.POTION).listElements().toList()){var st=PotionContents.createItemStack(Items.POTION,p);var remainder=st.copy().finishUsingItem(level,player);add("native/drink/"+p.key().identifier(),"interaction",slots(List.of(st)),List.of(remainder));}
        var mooshroom=EntityTypes.MOOSHROOM.create(level,EntitySpawnReason.COMMAND);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOWL));player.interactOn(mooshroom,InteractionHand.MAIN_HAND,Vec3.ZERO);add("native/mooshroom/stew","interaction",slots(List.of(new ItemStack(Items.BOWL))),List.of(player.getMainHandItem().copy()));
        // Actual pumpkin carving includes the simultaneous seed drops.
        clearSpace();level.setBlockAndUpdate(pos,Blocks.PUMPKIN.defaultBlockState());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));var before=new HashSet<Integer>();for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(4)))before.add(e.getId());level.getBlockState(pos).useItemOn(player.getMainHandItem(),level,player,InteractionHand.MAIN_HAND,hit);var carved=new ArrayList<>(Block.getDrops(level.getBlockState(pos),level,pos,level.getBlockEntity(pos),player,silk));for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(4)))if(!before.contains(e.getId()))carved.add(e.getItem().copy());add("native/carve-pumpkin","interaction",slots(List.of(new ItemStack(Items.PUMPKIN))),carved);
        // Water use on dirt retains the water bottle as an empty, refillable bottle.
        for(var id:List.of("dirt","coarse_dirt","rooted_dirt")){clearSpace();level.setBlockAndUpdate(pos,block(id).defaultBlockState());var water=PotionContents.createItemStack(Items.POTION,server.registryAccess().lookupOrThrow(Registries.POTION).getOrThrow(ResourceKey.create(Registries.POTION,Identifier.withDefaultNamespace("water"))));player.setItemInHand(InteractionHand.MAIN_HAND,water.copy());player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));var outs=new ArrayList<>(Block.getDrops(level.getBlockState(pos),level,pos,level.getBlockEntity(pos),player,silk));outs.add(player.getMainHandItem().copy());add("native/mud/"+id,"interaction",slots(List.of(new ItemStack(item(id)),water)),outs);}
        // Oxidation uses the native next-stage mapping. Axe actions already cover reversal.
        int weather=0;for(var it:BuiltInRegistries.ITEM){if(!(it instanceof BlockItem bi)||!(bi.getBlock() instanceof WeatheringCopper wc))continue;var next=wc.getNext(bi.getBlock().defaultBlockState());if(next.isEmpty())continue;level.setBlockAndUpdate(pos,next.get());add("native/weather/"+BuiltInRegistries.ITEM.getKey(it),"world-timed",slots(List.of(new ItemStack(it))),Block.getDrops(next.get(),level,pos,level.getBlockEntity(pos),player,silk));weather++;}checked.put("nativeWeatherStages",weather);
        // Composter chance/conditional layers read from native components in each fill state.
        int compost=0;for(var it:BuiltInRegistries.ITEM){var c=it.components().get(DataComponents.COMPOSTABLE);if(c==null)continue;int max=0;for(int fill=0;fill<7;fill++){var state=Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL,fill);var params=new LootParams.Builder(level).withParameter(LootContextParams.BLOCK_STATE,state).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).withParameter(LootContextParams.INTERACTING_ENTITY,player).create(LootContextParamSets.BLOCK_INTERACT);for(int sample=0;sample<96;sample++){var context=new LootContext.Builder(params).withOptionalRandomSeed(sample+4700L).create(Optional.empty());max=Math.max(max,c.layers().get(context,0));}}if(max>0)add("native/compost/"+BuiltInRegistries.ITEM.getKey(it),"interaction",List.of(new Slot(List.of(BuiltInRegistries.ITEM.getKey(it).toString()),(7+max-1)/max)),List.of(new ItemStack(Items.BONE_MEAL)));compost++;}checked.put("nativeCompostableSources",compost);
        // Piglin outcomes are native whole vectors, sampled with fixed seeds.
        var piglin=EntityTypes.PIGLIN.create(level,EntitySpawnReason.COMMAND);var params=new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY,piglin).create(LootContextParamSets.PIGLIN_BARTER);var table=server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,Identifier.withDefaultNamespace("gameplay/piglin_bartering")));var seen=new HashSet<String>();for(long seed=0;seed<8192;seed++){var out=table.getRandomItems(params,seed);String identity=out.stream().map(st->key(st)+":"+st.getCount()).sorted().toList().toString();if(seen.add(identity))add("native/barter/"+identity,"barter",slots(List.of(new ItemStack(Items.GOLD_INGOT))),out);}checked.put("nativeBarterDraws",8192);checked.put("nativeBarterVectors",seen.size());
        // Summoning produces both the chest and the golem. Death and statue outcomes are alternatives.
        int summons=0;for(var copper:BuiltInRegistries.BLOCK.listElements().filter(h->h.is(net.minecraft.tags.BlockTags.COPPER)).map(h->h.key().identifier().getPath()).sorted().toList())for(var head:List.of(Items.CARVED_PUMPKIN,Items.JACK_O_LANTERN)){clearSpace();var existing=new HashSet<Integer>();for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.CopperGolem.class,new AABB(pos).inflate(5)))existing.add(e.getId());level.setBlockAndUpdate(pos,block(copper).defaultBlockState());level.setBlockAndUpdate(pos.above(),((BlockItem)head).getBlock().defaultBlockState());var spawned=level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.CopperGolem.class,new AABB(pos).inflate(5)).stream().filter(e->!existing.contains(e.getId())).toList();if(spawned.size()!=1)throw new AssertionError("Native copper summon: "+copper+" yielded "+spawned.size());var golem=spawned.getFirst();checked.put("spawnWeather/"+copper,golem.getWeatherState().name());String token="audit:entity/copper_golem";var out=new TreeMap<String,Integer>();var chestDrops=Block.getDrops(level.getBlockState(pos),level,pos,level.getBlockEntity(pos),player,silk);for(var st:chestDrops)out.merge(key(st),st.getCount(),Integer::sum);out.put(token,1);actions.add(new Action("native/summon-copper/"+copper+"/"+BuiltInRegistries.ITEM.getKey(head),"interaction",slots(List.of(new ItemStack(item(copper)),new ItemStack(head))),out));var lootSeen=new HashSet<String>();for(int seed=0;seed<512;seed++){var drops=entityLoot(golem,"entities/copper_golem",seed);String signature=drops.stream().map(st->key(st)+":"+st.getCount()).sorted().toList().toString();if(lootSeen.add(signature))add("native/kill-copper/"+signature,"interaction",List.of(new Slot(List.of(token),1)),drops);}golem.setPos(Vec3.atBottomCenterOf(pos.above()));level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());golem.setWeatherState(WeatheringCopper.WeatherState.OXIDIZED);var turn=golem.getClass().getDeclaredMethod("turnToStatue",ServerLevel.class);turn.setAccessible(true);turn.invoke(golem,level);var statue=level.getBlockState(pos.above());if(!(statue.getBlock() instanceof CopperGolemStatueBlock))throw new AssertionError("Native golem did not become statue");player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);int poseClicks=0;while(statue.getValue(CopperGolemStatueBlock.POSE)!=CopperGolemStatueBlock.Pose.STANDING&&poseClicks++<4){statue.useItemOn(ItemStack.EMPTY,level,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos.above()),Direction.UP,pos.above(),false));statue=level.getBlockState(pos.above());}if(statue.getValue(CopperGolemStatueBlock.POSE)!=CopperGolemStatueBlock.Pose.STANDING)throw new AssertionError("Native pose controls failed");var statueDrops=Block.getDrops(statue,level,pos.above(),level.getBlockEntity(pos.above()),player,silk);add("native/copper-oxidation-statue/"+copper,"world-timed",List.of(new Slot(List.of(token),1)),statueDrops);if(copper.equals("oxidized_copper")&&head==Items.CARVED_PUMPKIN){demoInputs.put("native/oxidized-copper-golem",List.of(new ItemStack(item(copper)),new ItemStack(Items.CARVED_PUMPKIN)));var both=new ArrayList<>(chestDrops);both.addAll(statueDrops);demoOutputs.put("native/oxidized-copper-golem",both);}summons++;}checked.put("nativeCopperSummonVariants",summons);
        details.put("additionalWorldChecks",checked);
        int revivals=0;for(var h:BuiltInRegistries.BLOCK.listElements().filter(h->h.value() instanceof CopperGolemStatueBlock).toList()){
            clearSpace();var area=new AABB(pos).inflate(5);var oldIds=new HashSet<Integer>();for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.CopperGolem.class,area))oldIds.add(e.getId());
            level.setBlockAndUpdate(pos,h.value().defaultBlockState());player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_AXE));int clicks=0;
            while(level.getBlockState(pos).getBlock() instanceof CopperGolemStatueBlock&&clicks++<6){level.getBlockState(pos).useItemOn(player.getMainHandItem(),level,player,InteractionHand.MAIN_HAND,hit);if(level.getBlockState(pos).getBlock() instanceof CopperGolemStatueBlock)player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));}
            var revived=level.getEntitiesOfClass(net.minecraft.world.entity.animal.golem.CopperGolem.class,area).stream().filter(e->!oldIds.contains(e.getId())).toList();if(revived.size()!=1)throw new AssertionError("Native statue revival failed: "+h.key().identifier());
            var e=revived.getFirst();var unique=new HashSet<String>();for(int seed=0;seed<512;seed++){var drops=entityLoot(e,"entities/copper_golem",seed);String identity=drops.stream().map(st->key(st)+":"+st.getCount()).sorted().toList().toString();if(unique.add(identity))add("native/revive-kill/"+h.key().identifier()+"/"+identity,"interaction",slots(List.of(new ItemStack(h.value().asItem()))),drops);}e.discard();revivals++;
        }checked.put("nativeStatueRevivalVariants",revivals);
    }

    private void growthReview(){
        var silk=new ItemStack(Items.DIAMOND_PICKAXE);silk.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,Identifier.withDefaultNamespace("silk_touch"))),1);var origin=pos.offset(64,0,0);int sampled=0;
        for(var seed:List.of("crimson_fungus","warped_fungus","red_mushroom","brown_mushroom")){for(var p:BlockPos.betweenClosed(origin.offset(-12,0,-12),origin.offset(12,26,12)))level.setBlock(p,Blocks.AIR.defaultBlockState(),2);var soil=seed.contains("fungus")?block(seed.startsWith("crimson")?"crimson_nylium":"warped_nylium"):Blocks.PODZOL;level.setBlockAndUpdate(origin.below(),soil.defaultBlockState());level.setBlockAndUpdate(origin,block(seed).defaultBlockState());var meal=new ItemStack(Items.BONE_MEAL,64);int attempts=0;while(level.getBlockState(origin).getBlock()==block(seed)&&attempts++<40)BoneMealItem.growCrop(meal,level,origin);if(level.getBlockState(origin).getBlock()==block(seed))throw new AssertionError("Native growth never succeeded: "+seed);var harvest=new ArrayList<ItemStack>();for(var p:BlockPos.betweenClosed(origin.offset(-12,0,-12),origin.offset(12,26,12))){var state=level.getBlockState(p);if(!state.isAir())harvest.addAll(Block.getDrops(state,level,p,level.getBlockEntity(p),player,silk));}var input=List.of(new ItemStack(item(seed)),new ItemStack(Items.BONE_MEAL,64-meal.getCount()));add("native/growth/"+seed,"farming-excluded",slots(input),harvest);demoInputs.put("farming/"+seed,input);demoOutputs.put("farming/"+seed,harvest);sampled++;}details.put("nativeGrowthExamples",sampled);details.put("farmingPolicy","Growth examples are reported separately; ordinary resource farming was explicitly kept in scope as intended income, not automatically blocked.");
    }
    private ItemStack assembleDemo(String name,List<ItemStack> grid){var r=(CraftingRecipe)server.getRecipeManager().getRecipes().stream().filter(h->h.id().identifier().equals(Identifier.withDefaultNamespace(name))).findFirst().orElseThrow().value();var input=CraftingInput.of(3,3,grid);if(!r.matches(input,level))throw new AssertionError("Native demonstration recipe: "+name);return r.assemble(input);}
    private void replays(){
        var grid=blank();grid.set(0,new ItemStack(item("waxed_copper_block")));var ingots=assembleDemo("copper_ingot_from_waxed_copper_block",grid);if(ingots.getCount()!=9)throw new AssertionError("Copper unpack count");grid=blank();grid.set(0,new ItemStack(Items.COPPER_INGOT));grid.set(3,new ItemStack(Items.COPPER_INGOT));grid.set(6,new ItemStack(Items.COPPER_INGOT));var rod=assembleDemo("lightning_rod",grid);var b=((BlockItem)rod.getItem()).getBlock();var next=((WeatheringCopper)b).getNext(b.defaultBlockState()).orElseThrow();level.setBlockAndUpdate(pos,next);var harvested=Block.getDrops(next,level,pos,level.getBlockEntity(pos),player,new ItemStack(Items.DIAMOND_PICKAXE));var rods=new ArrayList<ItemStack>();for(var st:harvested)rods.add(st.copyWithCount(st.getCount()*3));demoInputs.put("native/craft-weather-lightning-rods",List.of(new ItemStack(item("waxed_copper_block"))));demoOutputs.put("native/craft-weather-lightning-rods",rods);
        var original=GuiShop.CONFIG;var comparisons=new LinkedHashMap<String,Object>();try{for(var preset:presetConfigs.entrySet()){GuiShop.CONFIG=preset.getValue();var demos=new ArrayList<Map<String,Object>>();for(var name:demoInputs.keySet()){double buy=0,sell=0;var payments=new ArrayList<Map<String,Object>>();var receipts=new ArrayList<Map<String,Object>>();boolean purchasable=true;for(var st:demoInputs.get(name)){double price=preset.getValue().categories.stream().flatMap(c->c.items.stream()).filter(i->i.buy>0&&ItemStackData.same(i.createStack(server.registryAccess()),st.copyWithCount(1))).mapToDouble(i->i.buy).min().orElse(Double.NaN);if(!Double.isFinite(price))purchasable=false;else buy+=price*st.getCount();payments.add(Map.of("key",key(st),"count",st.getCount(),"unitBuy",Double.isFinite(price)?price:0));}for(var st:demoOutputs.get(name)){var q=SaleValuation.quote(player,st);sell+=q.unit()*st.getCount();if(q.unit()>0)receipts.add(Map.of("key",key(st),"count",st.getCount(),"unitSell",q.unit(),"exact",q.exact()));}demos.add(Map.of("example",name,"purchasable",purchasable,"directBuyCost",buy,"actualSellQuoteTotal",sell,"profit",sell-buy,"inputs",payments,"outputs",receipts));}comparisons.put(preset.getKey(),demos);}}finally{GuiShop.CONFIG=original;}details.put("nativeRecipeAndUseReplays",comparisons);
    }
    private void itemInventory(){var inventory=new ArrayList<Map<String,Object>>();var allKeys=new HashSet<String>();for(var a:actions){allKeys.addAll(a.outputs().keySet());for(var in:a.inputs())allKeys.addAll(in.choices());}for(var it:BuiltInRegistries.ITEM.listElements().toList()){String id=it.key().identifier().toString();if(!id.startsWith("minecraft:"))continue;var val=it.value();var rows=presetConfigs.get("normal").categories.stream().flatMap(c->c.items.stream()).filter(i->i.item.equals(id)).toList();inventory.add(Map.of("item",id,"class",val.getClass().getSimpleName(),"listings",rows.size(),"buysEnabled",rows.stream().anyMatch(i->i.buy>0),"sellsEnabled",rows.stream().anyMatch(i->i.sell>0),"hasTransition",allKeys.stream().anyMatch(k->k.equals(id)||k.startsWith(id+"|")),"isBlockItem",val instanceof BlockItem,"isMobBucket",val instanceof MobBucketItem,"isConsumable",val.components().has(DataComponents.CONSUMABLE),"isCompostable",val.components().has(DataComponents.COMPOSTABLE)));}details.put("vanillaItemInventory",inventory);}
    private void tradeReplays(){
        var original=GuiShop.CONFIG;double oldBalance=GuiShop.ECONOMY.balance(player.getUUID());var report=new ArrayList<Map<String,Object>>();
        try{GuiShop.CONFIG=presetConfigs.get("normal");for(var name:List.of("native/craft-weather-lightning-rods","native/oxidized-copper-golem")){player.getInventory().clearContent();GuiShop.ECONOMY.setBalance(player.getUUID(),1000);for(var st:demoInputs.get(name)){var entry=GuiShop.CONFIG.categories.stream().flatMap(c->c.items.stream()).filter(i->i.buy>0&&ItemStackData.same(i.createStack(server.registryAccess()),st.copyWithCount(1))).min(Comparator.comparingDouble(i->i.buy)).orElseThrow();if(!ShopService.trade(player,entry,ShopGui.Mode.BUY,st.getCount()))throw new AssertionError("Actual shop purchase failed: "+name);}double afterBuying=GuiShop.ECONOMY.balance(player.getUUID());player.getInventory().clearContent();for(var st:demoOutputs.get(name)){player.setItemInHand(InteractionHand.MAIN_HAND,st.copy());if(!ShopService.sellHand(player,ShopService.SELL_ALL,false))throw new AssertionError("Actual shop sale failed: "+name+" "+key(st));}double afterSelling=GuiShop.ECONOMY.balance(player.getUUID());if(Boolean.getBoolean("guishop.expectPatchedPresets")?afterSelling>1000:afterSelling<=1000)throw new AssertionError("Shop replay had unexpected profit status: "+name+" "+afterSelling);report.add(Map.of("example",name,"startBalance",1000,"afterPurchase",afterBuying,"afterNativeOutputsSold",afterSelling,"netProfit",Math.round((afterSelling-1000)*100)/100.0,"execution","Real ShopService purchase and sellhand, with outputs assembled and harvested by native game methods"));}}
        finally{GuiShop.CONFIG=original;GuiShop.ECONOMY.setBalance(player.getUUID(),oldBalance);player.getInventory().clearContent();}details.put("actualShopTransactionReplays",report);
    }
    private void equipmentEnchantments(){
        var supported=new TreeMap<String,List<String>>();
        for(var item:BuiltInRegistries.ITEM){
            var stack=new ItemStack(item);var id=BuiltInRegistries.ITEM.getKey(item);
            if(!id.getNamespace().equals("minecraft")||!stack.isDamageableItem())continue;
            var ids=server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().filter(h->h.key().identifier().getNamespace().equals("minecraft")&&h.value().isSupportedItem(stack)).map(h->h.key().identifier().toString()).sorted().toList();
            supported.put(id.toString(),ids);
        }
        details.put("vanillaEquipmentEnchantments",supported);
    }

    private void bookActions() throws Exception {
        var enchants=server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);int combined=0;
        for(var h:enchants.listElements().toList())for(int lev=1;lev<h.value().getMaxLevel();lev++){
            var book=EnchantmentShopService.createBook(h,lev);var menu=new net.minecraft.world.inventory.AnvilMenu(420,player.getInventory(),net.minecraft.world.inventory.ContainerLevelAccess.create(level,pos));menu.getSlot(0).set(book.copy());menu.getSlot(1).set(book.copy());menu.createResult();var out=menu.getSlot(2).getItem().copy();if(!out.isEmpty()){add("native/anvil/book/"+h.key().identifier()+"/"+lev,"anvil",slots(List.of(book,book)),List.of(out));combined++;}
        }
        var variantList=new ArrayList<>(variantStacks.values());int grind=0;
        for(var st:variantList){var menu=new net.minecraft.world.inventory.GrindstoneMenu(421,player.getInventory(),net.minecraft.world.inventory.ContainerLevelAccess.create(level,pos));menu.getSlot(0).set(st.copy());menu.slotsChanged(player.getInventory());var out=menu.getSlot(2).getItem().copy();if(!out.isEmpty()){add("native/grindstone/"+key(st),"grindstone",slots(List.of(st)),List.of(out));grind++;}}
        details.put("nativeBookCombinations",combined);details.put("nativeEquipmentGrinds",grind);
    }
    @SuppressWarnings("unchecked") private void variantSales(Map<String,Object> prices){
        var original=GuiShop.CONFIG;var variants=new LinkedHashMap<String,Object>();
        try{for(var entry:presetConfigs.entrySet()){GuiShop.CONFIG=entry.getValue();GuiShop.CONFIG.sellEnchantedEquipment=true;var rows=(List<Map<String,Object>>)prices.get(entry.getKey());var quoted=new ArrayList<Map<String,Object>>();for(var entryStack:new ArrayList<>(variantStacks.entrySet())){var q=SaleValuation.quote(player,entryStack.getValue());rows.add(Map.of("key",entryStack.getKey(),"name","Native merchant equipment","item",BuiltInRegistries.ITEM.getKey(entryStack.getValue().getItem()).toString(),"buy",0,"sell",q.unit()));quoted.add(Map.of("key",entryStack.getKey(),"base",q.base(),"bonus",q.enchantments(),"unit",q.unit(),"condition",q.condition(),"note",q.note()));}variants.put(entry.getKey(),quoted);}}
        finally{GuiShop.CONFIG=original;}details.put("nativeEquipmentSaleQuotes",variants);
    }
}
