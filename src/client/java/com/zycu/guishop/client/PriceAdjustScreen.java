package com.zycu.guishop.client;
import com.zycu.guishop.ShopConfig;
import com.zycu.guishop.admin.BulkPricing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;
/** One-time changes to a detached draft, with selection and a review before modification. */
final class PriceAdjustScreen extends WorkbenchScreen {
    private final AdminScreen parent; private final ShopConfig config; private final String category;
    private final Set<String> selected=new HashSet<>(); private List<ShopConfig.ShopItem> shown=List.of();
    private String filter="",buy="0",sell="0",ratio="30",scope="category",error="";private boolean useRatio;private int page;
    PriceAdjustScreen(AdminScreen parent,ShopConfig config,String category,String item){super("Adjust prices");this.parent=parent;this.config=config;this.category=category;if(item!=null){selected.add(item);scope="selected";}if(category==null)scope="all";}
    PriceAdjustScreen(AdminScreen parent,ShopConfig config,boolean books){
        this(parent,config,null,null);filter=books?"minecraft:enchanted_book":"potion";scope="selected";
        for(var cat:config.categories)for(var item:cat.items)if(books?item.item.equals("minecraft:enchanted_book"):Set.of("minecraft:potion","minecraft:splash_potion","minecraft:lingering_potion").contains(item.item))selected.add(item.listingId);
    }
    private void input(String name,int x,int y,int w,String value,java.util.function.Consumer<String> change){var field=new WorkbenchField(font,x,y,w,Component.literal(name));field.setValue(value);field.setResponder(change);addRenderableWidget(field);}
    @Override protected void init(){
        var all=config.categories.stream().flatMap(cat->cat.items.stream()).toList();shown=all.stream().filter(item->(item.name+" "+item.listingId).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).toList();page=Math.min(page,Math.max(0,(shown.size()-1)/8));
        input("Filter listings",32,59,420,filter,value->filter=value);button("Search",464,59,164,()->{page=0;rebuildWidgets();});
        button("Scope: "+scope,32,92,192,()->{scope=scope.equals("selected")?category==null?"all":"category":scope.equals("category")?"all":"selected";rebuildWidgets();});
        button("Select shown",236,92,186,()->{for(var item:shown.subList(page*8,Math.min(shown.size(),page*8+8)))selected.add(item.listingId);rebuildWidgets();});button("Clear selection",434,92,194,()->{selected.clear();rebuildWidgets();});
        input("Buy adjustment percent",32,281,176,buy,value->buy=value);input("Sell adjustment percent",226,281,176,sell,value->sell=value);input("Sell return percent",420,281,208,ratio,value->ratio=value);
        button(useRatio?"Sell = % of buy":"Separate buy / sell %",32,313,238,()->{useRatio=!useRatio;rebuildWidgets();});button("Previous",282,313,96,()->{page=Math.max(0,page-1);rebuildWidgets();});button("Next",390,313,80,()->{page++;rebuildWidgets();});
        button("Review",480,313,148,this::review);button("Done",514,345,114,this::onClose);
    }
    private void review(){try{
        var items=config.categories.stream().filter(cat->!scope.equals("category")||cat.id.equals(category)).flatMap(cat->cat.items.stream()).filter(item->!scope.equals("selected")||selected.contains(item.listingId)).toList();
        var changes=BulkPricing.preview(items,Double.parseDouble(buy),Double.parseDouble(sell),useRatio?Double.parseDouble(ratio):null);
        long enabled=changes.stream().filter(change->change.item().sell==0&&change.sell()>0).count();
        String method="Scope: "+scope+". Buy change "+buy+"%. "+(useRatio?"Sell return "+ratio+"% of adjusted buy.":"Sell change "+sell+"%.");
        String example=changes.isEmpty()?"No prices change.":changes.getFirst().item().name+": buy "+config.money(changes.getFirst().item().buy)+" to "+config.money(changes.getFirst().buy())+", sell "+config.money(changes.getFirst().item().sell)+" to "+config.money(changes.getFirst().sell())+".";
        minecraft.gui.setScreen(new EditorDialog(this,"Change draft prices?",method+" "+changes.size()+" listings change; "+enabled+" sell prices enabled. "+example+" Apply is still required.",null,ignored->{BulkPricing.apply(changes);error="Changed "+changes.size()+" draft listings";rebuildWidgets();}));
    }catch(Exception invalid){error=invalid.getMessage();}}
    @Override protected void paint(GuiGraphicsExtractor g,int x,int y,float delta){panel(g,16,16,628,356,WorkbenchTheme.SURFACE);text(g,"Adjust prices | draft only",32,30,WorkbenchTheme.TEXT);
        for(int row=0;row<8&&page*8+row<shown.size();row++){var item=shown.get(page*8+row);int yy=124+row*16;g.fill(30,yy,630,yy+15,selected.contains(item.listingId)?WorkbenchTheme.SELECTED:WorkbenchTheme.ROW);text(g,font.plainSubstrByWidth(item.name,310),36,yy+3,WorkbenchTheme.TEXT);text(g,"Buy "+config.money(item.buy)+" | Sell "+config.money(item.sell),350,yy+3,WorkbenchTheme.MUTED);}
        text(g,"Buy change %",32,265,WorkbenchTheme.MUTED);text(g,"Sell change %",226,265,WorkbenchTheme.MUTED);text(g,"Sell return % of buy",420,265,WorkbenchTheme.MUTED);text(g,font.plainSubstrByWidth(error.isBlank()?"Page "+(page+1)+" | Negative percentages lower prices. Zero prices stay disabled unless explicitly given a sell return.":error,464),32,350,WorkbenchTheme.MUTED);
    }
    @Override protected boolean click(double x,double y,int button){if(button!=com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT||x<30||x>=630||y<124||y>=252)return false;int index=page*8+(int)((y-124)/16);if(index>=shown.size())return false;String id=shown.get(index).listingId;if(!selected.remove(id))selected.add(id);return true;}
    @Override public void onClose(){parent.status("Price edits remain in the draft until Apply.");minecraft.gui.setScreen(parent);}
}
