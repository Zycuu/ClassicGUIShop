package com.zycu.guishop.client;
import com.zycu.guishop.admin.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;
final class TrendingScreen extends WorkbenchScreen {
    private final AdminScreen parent;private final String currency;private Trending.Result result;
    private long generation;private int days=7,page,offset,retry;private boolean sold,started,busy;private String variant,player,asOf,message="";
    TrendingScreen(AdminScreen parent,String currency){super("Trending");this.parent=parent;this.currency=currency;}
    private void request(){busy=true;message="Reading complete server journals...";if(AdminShopClient.request("trending","",AdminDocument.JSON.toJson(new Trending.Request(days,sold,variant,player,offset,asOf,++generation)))){retry=0;}else retry=6;}
    @Override public void tick(){if(retry>0&&--retry==0)request();}
    void response(Trending.Result value){if(value.request().generation()!=generation||value.request().days()!=days||value.request().sold()!=sold||!Objects.equals(value.request().variant(),variant)||!Objects.equals(value.request().player(),player)||value.request().offset()!=offset)return;result=value;asOf=value.request().asOf();busy=false;message=value.warning();rebuildWidgets();}
    void failure(String value){busy=false;message=value;rebuildWidgets();}
    @Override protected void init(){
        button(sold?"Sold":"Purchased",32,58,150,()->{sold=!sold;variant=null;player=null;asOf=null;page=offset=0;request();rebuildWidgets();});
        button(days==1?"24 hours":days+" days",194,58,138,()->{days=days==1?7:days==7?30:1;variant=null;player=null;asOf=null;page=offset=0;request();rebuildWidgets();});
        button("Refresh",344,58,136,()->{asOf=null;page=offset=0;request();});button("Back",492,58,136,()->{if(player!=null)player=null;else if(variant!=null)variant=null;else{onClose();return;}page=offset=0;request();rebuildWidgets();});
        button("Previous",32,335,120,()->{if(page>0)page--;else if(offset>0){offset=Math.max(0,offset-100);request();}rebuildWidgets();});
        button("Next",164,335,120,()->{int size=result==null?0:player!=null?result.entries().size():variant!=null?result.players().size():result.items().size();if((page+1)*8<size){page++;rebuildWidgets();}else if(result!=null&&result.more()){offset+=100;page=0;request();}});
        button("Done",514,335,114,this::onClose);
        if(!started){started=true;request();}
    }
    @Override protected void paint(GuiGraphicsExtractor g,int x,int y,float delta){panel(g,16,16,628,356,WorkbenchTheme.SURFACE);text(g,"Trending | "+(sold?"Sold":"Purchased")+" units | "+(days==1?"last 24 hours":"last "+days+" days"),32,30,WorkbenchTheme.TEXT);
        text(g,variant==null?"Top 20 exact item variants | click to see players":player==null?"Players trading this variant | click for matching transactions":"Matching transactions, including archived player history",32,94,WorkbenchTheme.MUTED);
        if(result!=null&&!busy){
            for(int row=0;row<8;row++){int i=page*8+row,yy=117+row*23;String left,right;
                if(player!=null){if(i>=result.entries().size())break;var entry=result.entries().get(i);left=JournalDates.display(entry.time(),java.time.ZoneId.systemDefault())+" | "+TransactionJournal.activity(entry);right=entry.quantity()+" | "+currency+String.format(Locale.US,"%,.2f",TransactionJournal.signedTotal(entry));}
                else if(variant!=null){if(i>=result.players().size())break;var entry=result.players().get(i);left=entry.name();right=entry.quantity()+" units";}
                else{if(i>=result.items().size())break;var entry=result.items().get(i);left=(i+1)+". "+entry.name();right=entry.quantity()+" units";}
                g.fill(30,yy,630,yy+22,WorkbenchTheme.ROW);text(g,font.plainSubstrByWidth(left,455),36,yy+7,WorkbenchTheme.TEXT);text(g,font.plainSubstrByWidth(right,120),500,yy+7,WorkbenchTheme.TEAL);
                if(x>=30&&x<630&&y>=yy&&y<yy+22)g.setTooltipForNextFrame(font,font.split(Component.literal(left+"\n"+right),400),(int)(originX+x*scale),(int)(originY+y*scale));
            }
        }
        text(g,font.plainSubstrByWidth(message.isBlank()?"Page "+(page+1)+" | Full journal quantities. Resetting player history does not erase audit evidence.":message,595),32,307,WorkbenchTheme.MUTED);
    }
    @Override protected boolean click(double x,double y,int button){if(busy||result==null||button!=com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT||x<30||x>=630||y<117||y>=301)return false;int index=page*8+(int)((y-117)/23);if(player!=null)return true;
        if(variant==null){if(index>=result.items().size())return false;variant=result.items().get(index).variant();}else{if(index>=result.players().size())return false;player=result.players().get(index).uuid();}page=offset=0;request();rebuildWidgets();return true;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
