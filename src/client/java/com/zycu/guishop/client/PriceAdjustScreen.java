package com.zycu.guishop.client;
import com.zycu.guishop.ShopConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
/** Category-only bulk amounts. Cancel/escape never modify the category. */
final class PriceAdjustScreen extends WorkbenchScreen {
    private final AdminScreen parent;private final ShopConfig config;private final ShopConfig.Category category;
    private String buy="",sell="",error="";
    PriceAdjustScreen(AdminScreen parent,ShopConfig config,String categoryId,String ignored){super("Bulk Adjust");this.parent=parent;this.config=config;category=config.category(categoryId);if(category==null)throw new IllegalArgumentException("Select a category first");}
    @Override protected Screen modalParent(){return parent;}
    @Override protected void init(){var b=new WorkbenchField(font,160,173,162,Component.literal("New buy price"));b.setValue(buy);b.setResponder(value->buy=value);addRenderableWidget(b);var s=new WorkbenchField(font,338,173,162,Component.literal("New sell price"));s.setValue(sell);s.setResponder(value->sell=value);addRenderableWidget(s);setInitialFocus(b);button("Cancel",160,255,126,this::onClose);button("Update category",298,255,202,()->{try{double bv=amount(buy),sv=amount(sell);for(var item:category.items){if(item.unavailableImport&&(bv>0||sv>0))throw new IllegalArgumentException("This category contains unavailable items. Install their content before enabling prices.");}for(var item:category.items){item.buy=bv;item.sell=sv;item.manualPrice=true;}parent.bulkUpdated(category);minecraft.gui.setScreen(parent);}catch(Exception invalid){error=invalid instanceof NumberFormatException?"Enter both prices, from 0 to 1 billion.":invalid.getMessage();}});}
    private static double amount(String value){double n=Double.parseDouble(value);if(!Double.isFinite(n)||n<0||n>1e9)throw new IllegalArgumentException("Prices must be from 0 to 1 billion.");return n;}
    @Override protected void paint(GuiGraphicsExtractor g,int x,int y,float delta){panel(g,140,84,380,218,WorkbenchTheme.SURFACE);text(g,"Bulk Adjust",160,102,WorkbenchTheme.TEXT);text(g,font.plainSubstrByWidth(category.name+" | "+category.items.size()+" items",340),160,124,WorkbenchTheme.TEAL);text(g,"New buy price ("+config.currencySymbol+")",160,154,WorkbenchTheme.MUTED);text(g,"New sell price ("+config.currencySymbol+")",338,154,WorkbenchTheme.MUTED);int line=0;for(var text:font.split(Component.literal(error.isBlank()?"Every item in this category receives these prices. 0 disables that side. Save changes in the workbench.":error),340))g.text(font,text,160,204+line++*12,error.isBlank()?WorkbenchTheme.MUTED:WorkbenchTheme.DANGER,false);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
