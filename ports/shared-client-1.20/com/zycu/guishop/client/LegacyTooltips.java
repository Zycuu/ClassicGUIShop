package com.zycu.guishop.client;

import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** Defer old immediate tooltips until the editor's canvas transform has been restored. */
final class LegacyTooltips {
    private static Runnable pending;
    static void queue(GuiGraphics g,Font font,Component text,int x,int y){pending=()->g.renderTooltip(font,text,x,y);}
    static void queue(GuiGraphics g,Font font,ItemStack item,int x,int y){pending=()->g.renderTooltip(font,item,x,y);}
    static void queue(GuiGraphics g,Font font,List<FormattedCharSequence> text,int x,int y){pending=()->g.renderTooltip(font,text,x,y);}
    static void queue(GuiGraphics g,Component text,int x,int y){queue(g,WorkbenchTheme.font(),text,x,y);}
    static void flush(){Runnable next=pending;pending=null;if(next!=null)next.run();}
}
