package com.zycu.guishop.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.Consumer;
/** One consistent choice popup for colors and permission levels. */
final class ChoiceDialog extends WorkbenchScreen {
    record Option(String value,String label,Integer color){}
    private final Screen parent;private final List<Option> options;private final Consumer<String> accepted;private String selected;private int top,optionsY,footerY,buttonsY,height;
    ChoiceDialog(Screen parent,String title,String current,List<Option> options,Consumer<String> accepted){super(title);this.parent=parent;this.selected=current;this.options=options;this.accepted=accepted;}
    static List<Option> colors(){return List.of("black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white").stream().map(value->new Option(value,label(value),color(value))).toList();}
    private static int color(String name){return switch(name){case "black"->0x000000;case "dark_blue"->0x0000AA;case "dark_green"->0x00AA00;case "dark_aqua"->0x00AAAA;case "dark_red"->0xAA0000;case "dark_purple"->0xAA00AA;case "gold"->0xFFAA00;case "gray"->0xAAAAAA;case "dark_gray"->0x555555;case "blue"->0x5555FF;case "green"->0x55FF55;case "aqua"->0x55FFFF;case "red"->0xFF5555;case "light_purple"->0xFF55FF;case "yellow"->0xFFFF55;default->0xFFFFFF;};}
    static String label(String value){String name=value.replace('_',' ');return name.isEmpty()?name:Character.toUpperCase(name.charAt(0))+name.substring(1);}
    static List<Option> roles(){return List.of(new Option("0","Everyone",null),new Option("1","Moderator (OP 1+)",null),new Option("2","Gamemaster (OP 2+)",null),new Option("3","Admin (OP 3+)",null),new Option("4","Owner (OP 4)",null));}
    @Override protected Screen modalParent(){return parent;}
    @Override protected void init(){int rows=(options.size()+1)/2;height=96+rows*28;top=(H-height)/2;optionsY=top+40;footerY=optionsY+rows*28;buttonsY=footerY+25;for(int i=0;i<options.size();i++){var option=options.get(i);int x=104+(i%2)*230,y=optionsY+(i/2)*28;button((selected.equals(option.value())?"> ":"")+option.label(),x,y,218,()->{selected=option.value();rebuildWidgets();});}button("Cancel",222,buttonsY,100,this::onClose);button("Update",338,buttonsY,100,()->{accepted.accept(selected);minecraft.gui.setScreen(parent);});}
    @Override protected void paint(GuiGraphicsExtractor g,int x,int y,float delta){panel(g,86,top,488,height,WorkbenchTheme.SURFACE);text(g,font.plainSubstrByWidth(title.getString(),450),104,top+18,WorkbenchTheme.TEXT);text(g,"Choose an option, then Update. Save changes in the workbench.",104,footerY,WorkbenchTheme.MUTED);}
    @Override protected void afterWidgets(GuiGraphicsExtractor g,int x,int y,float delta){for(int i=0;i<options.size();i++){var option=options.get(i);if(option.color()!=null){int xx=110+(i%2)*230,yy=optionsY+5+(i/2)*28;WorkbenchTheme.rounded(g,xx-1,yy-1,12,12,2,WorkbenchTheme.TEXT);WorkbenchTheme.rounded(g,xx,yy,10,10,2,0xFF000000|option.color());}}if(y>=top+14&&y<top+32)g.setTooltipForNextFrame(font,title,(int)(originX+x*scale),(int)(originY+y*scale));}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
