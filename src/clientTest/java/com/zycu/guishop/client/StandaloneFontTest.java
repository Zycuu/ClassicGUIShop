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
    private AdminScreen bulkParent;
    private List<String> removedBulkIds=List.of();
    private String repricedBulkId="", repricedStack="", bulkRevision="";
    private int originalListingCount, originalCategoryCount;
    private final Path report=Path.of(System.getProperty("guishop.fontReport"));
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(Minecraft client){
        try {
            if(!hidden){client.options.pauseOnLostFocus=false;hidden=true;}
            if(++ticks>3000)throw new AssertionError("Font test timed out");
            if(client.gui.overlay()!=null||ticks<100)return;
            client.gui.toastManager().clear();
            if(stage>=30){commitTick(client);return;}
            if(stage==20&&ticks>250){
                Screenshot.grab(client.gameDirectory,"bulk-adjust-"+report.getFileName()+".png",client.gameRenderer.mainRenderTarget(),1,message->{});
                client.gui.setScreen(bulkParent);stage=12;ticks=100;return;
            }
            if(stage==0){
                client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                if(FabricLoader.getInstance().isModLoaded("fabric-api"))throw new AssertionError("Full Fabric API masks standalone packaging problems");
                if(Boolean.getBoolean("guishop.networkSmoke")){
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(client.gui.screen(),client,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25588"),new net.minecraft.client.multiplayer.ServerData("Release fixture","127.0.0.1:25588",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
                    stage=10;return;
                }
                client.gui.setScreen(new AdminNoticeScreen("Ask the Server Owner for permission to use the Adminshop tools",client.gui.screen()));
                stage=1;ticks=100;return;
            }
            if(stage==14){
                if(!(client.gui.screen() instanceof AdminScreen screen))return;
                var field=AdminScreen.class.getDeclaredField("modeCatalog");field.setAccessible(true);
                var catalog=(com.zycu.guishop.ShopModes.Catalog)field.get(screen);if(catalog==null)return;
                if(catalog.advancements().isEmpty()||catalog.advancements().stream().anyMatch(entry->entry.id().contains(":recipes/")))throw new AssertionError("Advancement catalog includes recipe unlocks or is empty");
                var draftField=AdminScreen.class.getDeclaredField("draft");draftField.setAccessible(true);
                var document=(AdminDocument)draftField.get(screen);String recipe="minecraft:recipes/building_blocks/oak_planks";
                document.config.modes.advancementPrices.put(recipe,500.0);
                var rewards=new ModeOffersScreen(screen,document.config.modes,catalog,true);
                var offers=ModeOffersScreen.class.getDeclaredField("catalog");offers.setAccessible(true);
                for(var entry:(java.util.List<?>)offers.get(rewards))if(((com.zycu.guishop.ShopModes.Offer)entry).id().equals(recipe))throw new AssertionError("Saved override revived recipe in rewards editor");
                var preserved=ModeOffersScreen.class.getDeclaredField("preserved");preserved.setAccessible(true);
                if(!((java.util.Map<?,?>)preserved.get(rewards)).containsKey(recipe))throw new AssertionError("Excluded override was not preserved");
                client.gui.setScreen(rewards);stage=1;ticks=100;return;
            }
            if(stage==10){if(client.player==null)return;client.gui.setScreen(null);client.getConnection().sendCommand("shop");stage=11;return;}
            if(stage==11){if(!(client.player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu))return;client.player.closeContainer();client.getConnection().sendCommand("adminshop");stage=12;ticks=100;return;}
            if(stage==12){
                if(!(client.gui.screen() instanceof AdminScreen screen)||ticks<125)return;
                var field=AdminScreen.class.getDeclaredField("draft");field.setAccessible(true);var document=(AdminDocument)field.get(screen);
                if(document.config.categories.isEmpty())throw new AssertionError("Server snapshot has no starter shop");
                if(Boolean.getBoolean("guishop.bulkSmoke")&&bulkParent==null){bulkParent=screen;verifyBulk(client);stage=20;ticks=100;return;}
                Screenshot.grab(client.gameDirectory,"network-builder-"+report.getFileName()+".png",client.gameRenderer.mainRenderTarget(),1,message->{});
                boolean clicked=false;for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals("Detected Content")){button.onPress(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(0,0)));clicked=true;break;}
                if(!clicked)throw new AssertionError("Missing detected tab");stage=13;return;
            }
            if(stage==13){
                if(!(client.gui.screen() instanceof AdminScreen screen))return;
                var field=AdminScreen.class.getDeclaredField("detectedData");field.setAccessible(true);var page=(com.zycu.guishop.admin.DetectedContent.Page)field.get(screen);
                if(page==null||!page.ready())return;if(page.sources().isEmpty())throw new AssertionError("Detected source packet empty");
                if(!AdminShopClient.request("mode_catalog","",""))return;
                stage=14;return;
            }
            if(stage==1&&ticks>125){
                Screenshot.grab(client.gameDirectory,"before-reload-"+report.getFileName()+".png",client.gameRenderer.mainRenderTarget(),1,message->{});
                verify(client);
                stage=2;
                client.reloadResourcePacks().thenRun(()->client.execute(()->{stage=3;ticks=100;}));
            }
            if(stage==3&&ticks>125){
                verify(client);Screenshot.grab(client.gameDirectory,"after-reload-"+report.getFileName()+".png",client.gameRenderer.mainRenderTarget(),1,message->{});
                if(Boolean.getBoolean("guishop.bulkCommitSmoke")){if(!AdminShopClient.request("open","",""))return;stage=30;ticks=100;return;}
                finish(client,true,"Bundled font and glyphs render without full Fabric API, before and after resource reload");
            }
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
    private static Object field(Object object,String name)throws Exception{
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void press(net.minecraft.client.gui.screens.Screen screen,String name){
        for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals(name)){
            check(button.active,"Button inactive: "+name);
            if(screen instanceof PriceAdjustScreen workbench){
                workbench.geometry();
                double x=workbench.originX+(button.getX()+button.getWidth()/2.0)*workbench.scale;
                double y=workbench.originY+(button.getY()+button.getHeight()/2.0)*workbench.scale;
                var event=new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));
                check(workbench.mouseClicked(event,false),"Mouse click accepted: "+name);workbench.mouseReleased(event);
            }else button.onPress(new net.minecraft.client.input.MouseButtonEvent(0,0,new net.minecraft.client.input.MouseButtonInfo(0,0)));
            return;
        }
        throw new AssertionError("Missing button: "+name);
    }
    private static void input(net.minecraft.client.gui.screens.Screen screen,String label,String value){
        for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.EditBox box&&box.getMessage().getString().equals(label)){box.setValue(value);return;}
        throw new AssertionError("Missing input: "+label);
    }
    @SuppressWarnings("unchecked")
    private void commitTick(Minecraft client)throws Exception{
        if(ticks<125)return;
        if(stage==30){
            if(!(client.gui.screen() instanceof AdminScreen parent))return;
            var config=((AdminDocument)field(parent,"draft")).config;
            var entries=com.zycu.guishop.admin.BulkListings.entries(config);var a=entries.get(0);
            var b=entries.stream().filter(e->e.category()!=a.category()).findFirst().orElseThrow();
            var c=entries.stream().filter(e->!e.equals(a)&&!e.equals(b)).findFirst().orElseThrow();
            removedBulkIds=List.of(a.item().listingId,b.item().listingId);repricedBulkId=c.item().listingId;repricedStack=AdminDocument.JSON.toJson(c.item().stack);
            originalListingCount=entries.size();originalCategoryCount=config.categories.size();bulkRevision=field(parent,"revision").toString();
            var menu=new PriceAdjustScreen(parent,config,a.category().id);client.gui.setScreen(menu);
            var picks=(Set<com.zycu.guishop.admin.BulkListings.Entry>)field(menu,"selection");picks.addAll(List.of(a,b,c));
            var update=PriceAdjustScreen.class.getDeclaredMethod("updateButtons");update.setAccessible(true);update.invoke(menu);
            input(menu,"New buy price","1000000000");input(menu,"New sell price","0");press(menu,"Set prices");
            picks.clear();picks.addAll(List.of(a,b));update.invoke(menu);press(menu,"Remove selected");press(client.gui.screen(),"Remove 2 listings");press(menu,"Done");
            stage=31;ticks=100;return;
        }
        if(!(client.gui.screen() instanceof AdminScreen parent))return;
        if(stage==31){press(parent,"Save changes");stage=32;ticks=100;return;}
        if((boolean)field(parent,"busy"))return;
        check(!bulkRevision.equals(field(parent,"revision")),"Server acknowledged new bulk-save revision: "+field(parent,"status"));
        var config=((AdminDocument)field(parent,"draft")).config;var entries=com.zycu.guishop.admin.BulkListings.entries(config);
        check(entries.size()==originalListingCount-2&&config.categories.size()==originalCategoryCount,"Server retains bulk removals and categories");
        check(entries.stream().noneMatch(e->removedBulkIds.contains(e.item().listingId)),"Exact deleted IDs absent from server snapshot");
        var item=entries.stream().map(com.zycu.guishop.admin.BulkListings.Entry::item).filter(i->i.listingId.equals(repricedBulkId)).findFirst().orElseThrow();
        check(item.buy==1e9&&item.sell==0&&AdminDocument.JSON.toJson(item.stack).equals(repricedStack),"Server retains selected prices and original stack data");
        if(stage==32){if(!AdminShopClient.request("open","",""))return;stage=33;ticks=100;return;}
        finish(client,true,"Bulk selection, confirmed removal, Save changes acknowledgement and refreshed server snapshot passed; native data and fonts preserved");
    }
    private static void verifyBulk(Minecraft client)throws Exception{
        var actual=(AdminScreen)client.gui.screen();
        var actualDraft=(AdminDocument)field(actual,"draft");
        var building=actualDraft.config.category("building_blocks");
        check(building!=null,"Default preset includes Building Blocks");
        actual.geometry();
        var categoryClick=new net.minecraft.client.input.MouseButtonEvent(actual.originX+(30+actual.contentOffsetX())*actual.scale,actual.originY+140*actual.scale,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));
        check(actual.mouseClicked(categoryClick,false),"Builder category mouse-down accepted");actual.mouseReleased(categoryClick);
        check(field(actual,"categoryId").equals("building_blocks"),"Builder mouse click selects Building Blocks");
        press(actual,"Bulk Adjust");
        var actualBulk=(PriceAdjustScreen)client.gui.screen();
        check(field(actualBulk,"categoryId").equals("building_blocks"),"Bulk Adjust retains builder category");
        press(actualBulk,"Select whole tab");
        var actualSelection=(Set<?>)field(actualBulk,"selection");
        check(actualSelection.size()==building.items.size(),"Default preset Select all count matches Building Blocks only");
        check(actualSelection.stream().allMatch(entry->((com.zycu.guishop.admin.BulkListings.Entry)entry).category()==building),"Default preset mouse selection excludes all other categories");
        press(actualBulk,"Colored Blocks");
        check(((net.minecraft.client.gui.components.Button)field(actualBulk,"selectAll")).getMessage().getString().equals("Select whole tab"),"Colored Blocks remains unselected after Building Blocks Select all");
        press(actualBulk,"Building Blocks");press(actualBulk,"Deselect whole tab");
        check(actualSelection.isEmpty(),"Default preset current-tab deselection clears Building Blocks only");
        press(actualBulk,"Select all listings");
        check(actualSelection.size()==com.zycu.guishop.admin.BulkListings.entries(actualDraft.config).size(),"Explicit header button selects the entire default shop");
        check(field(actualBulk,"categoryId").equals("building_blocks"),"Global selection retains the current category tab");
        press(actualBulk,"Deselect whole tab");
        check(actualSelection.stream().noneMatch(entry->((com.zycu.guishop.admin.BulkListings.Entry)entry).category()==building),"Whole-tab deselection after global selection excludes only Building Blocks");
        press(actualBulk,"Clear selection");
        client.gui.setScreen(actual);
        var doc=new AdminDocument();doc.config=new com.zycu.guishop.ShopConfig();
        for(int c=0;c<12;c++){
            var category=new com.zycu.guishop.ShopConfig.Category();category.id="bulk_"+c;category.name=c==0?"Stew variants":c==1?"Custom food":"Empty category "+c;category.icon="minecraft:suspicious_stew";
            if(c<2)for(int i=0;i<95;i++){
                var item=new com.zycu.guishop.ShopConfig.ShopItem();item.item="minecraft:suspicious_stew";item.listingId="bulk_"+c+"_variant_"+i;item.name="Stew variant "+i;item.buy=10;item.sell=2;
                var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SUSPICIOUS_STEW);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal(item.name));
                item.stack=com.zycu.guishop.ItemStackData.encode(stack,client.level.registryAccess());category.items.add(item);
            }
            doc.config.categories.add(category);
        }
        String untouched=AdminDocument.JSON.toJson(doc);
        var parent=new AdminScreen(doc,"fixture");client.gui.setScreen(parent);
        var draft=(AdminDocument)field(parent,"draft");var first=draft.config.categories.get(0).items.get(0);var other=draft.config.categories.get(1).items.get(0);
        var screen=new PriceAdjustScreen(parent,draft.config,"");client.gui.setScreen(screen);
        check(field(screen,"categoryId").equals("bulk_0"),"Opening from builder home defaults to first category");
        check(((List<?>)field(screen,"tabs")).size()==12,"Only actual category tabs remain");
        check(((List<?>)field(screen,"values")).size()==95,"Browser contains listings in selected shop category only");
        check(screen.click(100,156,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT),"Pick exact first listing");
        screen.click(390,290,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);screen.release(390,290,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);check((int)field(screen,"topRow")>0,"Creative scrollbar scrolls listings");
        press(screen,"Custom food");screen.click(100,156,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        input(screen,"Search shop listings","no matching listing");check(((List<?>)field(screen,"values")).isEmpty(),"Search narrows current tab");
        check(((Set<?>)field(screen,"selection")).size()==2,"Selection survives tabs and searches");
        input(screen,"Search shop listings","");input(screen,"New buy price","12.5");input(screen,"New sell price","0");press(screen,"Set prices");
        check(first.buy==12.5&&other.buy==12.5&&first.sell==0&&draft.config.categories.get(0).items.get(1).buy==10,"Prices affect exact selected listings across categories");
        input(screen,"New buy price","-1");press(screen,"Set prices");check(first.buy==12.5&&!field(screen,"error").toString().isBlank(),"Invalid amounts rejected");
        input(screen,"New buy price","");input(screen,"New sell price","1.25");press(screen,"Set prices");check(first.buy==12.5&&first.sell==1.25,"Blank price keeps other side");
        press(screen,"Remove selected");check(client.gui.screen() instanceof EditorDialog,"Removal confirmation opens");press(client.gui.screen(),"Cancel");
        check(draft.config.categories.get(0).items.size()==95&&((Set<?>)field(screen,"selection")).size()==2,"Cancel preserves listings and selection");
        press(screen,"Remove selected");press(client.gui.screen(),"Remove 2 listings");
        check(draft.config.categories.get(0).items.size()==94&&draft.config.categories.get(1).items.size()==94&&draft.config.categories.size()==12,"Exact variants removed without category/sibling deletion");
        check(((Set<?>)field(screen,"selection")).isEmpty(),"Removed listings clear selection");
        press(screen,"Select whole tab");check(((Set<?>)field(screen,"selection")).size()==94,"Select all includes offscreen matches");
        press(screen,"Deselect whole tab");check(((Set<?>)field(screen,"selection")).isEmpty(),"Second click deselects all current matches");
        screen.click(100,156,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);press(screen,"Select whole tab");
        check(((Set<?>)field(screen,"selection")).size()==94,"Select all completes a partial selection");press(screen,"Clear selection");
        press(screen,"Stew variants");input(screen,"Search shop listings","variant 1");press(screen,"Select whole tab");
        int picked=((Set<?>)field(screen,"selection")).size();check(picked==94,"Select all selects the whole current category regardless of search");
        check(((Set<?>)field(screen,"selection")).stream().allMatch(entry->((com.zycu.guishop.admin.BulkListings.Entry)entry).category().id.equals("bulk_0")),"Select all never adds listings from another category");
        press(screen,">");check(((Set<?>)field(screen,"selection")).size()==picked,"Tab paging preserves picks");press(screen,"<");
        press(screen,"Custom food");screen.click(100,156,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);check(((Set<?>)field(screen,"selection")).size()==picked+1,"Cross-category selection remains independent");
        press(screen,"Select whole tab");check(((Set<?>)field(screen,"selection")).size()==picked+94,"Select all preserves picks in other tabs");
        press(screen,"Deselect whole tab");check(((Set<?>)field(screen,"selection")).size()==picked,"Deselect all preserves picks in other tabs");
        input(screen,"Search shop listings","no matching listing");
        check(((net.minecraft.client.gui.components.Button)field(screen,"selectAll")).active,"Whole-tab selection remains available when search has no matches");
        press(screen,"Select whole tab");check(((Set<?>)field(screen,"selection")).size()==188,"Select all includes current category hidden by search");
        press(screen,"Deselect whole tab");check(((Set<?>)field(screen,"selection")).size()==94,"Search-hidden tab deselection preserves other category");
        press(screen,"Empty category 2");check(!((net.minecraft.client.gui.components.Button)field(screen,"selectAll")).active,"Select all disabled for an empty category");
        press(screen,"Stew variants");press(screen,"Deselect whole tab");check(((Set<?>)field(screen,"selection")).isEmpty(),"Filtered deselection clears the entire current tab");
        press(screen,"Select whole tab");screen.click(100,156,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        check(((net.minecraft.client.gui.components.Button)field(screen,"selectAll")).getMessage().getString().equals("Select whole tab"),"Manual deselection resets toggle label");
        input(screen,"Search shop listings","");press(screen,"Clear selection");
        press(screen,"Select all listings");check(((Set<?>)field(screen,"selection")).size()==188,"Select all listings spans every category including offscreen listings");
        check(!((net.minecraft.client.gui.components.Button)field(screen,"selectAllListings")).active,"Global button disables once all listings are selected");
        press(screen,"Deselect whole tab");check(((Set<?>)field(screen,"selection")).size()==94,"Whole-tab deselection preserves global selections in other categories");
        press(screen,"Clear selection");
        check(AdminDocument.JSON.toJson(doc).equals(untouched),"Source document unaffected by all draft changes");
        input(screen,"New buy price","12.50");input(screen,"New sell price","1.25");
    }
    private void finish(Minecraft client,boolean passed,String message)throws Exception{
        stage=4;Files.createDirectories(report.getParent());Files.writeString(report,AdminDocument.JSON.toJson(Map.of("passed",passed,"message",message,"networkSmoke",Boolean.getBoolean("guishop.networkSmoke"),"fullFabricApiInstalled",FabricLoader.getInstance().isModLoaded("fabric-api"),"bulkCommitSmoke",Boolean.getBoolean("guishop.bulkCommitSmoke"),"removedListingIds",removedBulkIds,"repricedListingId",repricedBulkId,"clientVisible",true)));System.out.println("[Standalone font] "+message);client.stop();
    }
}
