package com.zycu.guishop.client;
import com.zycu.guishop.ShopConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Browse registered variants; preset defaults live together on the Presets tab. */
final class VariantScreen extends WorkbenchScreen {
    private record Entry(String id,String name,net.minecraft.world.item.ItemStack stack){}
    private final AdminScreen parent;private final boolean books;private List<Entry> catalog=List.of(),shown=List.of();
    private String query="";private final WorkbenchScroll scroll=new WorkbenchScroll(614,112,200,8,()->{});
    VariantScreen(AdminScreen parent,ShopConfig config,boolean books){super(books?"Registered enchanted books":"Registered potions");this.parent=parent;this.books=books;}
    @Override protected WorkbenchScroll scroller(){return scroll;}
    private void filter(){shown=catalog.stream().filter(entry->(entry.id()+" "+entry.name()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();scroll.count(shown.size());}
    @Override protected void init(){
        if(minecraft.level==null)return;
        if(catalog.isEmpty()){var access=minecraft.level.registryAccess();var entries=new ArrayList<Entry>();
            if(books){for(var holder:access.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList())for(int level=1;level<=Math.min(255,holder.value().getMaxLevel());level++){var stack=com.zycu.guishop.EnchantmentShopService.createBook(holder,level);entries.add(new Entry(holder.key().identifier()+":"+level,holder.value().description().getString()+" "+com.zycu.guishop.EnchantmentShopService.roman(level),stack));}}
            else for(var holder:access.lookupOrThrow(Registries.POTION).listElements().toList())for(var item:List.of(net.minecraft.world.item.Items.POTION,net.minecraft.world.item.Items.SPLASH_POTION,net.minecraft.world.item.Items.LINGERING_POTION)){var stack=net.minecraft.world.item.alchemy.PotionContents.createItemStack(item,holder);entries.add(new Entry(holder.key().identifier()+" / "+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath(),stack.getHoverName().getString(),stack));}
            catalog=entries.stream().sorted(Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id)).toList();
        }
        filter();var search=new WorkbenchField(font,32,62,514,Component.literal("Search registered content"));search.setValue(query);search.setHint(Component.literal("Search names or IDs..."));search.setResponder(value->{query=value;scroll.row=0;filter();});addRenderableWidget(search);setInitialFocus(search);
        button("Clear",558,62,70,()->search.setValue(""));button("Back",514,338,114,this::onClose);
    }
    @Override protected boolean submitSearch(){filter();return true;}
    @Override protected void paint(GuiGraphicsExtractor g,int x,int y,float delta){panel(g,16,16,628,356,WorkbenchTheme.SURFACE);text(g,title.getString(),32,32,WorkbenchTheme.TEXT);
        for(int row=0;row<8&&scroll.row+row<shown.size();row++){var entry=shown.get(scroll.row+row);int yy=112+row*25;g.fill(30,yy,604,yy+24,x>=30&&x<604&&y>=yy&&y<yy+24?WorkbenchTheme.SELECTED:WorkbenchTheme.ROW);g.item(entry.stack(),34,yy+4);text(g,font.plainSubstrByWidth(entry.name(),534),56,yy+2,WorkbenchTheme.TEXT);text(g,font.plainSubstrByWidth(entry.id(),534),56,yy+13,WorkbenchTheme.MUTED);if(x>=30&&x<604&&y>=yy&&y<yy+24)g.setTooltipForNextFrame(font,font.split(Component.literal(entry.name()+"\n"+entry.id()),320),(int)(originX+x*scale),(int)(originY+y*scale));}
        scroll.paint(g);if(shown.isEmpty())text(g,"No matches. Clear the search to see registered content.",32,135,WorkbenchTheme.MUTED);text(g,shown.size()+" matches | Price defaults are on Presets; item prices are in Shop Builder.",32,320,WorkbenchTheme.MUTED);
    }
    static void paintSummary(GuiGraphicsExtractor g,Font font,ShopConfig config,RegistryAccess access,boolean books,int x,int y,com.zycu.guishop.admin.VariantSources.Catalog detected){
        var sources=new TreeMap<String,Integer>();if(books)for(var holder:access.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList())sources.merge(holder.key().identifier().getNamespace(),1,Integer::sum);else for(var holder:access.lookupOrThrow(Registries.POTION).listElements().toList())sources.merge(holder.key().identifier().getNamespace(),1,Integer::sum);
        long listed=config.categories.stream().flatMap(cat->cat.items.stream()).filter(item->books?item.item.equals("minecraft:enchanted_book"):Set.of("minecraft:potion","minecraft:splash_potion","minecraft:lingering_potion").contains(item.item)).count();
        g.text(font,"Detected: "+sources.values().stream().mapToInt(Integer::intValue).sum()+" | Shop items: "+listed,x,y,WorkbenchTheme.TEXT,false);var rows=detected==null?sources.entrySet().stream().map(entry->new com.zycu.guishop.admin.VariantSources.Source(entry.getKey().equals("minecraft")?"Vanilla":"Namespace: "+entry.getKey(),entry.getValue())).toList():books?detected.enchantments():detected.potions();int row=1;for(var entry:rows){if(row>7)break;g.text(font,font.plainSubstrByWidth(entry.name()+" "+entry.count(),265),x,y+row++*14,WorkbenchTheme.MUTED,false);}
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
