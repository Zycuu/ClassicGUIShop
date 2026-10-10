package com.zycu.guishop.client;
import com.zycu.guishop.ShopConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Registered content and a default return. Actual listing prices are edited only in the builder. */
final class VariantScreen extends WorkbenchScreen {
    private record Entry(String id,String name,net.minecraft.world.item.ItemStack stack) {}
    private final AdminScreen parent;private final ShopConfig config;private final boolean books;
    private List<Entry> shown=List.of();private String query="",ratio,error="";private int page;
    VariantScreen(AdminScreen parent,ShopConfig config,boolean books){super(books?"Registered enchanted books":"Registered potions");this.parent=parent;this.config=config;this.books=books;ratio=Double.toString((books?config.bookSellRatio:config.potionSellRatio)*100);}
    @Override protected void init(){
        if(minecraft.level==null)return;var access=minecraft.level.registryAccess();var entries=new ArrayList<Entry>();
        if(books){for(var holder:access.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList())for(int level=1;level<=Math.min(255,holder.value().getMaxLevel());level++){
            var stack=com.zycu.guishop.EnchantmentShopService.createBook(holder,level);entries.add(new Entry(holder.key().identifier()+":"+level,holder.value().description().getString()+" "+com.zycu.guishop.EnchantmentShopService.roman(level),stack));
        }}else for(var holder:access.lookupOrThrow(Registries.POTION).listElements().toList())for(var item:List.of(net.minecraft.world.item.Items.POTION,net.minecraft.world.item.Items.SPLASH_POTION,net.minecraft.world.item.Items.LINGERING_POTION)){
            var stack=net.minecraft.world.item.alchemy.PotionContents.createItemStack(item,holder);entries.add(new Entry(holder.key().identifier()+" / "+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath(),stack.getHoverName().getString(),stack));
        }
        shown=entries.stream().filter(entry->(entry.id()+" "+entry.name()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).sorted(Comparator.comparing(Entry::id)).toList();page=Math.min(page,Math.max(0,(shown.size()-1)/8));
        var filter=new WorkbenchField(font,32,58,438,Component.literal("Search registered content"));filter.setValue(query);filter.setResponder(value->query=value);addRenderableWidget(filter);
        button("Search",482,58,146,()->{page=0;rebuildWidgets();});
        var field=new WorkbenchField(font,32,292,150,Component.literal("Default sell return percent"));field.setValue(ratio);field.setResponder(value->ratio=value);addRenderableWidget(field);
        button("Previous",32,338,90,()->{page=Math.max(0,page-1);rebuildWidgets();});button("Next",134,338,70,()->{page++;rebuildWidgets();});button("Done",518,338,110,()->{try{double value=Double.parseDouble(ratio)/100;if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException();if(books)config.bookSellRatio=value;else config.potionSellRatio=value;parent.status("Default return updated in the draft. Existing prices are unchanged.");minecraft.gui.setScreen(parent);}catch(Exception invalid){error="Enter a percentage from 0 to 100.";}});
    }
    @Override protected void paint(GuiGraphicsExtractor g,int x,int y,float delta){
        panel(g,16,16,628,356,WorkbenchTheme.SURFACE);text(g,title.getString(),32,30,WorkbenchTheme.TEXT);
        text(g,"Registered name",32,95,WorkbenchTheme.MUTED);text(g,"Source / ID",342,95,WorkbenchTheme.MUTED);
        for(int row=0;row<8&&page*8+row<shown.size();row++){var entry=shown.get(page*8+row);int yy=113+row*18;g.fill(30,yy,630,yy+17,WorkbenchTheme.ROW);g.item(entry.stack(),33,yy);text(g,font.plainSubstrByWidth(entry.name(),275),54,yy+4,WorkbenchTheme.TEXT);text(g,font.plainSubstrByWidth(entry.id(),280),342,yy+4,WorkbenchTheme.MUTED);if(x>=30&&x<630&&y>=yy&&y<yy+17)g.setTooltipForNextFrame(font,font.split(Component.literal(entry.name()+" | "+entry.id()),320),(int)(originX+x*scale),(int)(originY+y*scale));}
        text(g,"Default sell return (% of buy)",32,275,WorkbenchTheme.MUTED);
        text(g,"New Normal presets only. Edit listed prices in the Shop Builder.",194,298,WorkbenchTheme.MUTED);
        text(g,error.isBlank()?"Page "+(page+1)+" | "+shown.size()+" registered entries":error,32,322,error.isBlank()?WorkbenchTheme.MUTED:WorkbenchTheme.DANGER);
    }
    static void paintSummary(GuiGraphicsExtractor g,Font font,ShopConfig config,RegistryAccess access,boolean books,int x,int y,com.zycu.guishop.admin.VariantSources.Catalog detected){
        var sources=new TreeMap<String,Integer>();
        if(books)for(var holder:access.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList())sources.merge(holder.key().identifier().getNamespace(),1,Integer::sum);
        else for(var holder:access.lookupOrThrow(Registries.POTION).listElements().toList())sources.merge(holder.key().identifier().getNamespace(),1,Integer::sum);
        long listed=config.categories.stream().flatMap(cat->cat.items.stream()).filter(item->books?item.item.equals("minecraft:enchanted_book"):Set.of("minecraft:potion","minecraft:splash_potion","minecraft:lingering_potion").contains(item.item)).count();
        g.text(font,"Detected: "+sources.values().stream().mapToInt(Integer::intValue).sum()+" | Store listings: "+listed,x,y,WorkbenchTheme.TEXT,false);
        var rows=detected==null?sources.entrySet().stream().map(entry->new com.zycu.guishop.admin.VariantSources.Source(entry.getKey().equals("minecraft")?"Vanilla (namespace)":"Namespace: "+entry.getKey(),entry.getValue())).toList():books?detected.enchantments():detected.potions();
        int row=1;for(var entry:rows){if(row>7){g.text(font,"More sources available on the server",x,y+row*14,WorkbenchTheme.MUTED,false);break;}g.text(font,font.plainSubstrByWidth(entry.name()+" "+entry.count(),265),x,y+row++*14,WorkbenchTheme.MUTED,false);}
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
