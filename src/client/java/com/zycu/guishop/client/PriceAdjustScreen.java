package com.zycu.guishop.client;
import com.zycu.guishop.ShopConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;
/** A straightforward price sheet. Amounts are final, per-item prices, held locally until Done. */
final class PriceAdjustScreen extends WorkbenchScreen {
    private record Row(ShopConfig.ShopItem item,String category) {}
    private final AdminScreen parent;private final ShopConfig config;private final List<Row> rows;
    private final Map<ShopConfig.ShopItem,String[]> prices=new IdentityHashMap<>();
    private List<Row> shown=List.of();private String filter="",buy="",sell="",error="";private int page;
    PriceAdjustScreen(AdminScreen parent,ShopConfig config,String category,String ignored){
        super("Listing prices");this.parent=parent;this.config=config;
        rows=config.categories.stream().filter(cat->category==null||cat.id.equals(category)).flatMap(cat->cat.items.stream().map(item->new Row(item,cat.name))).toList();
        for(var row:rows)prices.put(row.item(),new String[]{Double.toString(row.item().buy),Double.toString(row.item().sell)});
    }
    private void input(String name,int x,int y,int width,String value,java.util.function.Consumer<String> change){var field=new WorkbenchField(font,x,y,width,Component.literal(name));field.setValue(value);field.setResponder(change);addRenderableWidget(field);}
    @Override protected void init(){
        shown=rows.stream().filter(row->(row.item().name+" "+row.item().item+" "+row.category()).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).toList();page=Math.min(page,Math.max(0,(shown.size()-1)/6));
        input("Search listings",32,58,438,filter,value->filter=value);button("Search",482,58,146,()->{page=0;rebuildWidgets();});
        for(int row=0;row<6&&page*6+row<shown.size();row++){
            var item=shown.get(page*6+row).item();var amounts=prices.get(item);int y=112+row*25;
            input("Buy "+item.listingId,382,y,112,amounts[0],value->amounts[0]=value);
            input("Sell "+item.listingId,506,y,122,amounts[1],value->amounts[1]=value);
        }
        input("Buy amount for all",32,298,150,buy,value->buy=value);input("Sell amount for all",194,298,150,sell,value->sell=value);
        button("Set all "+rows.size()+" listings",356,298,272,()->{try{amount(buy);amount(sell);for(var row:rows)prices.put(row.item(),new String[]{buy,sell});error="Set all draft amounts. Done returns to the builder.";rebuildWidgets();}catch(Exception bad){error="Enter both buy and sell amounts, from 0 to 1 billion.";}});
        button("Previous",32,338,90,()->{page=Math.max(0,page-1);rebuildWidgets();});button("Next",134,338,70,()->{page++;rebuildWidgets();});button("Cancel",406,338,100,this::onClose);
        button("Done",518,338,110,()->{try{for(var row:rows){var values=prices.get(row.item());amount(values[0]);amount(values[1]);}for(var row:rows){var values=prices.get(row.item());row.item().buy=amount(values[0]);row.item().sell=amount(values[1]);row.item().manualPrice=true;}parent.status("Listing prices updated in the draft. Apply saves them.");minecraft.gui.setScreen(parent);}catch(Exception bad){error="A listing contains an invalid price. Use an amount from 0 to 1 billion.";}});
    }
    private static double amount(String value){double amount=Double.parseDouble(value);if(!Double.isFinite(amount)||amount<0||amount>1e9)throw new IllegalArgumentException("Invalid price");return amount;}
    @Override protected void paint(GuiGraphicsExtractor g,int x,int y,float delta){
        panel(g,16,16,628,356,WorkbenchTheme.SURFACE);text(g,"Listing prices | "+rows.size()+" listings",32,30,WorkbenchTheme.TEXT);
        text(g,"Item",32,94,WorkbenchTheme.MUTED);text(g,"Buy / item",382,94,WorkbenchTheme.MUTED);text(g,"Sell / item",506,94,WorkbenchTheme.MUTED);
        for(int row=0;row<6&&page*6+row<shown.size();row++){var entry=shown.get(page*6+row);int yy=112+row*25;g.fill(30,yy,370,yy+21,WorkbenchTheme.ROW);
            if(minecraft.level!=null&&com.zycu.guishop.VersionedListingStore.isRuntimeAvailable(entry.item(),minecraft.level.registryAccess()))g.item(entry.item().createStack(minecraft.level.registryAccess()),33,yy+2);
            text(g,font.plainSubstrByWidth(entry.item().name,306),55,yy+5,WorkbenchTheme.TEXT);
            if(x>=30&&x<370&&y>=yy&&y<yy+21)g.setTooltipForNextFrame(font,font.split(Component.literal(entry.item().name+" | "+entry.category()+" | "+entry.item().listingId),320),(int)(originX+x*scale),(int)(originY+y*scale));
        }
        text(g,"Set the same prices for every listing in this sheet:",32,264,WorkbenchTheme.MUTED);
        text(g,"Buy / item",32,282,WorkbenchTheme.MUTED);text(g,"Sell / item",194,282,WorkbenchTheme.MUTED);
        text(g,font.plainSubstrByWidth(error.isBlank()?"Page "+(page+1)+" | 0 disables buying or selling. Apply saves your draft.":error,590),32,323,error.isBlank()?WorkbenchTheme.MUTED:WorkbenchTheme.GOLD);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
