package com.zycu.guishop;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.core.component.DataComponents;
import java.util.*;
/** Legacy /shop enchant navigation over ordinary, explicitly priced exact book listings. */
public final class EnchantmentShopService {
    private EnchantmentShopService() {}
    public static ItemStack createBook(Holder<Enchantment> holder, int level) { var book = new ItemStack(Items.ENCHANTED_BOOK); book.enchant(holder, level); return book; }
    public static int maximumLevel(Enchantment enchantment, ShopConfig.EnchantmentOffer offer) { return Math.min(offer.maxLevel <= 0 ? enchantment.getMaxLevel() : offer.maxLevel, 255); }
    private static ShopConfig.FoundItem find(ServerPlayer player, String id, int level, ShopGui.Mode mode) {
        if (!ShopPermissions.user(player, "guishop.enchant")) return null;
        var holder = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(net.minecraft.resources.Identifier.parse(id)); if (holder.isEmpty()) return null;
        var stack = createBook(holder.get(), level); ShopConfig.FoundItem best = null;
        for (var cat : GuiShop.CONFIG.categories) if (ShopPermissions.category(player, cat.id)) for (var item : cat.items)
            if (VersionedListingStore.isRuntimeAvailable(item, player.registryAccess()) && ItemStackData.same(stack, item.createStack(player.registryAccess())) && (mode == ShopGui.Mode.BUY ? item.buy : item.sell) > 0
                && (best == null || (mode == ShopGui.Mode.BUY ? item.buy < best.item().buy : item.sell > best.item().sell))) best = new ShopConfig.FoundItem(cat, item);
        return best;
    }
    public static double cost(ServerPlayer player, String id, int level, ShopGui.Mode mode) { var found = find(player,id,level,mode); return found == null ? 0 : mode == ShopGui.Mode.BUY ? found.item().buy : found.item().sell; }
    public static List<EnchantmentView> availableEnchantments(ServerPlayer player) { return availableEnchantments(player, ShopGui.Mode.BUY); }
    public static List<EnchantmentView> availableEnchantments(ServerPlayer player, ShopGui.Mode mode) {
        var result = new ArrayList<EnchantmentView>();
        for (var holder : player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()) {
            String id = holder.key().identifier().toString(); int max = 0; double first = 0,last = 0;
            for (var cat : GuiShop.CONFIG.categories) if (ShopPermissions.category(player,cat.id)) for (var item : cat.items) {
                if (!item.item.equals("minecraft:enchanted_book") || !VersionedListingStore.isRuntimeAvailable(item,player.registryAccess())) continue;
                var stack = item.createStack(player.registryAccess()); var ench = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                int level = ench.getLevel(holder); if (ench.size() != 1 || level < 1 || (mode == ShopGui.Mode.BUY ? item.buy : item.sell) <= 0) continue;
                if (first == 0 || level == 1) first = mode == ShopGui.Mode.BUY ? item.buy : item.sell;
                if (level > max) { max = level;last = mode == ShopGui.Mode.BUY ? item.buy : item.sell; }
            }
            if (max > 0 && ShopPermissions.user(player,"guishop.enchant")) result.add(new EnchantmentView(id,holder.value().description().getString(),holder,max,first,last));
        }
        result.sort(Comparator.comparing(EnchantmentView::displayName,String.CASE_INSENSITIVE_ORDER)); return result;
    }
    public static List<OfferView> availableLevels(ServerPlayer player,String id) { return availableLevels(player,id,ShopGui.Mode.BUY); }
    public static List<OfferView> availableLevels(ServerPlayer player,String id,ShopGui.Mode mode) {
        var holder = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(net.minecraft.resources.Identifier.parse(id)); if (holder.isEmpty()) return List.of();
        var best=new TreeMap<Integer,Double>();
        if(!ShopPermissions.user(player,"guishop.enchant"))return List.of();
        for(var cat:GuiShop.CONFIG.categories)if(ShopPermissions.category(player,cat.id))for(var item:cat.items){
            if(!item.item.equals("minecraft:enchanted_book")||!VersionedListingStore.isRuntimeAvailable(item,player.registryAccess()))continue;
            double value=mode==ShopGui.Mode.BUY?item.buy:item.sell;if(value<=0)continue;
            var stack=item.createStack(player.registryAccess());var ench=stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY);int level=ench.getLevel(holder.get());
            if(ench.size()!=1||level<1||!ItemStackData.same(stack,createBook(holder.get(),level)))continue;
            best.merge(level,value,(old,now)->mode==ShopGui.Mode.BUY?Math.min(old,now):Math.max(old,now));
        }
        return best.entrySet().stream().map(entry->new OfferView(id,holder.get().value().description().getString(),holder.get(),entry.getKey(),entry.getValue())).toList();
    }
    public static boolean purchase(ServerPlayer player,String id,int level) { var found=find(player,id,level,ShopGui.Mode.BUY);return found!=null && ShopService.trade(player,found.item(),ShopGui.Mode.BUY,1); }
    public static boolean sell(ServerPlayer player,String id,int level,int quantity) { var found=find(player,id,level,ShopGui.Mode.SELL);return found!=null && ShopService.trade(player,found.item(),ShopGui.Mode.SELL,quantity); }
    public static boolean sellHeld(ServerPlayer player,ItemStack held,int quantity,boolean all) { return false; }
    public static boolean showWorth(ServerPlayer player,ItemStack held,int quantity) { return false; }
    public static String roman(int value) { return switch(value) { case 1->"I";case 2->"II";case 3->"III";case 4->"IV";case 5->"V";case 6->"VI";case 7->"VII";case 8->"VIII";case 9->"IX";case 10->"X";default->Integer.toString(value); }; }
    public record EnchantmentView(String enchantmentId,String displayName,Holder<Enchantment> holder,int maxLevel,double firstLevelCost,double maximumLevelCost) {}
    public record OfferView(String enchantmentId,String displayName,Holder<Enchantment> holder,int targetLevel,double cost) {}
}
