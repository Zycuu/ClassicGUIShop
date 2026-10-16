package com.zycu.guishop.client;

import com.zycu.guishop.ShopModes;
import com.zycu.guishop.admin.AdminDocument;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

/** Short, hidden, isolated real-client review of the changed screens and controls. */
public final class Beta7UiTest implements ClientModInitializer {
    private int step,delay=80,age;private boolean done,hidden;
    private final Path report=Path.of(System.getProperty("guishop.beta7UiReport"));
    private AdminScreen parent;
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(Minecraft client) {
        if(done)return;
        try {
            if(!hidden){client.options.pauseOnLostFocus=false;hidden=true;}
            if(++age>6000)throw new AssertionError("Beta7 GUI timeout at step "+step);
            client.gui.toastManager().clear();
            if(delay-->0)return;
            var screen=client.gui.screen();
            if(step==1&&screen instanceof DisconnectedScreen)throw new AssertionError("Isolated fixture disconnected: "+get(screen,"reason"));
            switch(step) {
                case 0 -> {if(client.gui.overlay()!=null||screen==null)return;client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);ConnectScreen.startConnecting(screen,client,ServerAddress.parseString("127.0.0.1:25582"),new ServerData("Beta7 isolated GUI","127.0.0.1:25582",ServerData.Type.OTHER),false,null);next(60);}
                case 1 -> {if(client.player==null)return;client.gui.setScreen(null);client.getConnection().sendCommand("adminshop");next(30);}
                case 2 -> {if(!(screen instanceof AdminScreen))return;parent=(AdminScreen)screen;click(screen,"Settings");click(screen,"Economy");next(8);}
                case 3 -> {capture(client,"beta7-economy.png");var draft=(AdminDocument)get(parent,"draft");check(draft.config.villagerTradeLoopFix,"Economy toggle defaults On");clickPrefix(parent,"Villager Trade Loop Fix");check(!draft.config.villagerTradeLoopFix,"Native click switches the unsaved toggle Off");clickPrefix(parent,"Villager Trade Loop Fix");check(draft.config.villagerTradeLoopFix,"Native click switches it back On");click(parent,"Shop Modes");next(30);}
                case 4 -> {if((boolean)get(parent,"busy"))return;click(parent,"Cash Mobs");next(8);}
                case 5 -> {capture(client,"beta7-cash-mobs.png");check(fontWidth(parent,"Default reward override")<=174,"Reward label fits its column");click(parent,"Edit mob rewards");next(8);}
                case 6 -> {check(screen instanceof ModeOffersScreen,"Reward editor opened");capture(client,"beta7-mob-rewards.png");var shown=(List<ShopModes.Offer>)get(screen,"shown");check(shown.stream().noneMatch(o->o.id().contains("armor_stand")||o.id().equals("minecraft:arrow")),"Reward editor excludes decorations and projectiles");click(screen,"Add detected mobs");next(8);}
                case 7 -> {capture(client,"beta7-detected-mobs-empty.png");check((boolean)get(screen,"adding"),"Detected selector uses the same menu");click(screen,"Back to rewards");click(screen,"Cancel");
                    var content=(ShopModes.Catalog)get(parent,"modeCatalog");var entities=new ArrayList<>(content.entities());entities.add(new ShopModes.Offer("guishop_ui:detected_mob","Detected living mob fixture",0));
                    var replacement=new ShopModes.Catalog(entities,content.advancements());var field=AdminScreen.class.getDeclaredField("modeCatalog");field.setAccessible(true);field.set(parent,replacement);click(parent,"Edit mob rewards");click(client.gui.screen(),"Add detected mobs");next(8);}
                case 8 -> {capture(client,"beta7-detected-mobs-add.png");check(((List<?>)get(screen,"shown")).size()==1,"Detected source row is available");click(screen,"Add");click(screen,"Back to rewards");set(screen,"Search rewards","guishop_ui:detected_mob");next(8);}
                case 9 -> {capture(client,"beta7-added-mob.png");check(((List<?>)get(screen,"shown")).size()==1,"Added mob returns to the rewards list");set(screen,"Payout guishop_ui:detected_mob","13");click(screen,"Update");check(((AdminDocument)get(parent,"draft")).config.modes.mobPrices.get("guishop_ui:detected_mob")==13,"Update changes only the workbench draft");click(parent,"Presets");click(parent,"Full Vanilla Normal");click(parent,"Preview preset");next(30);}
                case 10 -> {if((boolean)get(parent,"busy")||get(parent,"presetPlan")==null)return;click(parent,"Load into editor");click(client.gui.screen(),"Confirm");next(12);}
                case 11 -> {set(parent,"Search listings","Combat");next(8);}
                case 12 -> {hit(parent,"tile:category","combat");set(parent,"Search listings","arrow");next(8);}
                case 13 -> {capture(client,"beta7-arrow-order.png");hit(parent,"tile:bookgroup","arrows");set(parent,"Search listings","");next(8);}
                case 14 -> {capture(client,"beta7-arrow-families.png");hit(parent,"tile:bookgroup","arrow:minecraft:swiftness");next(8);}
                case 15 -> {check(count(parent,"tile:item")==3,"Simulator shows the three speed variants together");capture(client,"beta7-arrow-variants.png");finish(client,true,"Economy toggle, label fit, living-only rewards, same-menu detected addition, draft payout, arrow order and families passed");}
            }
        }catch(Throwable error){error.printStackTrace();try{finish(client,false,error.toString());}catch(Exception nested){nested.printStackTrace();client.stop();}}
    }
    private void next(int ticks){step++;delay=ticks;}
    private void finish(Minecraft client,boolean passed,String message) throws Exception {
        done=true;Files.createDirectories(report.getParent());Files.writeString(report,AdminDocument.JSON.toJson(Map.of("passed",passed,"message",message,"steps",step,"isolatedClient",true,"serverDataSaved",false)));
        System.out.println("[Beta7 UI test] "+(passed?"PASS: ":"FAIL: ")+message);client.stop();
    }
    private static Object get(Object object,String name) throws Exception {for(var type=object.getClass();type!=null;type=type.getSuperclass()){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(name);}
    private static int fontWidth(Screen screen,String text) throws Exception {return ((net.minecraft.client.gui.Font)get(screen,"font")).width(text);}
    private static void click(Screen screen,String label) throws Exception {
        if(label.equals("Confirm")&&screen instanceof EditorDialog)label=(String)get(screen,"action");
        for(var child:screen.children())if(child instanceof Button button&&button.getMessage().getString().replace("* ","").replace("> ","").equals(label)){press((WorkbenchScreen)screen,button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0);return;}
        throw new AssertionError("Missing button "+label);
    }
    private static void clickPrefix(Screen screen,String prefix){for(var child:screen.children())if(child instanceof Button b&&b.getMessage().getString().startsWith(prefix)){press((WorkbenchScreen)screen,b.getX()+b.getWidth()/2.0,b.getY()+b.getHeight()/2.0);return;}throw new AssertionError("Missing toggle "+prefix);}
    private static void set(Screen screen,String label,String value){for(var child:screen.children())if(child instanceof EditBox input&&input.getMessage().getString().equals(label)){input.setValue(value);return;}throw new AssertionError("Missing field "+label);}
    private static void press(WorkbenchScreen screen,double x,double y){screen.geometry();var event=new MouseButtonEvent(screen.originX+(x+screen.contentOffsetX())*screen.scale,screen.originY+y*screen.scale,new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,0));screen.mouseClicked(event,false);screen.mouseReleased(event);}
    private static void hit(AdminScreen screen,String kind,String id) throws Exception {for(var hit:(List<?>)get(screen,"hits"))if(get(hit,"kind").equals(kind)&&get(hit,"id").equals(id)){press(screen,(int)get(hit,"x")+(int)get(hit,"w")/2.0,(int)get(hit,"y")+(int)get(hit,"h")/2.0);return;}throw new AssertionError("Missing tile "+kind+" "+id);}
    private static long count(AdminScreen screen,String kind) throws Exception {int total=0;for(var hit:(List<?>)get(screen,"hits"))if(get(hit,"kind").equals(kind))total++;return total;}
    private static void capture(Minecraft client,String name){Screenshot.grab(client.gameDirectory,"review-v5-"+name,client.gameRenderer.mainRenderTarget(),1,message->System.out.println("[Beta7 UI test] Screenshot "+name));}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
