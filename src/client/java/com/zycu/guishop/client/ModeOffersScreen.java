package com.zycu.guishop.client;

import com.zycu.guishop.ShopModes;
import com.zycu.guishop.ShopModesConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Detached rewards and detected-mob selection in one editor. Update edits the draft. */
final class ModeOffersScreen extends WorkbenchScreen {
    private final AdminScreen parent;
    private final ShopModesConfig config;
    private final boolean advancements;
    private final List<ShopModes.Offer> catalog;
    private final Map<String,String> amounts=new HashMap<>();
    private final Map<String,Double> preserved=new LinkedHashMap<>();
    private final Set<String> added=new HashSet<>();
    private List<ShopModes.Offer> shown=List.of();
    private String query="",error="";
    private int filterTicks;
    private boolean adding;
    private final WorkbenchScroll scroll=new WorkbenchScroll(614,110,200,8,this::rebuildKeepingFocus);

    ModeOffersScreen(AdminScreen parent,ShopModesConfig config,ShopModes.Catalog content,boolean advancements) {
        super(advancements?"Advancement rewards":"Mob rewards");
        this.parent=parent;this.config=config;this.advancements=advancements;
        var entries=new LinkedHashMap<String,ShopModes.Offer>();
        for(var offer:advancements?content.advancements():content.entities())entries.put(offer.id(),offer);
        if(advancements)for(var id:prices().keySet())entries.putIfAbsent(id,new ShopModes.Offer(id,"Unavailable content",0));
        this.catalog=entries.values().stream().sorted(Comparator.comparing(ShopModes.Offer::name,String.CASE_INSENSITIVE_ORDER).thenComparing(ShopModes.Offer::id)).toList();
        for(var entry:prices().entrySet()) {
            if(entries.containsKey(entry.getKey())){amounts.put(entry.getKey(),Double.toString(entry.getValue()));added.add(entry.getKey());}
            else preserved.put(entry.getKey(),entry.getValue());
        }
    }
    private Map<String,Double> prices(){return advancements?config.advancementPrices:config.mobPrices;}
    private double fallback(ShopModes.Offer offer) {
        if(advancements)return !offer.id().startsWith("minecraft:")&&offer.suggested()>0?config.advancementDefault:offer.suggested();
        return config.mobDefault>0?config.mobDefault:offer.suggested();
    }
    private boolean listed(ShopModes.Offer offer){return advancements||offer.id().startsWith("minecraft:")||added.contains(offer.id());}
    @Override protected WorkbenchScroll scroller(){return scroll;}
    @Override public void tick(){if(filterTicks>0&&--filterTicks==0)rebuildKeepingFocus();}
    @Override protected boolean submitSearch(){filterTicks=0;rebuildKeepingFocus();return true;}
    @Override protected void init() {
        shown=catalog.stream().filter(entry->(adding?!listed(entry):listed(entry))&&(entry.id()+" "+entry.name()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        scroll.count(shown.size());
        var search=new WorkbenchField(font,32,62,514,Component.literal("Search rewards"));
        search.setValue(query);search.setHint(Component.literal(adding?"Search detected living mobs...":"Search mob/advancement name or ID..."));
        search.setResponder(value->{query=value;scroll.row=0;filterTicks=4;});addRenderableWidget(search);setInitialFocus(search);
        button("Clear",558,62,70,()->search.setValue(""));
        for(int row=0;row<8&&scroll.row+row<shown.size();row++) {
            var offer=shown.get(scroll.row+row);int y=111+row*25;
            if(adding) {
                button("Add",488,y,112,()->{added.add(offer.id());amounts.put(offer.id(),Double.toString(fallback(offer)));error="";rebuildKeepingFocus();});
            }else {
                var input=new WorkbenchField(font,488,y,112,Component.literal("Payout "+offer.id()));
                input.setValue(amounts.getOrDefault(offer.id(),Double.toString(fallback(offer))));
                try{amount(input.getValue());}catch(Exception invalid){input.setTextColor(WorkbenchTheme.DANGER);}
                input.setResponder(value->{amounts.put(offer.id(),value);try{amount(value);input.setTextColor(WorkbenchTheme.TEXT);error="";}catch(Exception invalid){input.setTextColor(WorkbenchTheme.DANGER);error="Correct the highlighted payout before updating.";}});
                addRenderableWidget(input);
            }
        }
        String defaultHelp=advancements?"This resets rewards in the editor. Update commits them to your unsaved draft.":"Vanilla mob defaults are 2 to 50. Added mobs use the configured default. Update commits these to your unsaved draft.";
        button("Use defaults",32,338,128,()->minecraft.gui.setScreen(new EditorDialog(this,"Use default rewards?",defaultHelp,null,"Use defaults","",ignored->{amounts.clear();for(var offer:catalog)if(added.contains(offer.id())&&!offer.id().startsWith("minecraft:"))amounts.put(offer.id(),Double.toString(fallback(offer)));error="";rebuildWidgets();}))).active=!adding;
        if(!advancements)button(adding?"Back to rewards":"Add detected mobs",168,338,194,()->{adding=!adding;query="";scroll.row=0;error="";rebuildWidgets();});
        button("Cancel",374,338,112,this::onClose);
        button("Update",498,338,130,()->{
            try {
                var parsed=new LinkedHashMap<>(preserved);
                for(var entry:amounts.entrySet())parsed.put(entry.getKey(),amount(entry.getValue()));
                prices().clear();prices().putAll(parsed);parent.status("Rewards updated. Save changes when ready.");minecraft.gui.setScreen(parent);
            }catch(Exception invalid){error="Correct invalid payouts, including those outside the current view.";}
        });
    }
    private static double amount(String value){double number=Double.parseDouble(value);if(!Double.isFinite(number)||number<0||number>1_000_000)throw new IllegalArgumentException();return number;}
    @Override protected void paint(GuiGraphicsExtractor g,int x,int y,float delta) {
        panel(g,16,16,628,356,WorkbenchTheme.SURFACE);
        text(g,adding?"Add detected mobs":title.getString(),32,32,WorkbenchTheme.TEXT);
        text(g,advancements?"Edit registered advancement rewards. 0 disables a reward.":adding?"Choose a living mob to add to rewards.":"Vanilla defaults: 2 to 50. Added mobs use your default reward.",32,47,WorkbenchTheme.MUTED);
        text(g,"Name",32,94,WorkbenchTheme.MUTED);text(g,adding?"Add to rewards":"Reward (0 = off)",488,94,WorkbenchTheme.MUTED);
        for(int row=0;row<8&&scroll.row+row<shown.size();row++) {
            var offer=shown.get(scroll.row+row);int yy=110+row*25;
            g.fill(30,yy,476,yy+24,WorkbenchTheme.ROW);
            text(g,font.plainSubstrByWidth(offer.name(),430),36,yy+2,WorkbenchTheme.TEXT);
            text(g,font.plainSubstrByWidth(offer.id(),430),36,yy+13,WorkbenchTheme.MUTED);
            if(x>=30&&x<476&&y>=yy&&y<yy+24)g.setTooltipForNextFrame(font,font.split(Component.literal(offer.name()+"\n"+offer.id()),320),(int)(originX+x*scale),(int)(originY+y*scale));
        }
        if(shown.isEmpty()){
            int yy=120;
            for(var line:font.split(Component.literal(adding?"No additional detected living mobs. Refresh content in Shop Modes after changing server mods.":"No matching rewards. Clear search to see the list."),560)){g.text(font,line,36,yy,WorkbenchTheme.MUTED,false);yy+=12;}
        }
        scroll.paint(g);
        text(g,font.plainSubstrByWidth(error.isBlank()?shown.size()+" matches | Update edits the draft. Save changes applies it.":error,594),32,320,error.isBlank()?WorkbenchTheme.MUTED:WorkbenchTheme.DANGER);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
