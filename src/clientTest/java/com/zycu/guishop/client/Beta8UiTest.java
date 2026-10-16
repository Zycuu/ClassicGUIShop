package com.zycu.guishop.client;

import com.zycu.guishop.admin.AdminDocument;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.*;
import java.util.*;

/** Actual client saves and acknowledgements against an isolated local server. */
public final class Beta8UiTest implements ClientModInitializer {
    private int step,delay=80,age;private boolean done,hidden;
    private final Path report=Path.of(System.getProperty("guishop.beta8UiReport"));
    private AdminScreen parent;
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(Minecraft client) {
        if(done)return;
        try {
            if(!hidden){client.options.pauseOnLostFocus=false;hidden=true;}
            if(++age>6000)throw new AssertionError("Beta8 GUI timeout at step "+step);
            client.gui.toastManager().clear();
            if(delay-->0)return;
            var screen=client.gui.screen();
            if(step==1&&screen instanceof DisconnectedScreen)throw new AssertionError("Fixture disconnected: "+get(screen,"reason"));
            switch(step) {
                case 0 -> {if(client.gui.overlay()!=null||screen==null)return;client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);ConnectScreen.startConnecting(screen,client,ServerAddress.parseString("127.0.0.1:25585"),new ServerData("Beta8 isolated GUI","127.0.0.1:25585",ServerData.Type.OTHER),false,null);next(40);}
                case 1 -> {if(client.player==null)return;client.gui.setScreen(null);client.getConnection().sendCommand("adminshop");next(30);}
                case 2 -> {if(!(screen instanceof AdminScreen))return;parent=(AdminScreen)screen;click(parent,"Settings");click(parent,"Economy");next(8);}
                case 3 -> {check(config().villagerTradeLoopFix,"Starts On");clickPrefix(parent,"Villager Trade Loop Fix");check(!config().villagerTradeLoopFix,"Toggle edits draft");click(parent,"Save changes");next(20);}
                case 4 -> {if((boolean)get(parent,"busy"))return;check(!config().villagerTradeLoopFix,"Server acknowledges saved Off");check(get(parent,"status").toString().contains("original payments restored for 2 villagers"),"Save acknowledgement confirms actual NPC refresh");capture(client,"beta8-economy-saved-off.png");clickPrefix(parent,"Villager Trade Loop Fix");click(parent,"Save changes");next(20);}
                case 5 -> {if((boolean)get(parent,"busy"))return;check(config().villagerTradeLoopFix,"Server acknowledges saved On");check(get(parent,"status").toString().contains("2 villagers refreshed, 2 offers adjusted"),"Save On reports recalculation");capture(client,"beta8-economy-saved-on.png");click(parent,"Reload shop");next(20);}
                case 6 -> {if((boolean)get(parent,"busy"))return;check(config().villagerTradeLoopFix,"Reload retains saved setting");finish(client,true,"Real client toggle, Save Off/On, server refresh acknowledgements and Reload passed");}
            }
        }catch(Throwable error){error.printStackTrace();try{finish(client,false,error.toString());}catch(Exception nested){nested.printStackTrace();client.stop();}}
    }
    private com.zycu.guishop.ShopConfig config() throws Exception {return ((AdminDocument)get(parent,"draft")).config;}
    private void next(int ticks){step++;delay=ticks;}
    private void finish(Minecraft client,boolean passed,String message) throws Exception {
        done=true;Files.createDirectories(report.getParent());Files.writeString(report,AdminDocument.JSON.toJson(Map.of("passed",passed,"message",message,"steps",step,"isolatedClient",true,"serverDataSaved",passed)));
        System.out.println("[Beta8 UI test] "+(passed?"PASS: ":"FAIL: ")+message);client.stop();
    }
    private static Object get(Object object,String name) throws Exception {for(var type=object.getClass();type!=null;type=type.getSuperclass()){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(name);}
    private static void click(Screen screen,String label) {for(var child:screen.children())if(child instanceof Button button&&button.getMessage().getString().replace("* ","").replace("> ","").equals(label)){press((WorkbenchScreen)screen,button);return;}throw new AssertionError("Missing button "+label);}
    private static void clickPrefix(Screen screen,String prefix){for(var child:screen.children())if(child instanceof Button b&&b.getMessage().getString().startsWith(prefix)){press((WorkbenchScreen)screen,b);return;}throw new AssertionError("Missing toggle "+prefix);}
    private static void press(WorkbenchScreen screen,Button button){screen.geometry();var event=new MouseButtonEvent(screen.originX+(button.getX()+button.getWidth()/2.0+screen.contentOffsetX())*screen.scale,screen.originY+(button.getY()+button.getHeight()/2.0)*screen.scale,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));screen.mouseClicked(event,false);screen.mouseReleased(event);}
    private static void capture(Minecraft client,String name){Screenshot.grab(client.gameDirectory,"v3-"+name,client.gameRenderer.mainRenderTarget(),1,message->System.out.println("[Beta8 UI test] Screenshot "+name));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
