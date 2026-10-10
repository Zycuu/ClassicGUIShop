package com.zycu.guishop.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
/** Creative-style row scrollbar shared by the editor's long lists. */
final class WorkbenchScroll {
    final int x,y,height,visible; int row,total; boolean dragging; private final Runnable changed;
    WorkbenchScroll(int x,int y,int height,int visible,Runnable changed){this.x=x;this.y=y;this.height=height;this.visible=visible;this.changed=changed;}
    int max(){return Math.max(0,total-visible);}
    void count(int count){total=count;row=Math.clamp(row,0,max());}
    void move(int next){int value=Math.clamp(next,0,max());if(value!=row){row=value;changed.run();}}
    boolean click(double mx,double my,int button){if(button!=com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT||mx<x||mx>=x+14||my<y||my>=y+height||max()==0)return false;dragging=true;drag(my);return true;}
    void drag(double my){move((int)Math.round(Math.clamp((my-y-10)/(height-20),0,1)*max()));}
    boolean wheel(double mx,double my,double amount){if(mx<24||mx>=x+14||my<y||my>=y+height||amount==0)return false;move(row-(int)Math.signum(amount)*3);return true;}
    void paint(GuiGraphicsExtractor g){WorkbenchTheme.rounded(g,x,y,14,height,3,WorkbenchTheme.INPUT);int thumb=y+(max()==0?0:Math.round((height-20)*row/(float)max()));g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,net.minecraft.resources.Identifier.withDefaultNamespace("container/creative_inventory/scroller"+(max()==0?"_disabled":"")),x,thumb,14,20);}
}
