package com.zycu.guishop;

import com.zycu.guishop.admin.AdminDocument;
import com.zycu.guishop.admin.AccountOperations;
import com.zycu.guishop.admin.ShopPresets;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;
import java.util.ArrayList;

/** A resumable first-install setup. Existing or intentionally emptied shops are never regenerated. */
public final class DefaultShopSetup {
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("guishop/default-shop-setup.json");
    private record State(int version,String preset,String status){}
    private DefaultShopSetup(){}
    public static void prepareFresh() throws Exception {
        if(!Files.exists(FILE))AccountOperations.writeAtomic(FILE,AdminDocument.JSON.toJson(new State(1,"normal","pending")));
    }
    public static void complete(MinecraftServer server,ShopConfig config) throws Exception {
        if(!Files.exists(FILE))return;
        var state=AdminDocument.JSON.fromJson(Files.readString(FILE),State.class);
        if(state==null||state.version()!=1||!"normal".equals(state.preset())||!java.util.Set.of("pending","applied").contains(state.status()))throw new IllegalArgumentException("Invalid starter shop setup state: "+FILE);
        if(!state.status().equals("pending"))return;
        if(config.categories.isEmpty()){
            var plan=ShopPresets.starter(server);
            config.categories=new ArrayList<>(plan.categories());
            config.presetSellRatio=config.bookSellRatio=config.potionSellRatio=.30;
            config.saveChecked();
            System.out.println("[ClassicGUIShop] Full Vanilla Normal starter shop saved: "+plan.items()+" listings, 30% default sell return, "+plan.capped()+" recipe/map safety caps.");
        }
        // If saving completed before a crash, preserve that catalog instead of applying it again.
        AccountOperations.writeAtomic(FILE,AdminDocument.JSON.toJson(new State(1,"normal","applied")));
    }
}
