package com.zycu.guishop.client;
import com.zycu.guishop.admin.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.nio.file.*;
import java.util.*;
final class PresetLibraryScreen extends WorkbenchScreen {
    private final AdminScreen parent;private final AdminDocument draft;
    private List<String> files=List.of();private PortablePresets.Review review;private int page;private boolean started,busy;private String message="";
    PresetLibraryScreen(AdminScreen parent,AdminDocument draft){super("Portable preset library");this.parent=parent;this.draft=draft;}
    private void request(String action,String name,PortablePresets.Bundle bundle){if(AdminShopClient.request("portable","",AdminDocument.JSON.toJson(new PortablePresets.Request(action,name,bundle,action.equals("export")?draft:null)))){busy=true;message="Reading server preset library...";}}
    void response(PortablePresets.Response value){busy=false;message=value.message();if(value.action().equals("list"))files=value.files();if(value.review()!=null)review=value.review();
        if(value.bundle()!=null)try{Path dir=FabricLoader.getInstance().getConfigDir().resolve("guishop/presets");Files.createDirectories(dir);String safeName=value.bundle().name().replaceAll("[^A-Za-z0-9_.-]","_");Path file=dir.toAbsolutePath().normalize().resolve(safeName+"-"+System.currentTimeMillis()+".json");if(!file.startsWith(dir.toAbsolutePath().normalize()))throw new IllegalArgumentException("Invalid export filename");Files.writeString(file,AdminDocument.JSON.toJson(value.bundle()),StandardOpenOption.CREATE_NEW);message="Exported on server and client: "+file;}catch(Exception error){message="Server export succeeded; client copy failed: "+error.getMessage();}rebuildWidgets();}
    void failure(String value){busy=false;message=value;rebuildWidgets();}
    @Override protected void init(){
        button("Export preset",32,58,192,()->minecraft.gui.setScreen(new EditorDialog(this,"Export preset","Choose a new filename. Exports categories, exact variants and final prices only.","MyShop","Export preset","Preset name",name->request("export",name,null))));
        button("Import preset",236,58,192,()->minecraft.gui.setScreen(new EditorDialog(this,"Import preset","Enter a local JSON file path. The data is checked and stored in the server library before review.","config/guishop/presets/MyShop.json","Import preset","File path",name->{try{Path file=Path.of(name).toAbsolutePath().normalize();if(!Files.isRegularFile(file)||Files.size(file)>1_900_000)throw new IllegalArgumentException("File missing or larger than 1.9 MB");var bundle=AdminDocument.JSON.fromJson(Files.readString(file),PortablePresets.Bundle.class);request("import","import-"+System.currentTimeMillis(),bundle);}catch(Exception error){throw new IllegalArgumentException(error.getMessage());}})));
        button("Refresh library",440,58,188,()->request("list","",null));
        button("Previous",32,335,100,()->{page=Math.max(0,page-1);rebuildWidgets();}).active=page>0;button("Next",144,335,80,()->{page=Math.min(Math.max(0,(files.size()-1)/9),page+1);rebuildWidgets();}).active=(page+1)*9<files.size();
        button("Load into editor",236,335,264,()->{if(review==null)return;minecraft.gui.setScreen(new EditorDialog(this,"Replace draft listings?",review.bundle().categories().size()+" categories; "+review.unavailable()+" unavailable listings disabled. Server settings and accounts stay intact. Save changes creates a backup.",null,ignored->{draft.config.categories=new ArrayList<>(review.bundle().categories());parent.status("Imported preset loaded into the draft. Review, then Save changes.");minecraft.gui.setScreen(parent);}));}).active=!busy&&review!=null;
        button("Back",514,335,114,this::onClose);if(!started){started=true;request("list","",null);}
    }
    @Override protected void paint(GuiGraphicsExtractor g,int x,int y,float delta){panel(g,16,16,628,356,WorkbenchTheme.SURFACE);text(g,"Portable preset library | data only",32,30,WorkbenchTheme.TEXT);
        for(int row=0;row<9&&page*9+row<files.size();row++){int yy=94+row*22;g.fill(30,yy,322,yy+21,WorkbenchTheme.ROW);text(g,font.plainSubstrByWidth(files.get(page*9+row),278),36,yy+6,WorkbenchTheme.TEXT);}
        if(review!=null){text(g,font.plainSubstrByWidth(review.bundle().name(),290),336,94,WorkbenchTheme.TEXT);text(g,"Minecraft "+review.bundle().minecraft(),336,110,WorkbenchTheme.MUTED);int yy=132;var warnings=new ArrayList<String>();if(!review.bundle().dependencies().isEmpty())warnings.add("Content namespaces: "+String.join(", ",review.bundle().dependencies()));warnings.addAll(review.warnings());for(String warning:warnings)for(var line:font.split(net.minecraft.network.chat.Component.literal(warning),292)){if(yy>292)break;g.text(font,line,336,yy,WorkbenchTheme.MUTED,false);yy+=12;}}
        text(g,font.plainSubstrByWidth(message,595),32,309,WorkbenchTheme.MUTED);
        if(x>=30&&x<630&&y>=306&&y<329&&!message.isBlank())g.setTooltipForNextFrame(font,font.split(net.minecraft.network.chat.Component.literal(message),400),(int)(originX+x*scale),(int)(originY+y*scale));
    }
    @Override protected boolean click(double x,double y,int button){if(busy||button!=com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT||x<30||x>=322||y<94||y>=292)return false;int index=page*9+(int)((y-94)/22);if(index>=files.size())return false;request("review",files.get(index),null);return true;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
