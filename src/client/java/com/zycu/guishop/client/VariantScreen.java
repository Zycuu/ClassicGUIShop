package com.zycu.guishop.client;
import com.zycu.guishop.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import java.util.*;

/** Exact registered book and potion variants use the same listing and pricing controls. */
final class VariantScreen extends WorkbenchScreen {
    private record Variant(String key,String source,String name,ItemStack stack,double scale) {}
    private final AdminScreen parent;private final ShopConfig config;private final boolean books;
    private List<Variant> catalog=List.of(),shown=List.of();private final Set<String> picks=new HashSet<>();
    private String query="",base="100",ratio="30",max="",destination="",message="";private int page;
    VariantScreen(AdminScreen parent,ShopConfig config,boolean books){super(books?"Enchanted book variants":"Potion variants");this.parent=parent;this.config=config;this.books=books;}
    private void field(String name,int x,int y,int width,String value,java.util.function.Consumer<String> change){var input=new WorkbenchField(font,x,y,width,Component.literal(name));input.setValue(value);input.setResponder(change);addRenderableWidget(input);}
    @Override protected void init(){
        if(minecraft.level==null)return;var access=minecraft.level.registryAccess();var entries=new ArrayList<Variant>();
        if(books){for(var holder:access.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList())for(int level=1;level<=Math.min(255,holder.value().getMaxLevel());level++){
            var stack=EnchantmentShopService.createBook(holder,level);entries.add(new Variant(holder.key().identifier()+":"+level,holder.key().identifier().getNamespace(),holder.value().description().getString()+" "+EnchantmentShopService.roman(level),stack,level));
        }}else for(var holder:access.lookupOrThrow(Registries.POTION).listElements().toList())for(var item:List.of(Items.POTION,Items.SPLASH_POTION,Items.LINGERING_POTION)){
            var stack=PotionContents.createItemStack(item,holder);double strength=1,duration=1;
            for(var effect:holder.value().getEffects()){strength=Math.max(strength,effect.getAmplifier()+1);duration=Math.max(duration,effect.getDuration()/3600.0);}
            double form=item==Items.LINGERING_POTION?1.5:item==Items.SPLASH_POTION?1.25:1;
            entries.add(new Variant(holder.key().identifier()+":"+ShopService.itemId(stack),holder.key().identifier().getNamespace(),stack.getHoverName().getString(),stack,strength*duration*form));
        }
        catalog=entries.stream().sorted(Comparator.comparing(Variant::key)).toList();shown=catalog.stream().filter(entry->(entry.key()+" "+entry.name()+" "+entry.source()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();page=Math.min(page,Math.max(0,(shown.size()-1)/8));
        field("Filter variants",32,58,420,query,value->query=value);button("Search",464,58,164,()->{page=0;rebuildWidgets();});
        button("Select shown",32,89,180,()->{for(var entry:shown.subList(page*8,Math.min(shown.size(),page*8+8)))picks.add(entry.key());rebuildWidgets();});button("Clear picks",224,89,180,()->{picks.clear();rebuildWidgets();});button("Prices of listed variants",416,89,212,()->minecraft.gui.setScreen(new PriceAdjustScreen(parent,config,books)));
        field("Category ID",32,269,174,destination,value->destination=value);field("Base buy price",218,269,128,base,value->base=value);field("Sell return percent",358,269,128,ratio,value->ratio=value);if(books)field("Custom maximum level",498,269,130,max,value->max=value);
        button("Previous",32,314,96,()->{page=Math.max(0,page-1);rebuildWidgets();});button("Next",140,314,80,()->{page++;rebuildWidgets();});button("Review variants",232,314,264,this::review);button("Done",514,314,114,this::onClose);
    }
    private void review(){try{
        if(picks.isEmpty())throw new IllegalArgumentException("Select variants first");double price=Double.parseDouble(base),returnRatio=Double.parseDouble(ratio)/100;
        if(!Double.isFinite(price)||price<0||price>1e9||!Double.isFinite(returnRatio)||returnRatio<0||returnRatio>1)throw new IllegalArgumentException("Use a finite base price and a return from 0% to 100%");
        String id=destination.isBlank()?books?"enchanted_books":"potions":destination.trim();if(!id.matches("[a-z0-9_.-]{1,80}"))throw new IllegalArgumentException("Enter a lowercase category ID");
        var selected=new ArrayList<>(catalog.stream().filter(entry->picks.contains(entry.key())).toList());
        if(books&&!max.isBlank()){
            int maximum=Integer.parseInt(max);if(maximum<1||maximum>255)throw new IllegalArgumentException("Custom maximum must be 1 to 255");
            var holders=new HashSet<String>();for(var entry:selected){var enchant=entry.stack().get(DataComponents.STORED_ENCHANTMENTS);for(var holder:enchant.keySet())holders.add(holder.unwrapKey().orElseThrow().identifier().toString());}
            selected.clear();for(var name:holders){var holder=minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(net.minecraft.resources.Identifier.parse(name)).orElseThrow();for(int level=1;level<=maximum;level++)selected.add(new Variant(name+":"+level,holder.key().identifier().getNamespace(),holder.value().description().getString()+" "+EnchantmentShopService.roman(level),EnchantmentShopService.createBook(holder,level),level));}
        }
        if(config.categories.stream().mapToInt(cat->cat.items.size()).sum()+selected.size()>10000)throw new IllegalArgumentException("Variant selection would exceed 10,000 listings. Choose fewer variants.");
        final var planned=List.copyOf(selected);
        var existing=config.category(id);int count=planned.size();
        minecraft.gui.setScreen(new EditorDialog(this,"Add exact variants?",count+" variants to "+id+". Base buy: "+config.money(price)+". Books: base x level. Potions: base x strength x duration (minimum 3 min) x form (1 / 1.25 / 1.5). Sell = buy x "+ratio+"%. Existing listings keep their prices. Apply is required.",null,ignored->{
            var category=existing;if(category==null){category=new ShopConfig.Category();category.id=id;category.name=books?"Enchanted Books":"Potions";category.icon=books?"minecraft:enchanted_book":"minecraft:potion";config.categories.add(category);}
            var used=new HashSet<String>();for(var cat:config.categories)for(var item:cat.items)used.add(item.listingId.toLowerCase(Locale.ROOT));
            int added=0;for(var variant:planned){boolean duplicate=false;for(var item:category.items)if(VersionedListingStore.isRuntimeAvailable(item,minecraft.level.registryAccess())&&ItemStackData.same(item.createStack(minecraft.level.registryAccess()),variant.stack())){duplicate=true;break;}if(duplicate)continue;
                var item=new ShopConfig.ShopItem();item.item=ShopService.itemId(variant.stack());item.stack=ItemStackData.encode(variant.stack(),minecraft.level.registryAccess());item.listingId=ItemStackData.unusedListingId(ItemStackData.listingId(variant.stack(),minecraft.level.registryAccess()),used);used.add(item.listingId.toLowerCase(Locale.ROOT));item.name=variant.name();item.buy=Math.round(price*variant.scale()*100)/100.0;item.sell=SaleValuation.floor(item.buy*returnRatio);item.manualPrice=true;category.items.add(item);added++;
            }message="Added "+added+" exact draft listings";rebuildWidgets();
        }));
    }catch(Exception invalid){message=invalid.getMessage();}}
    @Override protected void paint(GuiGraphicsExtractor g,int x,int y,float delta){panel(g,16,16,628,356,WorkbenchTheme.SURFACE);text(g,title.getString(),32,30,WorkbenchTheme.TEXT);
        for(int row=0;row<8&&page*8+row<shown.size();row++){var entry=shown.get(page*8+row);int yy=121+row*16;g.fill(30,yy,630,yy+15,picks.contains(entry.key())?WorkbenchTheme.SELECTED:WorkbenchTheme.ROW);g.item(entry.stack(),33,yy);text(g,font.plainSubstrByWidth(entry.name(),300),56,yy+3,WorkbenchTheme.TEXT);text(g,font.plainSubstrByWidth(entry.key(),244),380,yy+3,WorkbenchTheme.MUTED);}
        text(g,"Category ID (blank creates one)",32,255,WorkbenchTheme.MUTED);text(g,"Base buy price",218,255,WorkbenchTheme.MUTED);text(g,"Sell return %",358,255,WorkbenchTheme.MUTED);if(books)text(g,"Custom max (optional)",498,255,WorkbenchTheme.MUTED);
        text(g,font.plainSubstrByWidth(message.isBlank()?"Page "+(page+1)+" | "+shown.size()+" variants | "+picks.size()+" selected. New variants can be added at 0 buy / 0 sell.":message,595),32,347,WorkbenchTheme.MUTED);
    }
    @Override protected boolean click(double x,double y,int button){if(button!=com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT||x<30||x>=630||y<121||y>=249)return false;int index=page*8+(int)((y-121)/16);if(index>=shown.size())return false;String id=shown.get(index).key();if(!picks.remove(id))picks.add(id);return true;}
    static void paintSummary(GuiGraphicsExtractor g,Font font,ShopConfig config,RegistryAccess access,boolean books,int x,int y,com.zycu.guishop.admin.VariantSources.Catalog detected){
        var sources=new TreeMap<String,Integer>();
        if(books)for(var holder:access.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList())sources.merge(holder.key().identifier().getNamespace(),1,Integer::sum);
        else for(var holder:access.lookupOrThrow(Registries.POTION).listElements().toList())sources.merge(holder.key().identifier().getNamespace(),1,Integer::sum);
        long listed=config.categories.stream().flatMap(cat->cat.items.stream()).filter(item->books?item.item.equals("minecraft:enchanted_book"):Set.of("minecraft:potion","minecraft:splash_potion","minecraft:lingering_potion").contains(item.item)).count();
        g.text(font,"Detected: "+sources.values().stream().mapToInt(Integer::intValue).sum()+" | Store listings: "+listed,x,y,WorkbenchTheme.TEXT,false);
        var rows=detected==null?sources.entrySet().stream().map(entry->new com.zycu.guishop.admin.VariantSources.Source(entry.getKey().equals("minecraft")?"Vanilla (namespace)":"Namespace: "+entry.getKey(),entry.getValue())).toList():books?detected.enchantments():detected.potions();
        int row=1;for(var entry:rows){if(row>7){g.text(font,"More sources available on the server",x,y+row*14,WorkbenchTheme.MUTED,false);break;}g.text(font,font.plainSubstrByWidth(entry.name()+" "+entry.count(),265),x,y+row++*14,WorkbenchTheme.MUTED,false);}
    }
    @Override public void onClose(){parent.status("Variant edits remain in your draft until Apply.");minecraft.gui.setScreen(parent);}
}
