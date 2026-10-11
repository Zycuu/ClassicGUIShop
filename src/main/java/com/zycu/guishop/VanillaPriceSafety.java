package com.zycu.guishop;

import com.zycu.guishop.admin.AdminDocument;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Vanilla purchase/conversion bounds. Final prices are stored, never modified during a trade. */
public final class VanillaPriceSafety {
    private VanillaPriceSafety() {}
    public record Input(List<String> choices, double count) {}
    public record Rule(String name, String kind, List<Input> inputs, String output, double count,
                       List<String> catalysts, String block, Double share, List<Input> pricingInputs) {}
    private record Model(int version, String minecraft, String source_sha256, List<Rule> rules,
                         int recipe_count, int trade_count, List<String> items, List<String> potions, Map<String,Integer> enchantments) {}
    public record Bound(double cost, String route) {}
    public record Change(String listingId, String name, double buy, double sell, double safeSell,
                         double acquisitionCost, String route) {}
    public record Review(int checked, int excluded, int recipes, int trades, int rules,
                         int buyOnly, List<Change> changes, String scope) {}
    private static final double EPSILON = 1e-9;
    private static final double ZERO = 1e-8;
    private static final Model MODEL = load();
    private static final Set<String> ITEMS=Set.copyOf(MODEL.items());
    private static final Set<String> POTIONS=Set.copyOf(MODEL.potions());
    public static int maximumLevel(String enchantment){return MODEL.enchantments().getOrDefault(enchantment,0);}
    private static Model load() {
        try (var stream = VanillaPriceSafety.class.getResourceAsStream("/guishop/vanilla-economy-26.3.json")) {
            if (stream == null) throw new IllegalStateException("Missing vanilla economic model");
            var model = AdminDocument.JSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Model.class);
            if (model.version() != 1 || !model.minecraft().equals("26.3") || model.rules().isEmpty() || model.items()==null || model.potions()==null || model.enchantments()==null) throw new IllegalStateException("Invalid vanilla economic model");
            return model;
        } catch (Exception error) { throw new IllegalStateException("Could not load vanilla economic model", error); }
    }
    /** Custom datapack/mod components are the administrator's responsibility. */
    public static String key(ItemStack stack) {
        String item = ShopService.itemId(stack);
        if (!ITEMS.contains(item) || stack.has(DataComponents.CUSTOM_DATA)
            || stack.has(DataComponents.CUSTOM_MODEL_DATA)
            || !Objects.equals(stack.get(DataComponents.ITEM_MODEL),stack.getItem().components().get(DataComponents.ITEM_MODEL))
            || stack.has(DataComponents.CUSTOM_NAME) || !stack.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines().isEmpty()) return null;
        var potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            if (!potion.customEffects().isEmpty() || potion.customName().isPresent()) return null;
            var holder = potion.potion().flatMap(h -> h.unwrapKey());
            if (holder.isPresent()) return POTIONS.contains(holder.get().identifier().toString()) ? item + "|potion=" + holder.get().identifier() : null;
        }
        var enchants = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!enchants.isEmpty()) {
            if (enchants.size() != 1) return null;
            var e = enchants.entrySet().iterator().next(); var id = e.getKey().unwrapKey().orElseThrow().identifier();
            return maximumLevel(id.toString())>0 && e.getIntValue() <= maximumLevel(id.toString())
                ? item + "|enchantment=" + id + "@" + e.getIntValue() : null;
        }
        // Cosmetic vanilla variants share a conservative ordinary-item bound.
        // The exact original components still control actual trade matching.
        return item;
    }
    private static boolean exists(String key, RegistryAccess access) {
        String item = key.split("\\|", 2)[0];
        var id = Identifier.tryParse(item);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.getValue(id) == Items.AIR) return false;
        if (key.contains("|potion=")) return access.lookupOrThrow(Registries.POTION).containsKey(Identifier.parse(key.substring(key.indexOf('=')+1)));
        if (key.contains("|enchantment=")) return access.lookupOrThrow(Registries.ENCHANTMENT).containsKey(Identifier.parse(key.substring(key.indexOf('=')+1,key.indexOf('@'))));
        return true;
    }
    private static List<Rule> rules(RegistryAccess access) {
        var rules=new ArrayList<>(MODEL.rules().stream().filter(r -> {
            if (!exists(r.output(), access)) return false;
            if (r.block() == null) return true;
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(r.block()));
            return item instanceof BlockItem blockItem && BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString().equals(r.block());
        }).toList());
        // Shipped compostables add at most one layer per consumed input. Seven
        // successful inputs yield one bone meal; unsuccessful rolls cost more.
        // Only official vanilla item IDs participate, never modded entries.
        var compostables=ITEMS.stream().filter(id->BuiltInRegistries.ITEM.containsKey(Identifier.parse(id)))
            .filter(id->BuiltInRegistries.ITEM.getValue(Identifier.parse(id)).components().has(DataComponents.COMPOSTABLE)).sorted().toList();
        if(!compostables.isEmpty())rules.add(new Rule("compost/7-successful-inputs","interaction",List.of(new Input(compostables,7)),"minecraft:bone_meal",1,null,null,null,null));
        return rules;
    }
    public static Map<String,Bound> bounds(ShopConfig config, RegistryAccess access) {
        var costs = new TreeMap<String,Bound>();
        for (var category : config.categories) for (var item : category.items) {
            if (!(item.buy > 0) || !VersionedListingStore.isRuntimeAvailable(item, access)) continue;
            String key = key(item.createStack(access)); if (key == null) continue;
            var old = costs.get(key);
            if (old == null || item.buy < old.cost()) costs.put(key,new Bound(item.buy,"Direct purchase: " + item.name));
        }
        relax(costs,rules(access),false);
        return costs;
    }
    private static void relax(Map<String,Bound> costs, List<Rule> rules, boolean pricing) {
        // Stable rule order and a very small convergence threshold remove the
        // old recipe-order/rounding instability. A productive cycle converges
        // to zero; no positive-cent sell price is enabled for its outputs.
        for (int pass=0;pass<4096;pass++) {
            boolean changed=false;
            for (var rule : rules) {
                if (rule.catalysts()!=null && rule.catalysts().stream().anyMatch(k -> !costs.containsKey(k))) continue;
                var inputs=pricing&&rule.pricingInputs()!=null?rule.pricingInputs():rule.inputs();
                double total=0;String witness="";boolean available=true;
                for (var input:inputs) {
                    Bound best=null;String choice="";
                    for (String key:input.choices()) {var cost=costs.get(key);if(cost!=null&&(best==null||cost.cost()<best.cost())){best=cost;choice=key;}}
                    if(best==null){available=false;break;}
                    total+=best.cost()*input.count();
                    if(witness.length()<180) witness+=(witness.isEmpty()?"":", ")+number(input.count())+"x "+choice;
                }
                if(!available)continue;
                // Acquisition costs describe a real route that can discard its
                // other outputs. Splitting them during propagation invents
                // multiplication in reversible recipes such as honey blocks.
                double next=total/rule.count();
                if(next<ZERO)next=0;
                var previous=costs.get(rule.output());
                if(previous==null||next+EPSILON<previous.cost()){
                    costs.put(rule.output(),new Bound(next,rule.name()+(witness.isEmpty()?" (retained/refilled catalyst)":" | "+witness)));
                    changed=true;
                }
            }
            if(!changed)return;
        }
        throw new IllegalStateException("Vanilla price analysis did not converge. Existing shop was not changed.");
    }
    public static Review review(ShopConfig config, RegistryAccess access) {
        var activeRules=rules(access);var costs=bounds(config,access);var saleLimits=new TreeMap<>(costs);
        // Allocate the sale budget across every simultaneously obtainable
        // output, including returned containers. These limits affect final
        // sale prices only, never subsequent acquisition-cost propagation.
        for(var rule:activeRules)if(rule.share()!=null){
            if(rule.catalysts()!=null&&rule.catalysts().stream().anyMatch(k->!costs.containsKey(k)))continue;
            double total=0;boolean available=true;
            for(var input:rule.inputs()){
                double best=input.choices().stream().filter(costs::containsKey).mapToDouble(k->costs.get(k).cost()).min().orElse(Double.POSITIVE_INFINITY);
                if(!Double.isFinite(best)){available=false;break;}total+=best*input.count();
            }
            if(available){double limit=total/rule.count()*rule.share();var old=saleLimits.get(rule.output());
                if(old==null||limit<old.cost())saleLimits.put(rule.output(),new Bound(limit,rule.name()+" | shared budget for outputs and returned containers"));
            }
        }
        var changes=new ArrayList<Change>();int checked=0,excluded=0,buyOnly=0;
        for(var category:config.categories)for(var item:category.items){
            if(!VersionedListingStore.isRuntimeAvailable(item,access)){excluded++;continue;}
            String key=key(item.createStack(access));if(key==null){excluded++;continue;}checked++;
            var bound=saleLimits.get(key);if(bound==null||!(item.sell>0))continue;
            double safe=floor(bound.cost()*.95);
            if(item.sell>safe+.000001){changes.add(new Change(item.listingId,item.name,item.buy,item.sell,safe,bound.cost(),bound.route()));if(safe==0)buyOnly++;}
        }
        changes.sort(Comparator.comparing(Change::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Change::listingId));
        return new Review(checked,excluded,MODEL.recipe_count(),MODEL.trade_count(),activeRules.size(),buyOnly,changes,
            "Vanilla 26.3 only. Mods, custom components and modified datapack mechanics are not assessed. Timed resource farming is separate.");
    }
    public static int apply(ShopConfig config, RegistryAccess access) {
        var report=review(config,access);
        for(var change:report.changes())config.findItem(change.listingId()).item().sell=change.safeSell();
        if(!review(config,access).changes().isEmpty())throw new IllegalStateException("Vanilla preset price audit failed; existing shop was not changed");
        return report.changes().size();
    }
    /** Deterministic component-aware brew buy prices, generated once into the preset. */
    public static void pricePotions(ShopConfig config, RegistryAccess access) {
        var costs=new TreeMap<String,Bound>();
        double bottle=config.categories.stream().flatMap(c->c.items.stream()).filter(i->i.item.equals("minecraft:glass_bottle")&&i.buy>0).mapToDouble(i->i.buy).min().orElse(2);
        for(var category:config.categories)for(var item:category.items){
            if(!(item.buy>0))continue;String key=key(item.createStack(access));if(key==null)continue;
            if(!ListingGroups.potion(item.item))costs.merge(key,new Bound(item.buy,"Ingredient purchase"),(a,b)->a.cost()<=b.cost()?a:b);
        }
        costs.put("minecraft:potion|potion=minecraft:water",new Bound(bottle,"Empty bottle + water"));
        var brewing=rules(access).stream().filter(r->r.kind().equals("recipe")&&r.output().contains("|potion=")).toList();
        relax(costs,brewing,true);
        for(var category:config.categories)for(var item:category.items){
            if(!(item.buy>0)||!ListingGroups.potion(item.item))continue;
            var cost=costs.get(key(item.createStack(access)));if(cost==null)continue;
            boolean water=item.item.equals("minecraft:potion")&&key(item.createStack(access)).endsWith("=minecraft:water");
            item.buy=Math.max(.01,Math.round(cost.cost()*(water?1:1.10)*100)/100.0);
        }
    }
    private static String number(double value){return value==Math.rint(value)?Long.toString((long)value):Double.toString(value);}
    private static double floor(double amount){return Math.max(0,Math.floor((amount+1e-10)*100)/100.0);}
}
