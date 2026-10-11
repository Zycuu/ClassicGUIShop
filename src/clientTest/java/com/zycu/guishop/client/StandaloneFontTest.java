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
                client.gui.setScreen(new AdminNoticeScreen("Ask the Server Owner for permission to use the Adminshop tools",client.gui.screen()));
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
        stage=4;Files.createDirectories(report.getParent());Files.writeString(report,AdminDocument.JSON.toJson(Map.of("passed",passed,"message",message,"fullFabricApiInstalled",FabricLoader.getInstance().isModLoaded("fabric-api"))));System.out.println("[Standalone font] "+message);client.stop();
    }
}
