package com.zycu.guishop.client;

import com.zycu.guishop.admin.AdminDocument;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import java.nio.file.*;
import java.util.*;

/** Real rendering with only the modules shipped by ClassicGUIShop, including after resource reload. */
public final class StandaloneFontTest implements ClientModInitializer {
    private int ticks,stage;
    private boolean hidden;
    private final Path report=Path.of(System.getProperty("guishop.fontReport"));
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(Minecraft client){
        try {
            if(!hidden){Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_HideWindow",long.class).invoke(null,client.getWindow().handle());client.options.pauseOnLostFocus=false;hidden=true;}
            if(++ticks>3000)throw new AssertionError("Font test timed out");
            if(client.gui.overlay()!=null||ticks<100)return;
            if(stage==0){
                if(FabricLoader.getInstance().isModLoaded("fabric-api"))throw new AssertionError("Full Fabric API masks standalone packaging problems");
                if(Boolean.getBoolean("guishop.networkSmoke")){
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(client.gui.screen(),client,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25588"),new net.minecraft.client.multiplayer.ServerData("Release fixture","127.0.0.1:25588",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
                    stage=10;return;
                }
                client.gui.setScreen(new AdminNoticeScreen("Ask the Server Owner for permission to use the Adminshop tools",client.gui.screen()));
                stage=1;ticks=100;return;
            }
            if(stage==10){if(client.player==null)return;client.gui.setScreen(null);client.getConnection().sendCommand("shop");stage=11;return;}
            if(stage==11){if(!(client.player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu))return;client.player.closeContainer();client.getConnection().sendCommand("adminshop");stage=12;ticks=100;return;}
            if(stage==12){
                if(!(client.gui.screen() instanceof AdminScreen screen)||ticks<125)return;
                var field=AdminScreen.class.getDeclaredField("draft");field.setAccessible(true);var document=(AdminDocument)field.get(screen);
                if(document.config.categories.isEmpty())throw new AssertionError("Server snapshot has no starter shop");
                Screenshot.grab(client.gameDirectory,"network-builder-"+report.getFileName()+".png",client.gameRenderer.mainRenderTarget(),1,message->{});
                boolean clicked=false;for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals("Detected Content")){button.onPress(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(0,0)));clicked=true;break;}
                if(!clicked)throw new AssertionError("Missing detected tab");stage=13;return;
            }
            if(stage==13){
                if(!(client.gui.screen() instanceof AdminScreen screen))return;
                var field=AdminScreen.class.getDeclaredField("detectedData");field.setAccessible(true);var page=(com.zycu.guishop.admin.DetectedContent.Page)field.get(screen);
                if(page==null||!page.ready())return;if(page.sources().isEmpty())throw new AssertionError("Detected source packet empty");
                stage=1;ticks=100;return;
            }
            if(stage==1&&ticks>125){
                Screenshot.grab(client.gameDirectory,"before-reload-"+report.getFileName()+".png",client.gameRenderer.mainRenderTarget(),1,message->{});
                verify(client);
                stage=2;
                client.reloadResourcePacks().thenRun(()->client.execute(()->{stage=3;ticks=100;}));
            }
            if(stage==3&&ticks>125){verify(client);Screenshot.grab(client.gameDirectory,"after-reload-"+report.getFileName()+".png",client.gameRenderer.mainRenderTarget(),1,message->{});finish(client,true,"Bundled font and glyphs render without full Fabric API, before and after resource reload");}
        }catch(Throwable error){error.printStackTrace();try{finish(client,false,error.toString());}catch(Exception nested){nested.printStackTrace();client.stop();}}
    }
    private void verify(Minecraft client){
        for(String path:List.of("font/ui.json","font/inter-regular.ttf"))if(client.getResourceManager().getResource(Identifier.fromNamespaceAndPath("guishop",path)).isEmpty())throw new AssertionError("Missing mod resource: "+path);
        var provider=((com.zycu.guishop.client.mixin.EditorFontAccess)client.font).guishop$provider();
        var source=provider.glyphs(new FontDescription.Resource(Identifier.fromNamespaceAndPath("guishop","ui")));
        var missing=provider.glyphs(new FontDescription.Resource(FontManager.MISSING_FONT)).getGlyph('A');
        for(int character:"ClassicGUIShop 123 $ é Ω".codePoints().filter(c->c!=' ').toArray())if(source.getGlyph(character)==missing)throw new AssertionError("Missing workbench glyph: "+character);
        if(WorkbenchTheme.font().width("iii")>=WorkbenchTheme.font().width("WWW"))throw new AssertionError("Workbench renders missing glyphs instead of proportional font");
    }
    private void finish(Minecraft client,boolean passed,String message)throws Exception{
        stage=4;Files.createDirectories(report.getParent());Files.writeString(report,AdminDocument.JSON.toJson(Map.of("passed",passed,"message",message,"networkSmoke",Boolean.getBoolean("guishop.networkSmoke"),"fullFabricApiInstalled",FabricLoader.getInstance().isModLoaded("fabric-api"))));System.out.println("[Standalone font] "+message);client.stop();
    }
}
