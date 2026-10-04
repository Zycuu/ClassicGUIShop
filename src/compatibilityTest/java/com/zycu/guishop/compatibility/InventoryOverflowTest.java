package com.zycu.guishop.compatibility;

import com.mojang.authlib.GameProfile;
import com.zycu.guishop.EconomyExploitScanner;
import com.zycu.guishop.EnchantmentShopService;
import com.zycu.guishop.GuiShop;
import com.zycu.guishop.ShopGui;
import com.zycu.guishop.ShopService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Executes only on disposable test servers, using the real Minecraft inventory/drop code. */
@Mixin(MinecraftServer.class)
public abstract class InventoryOverflowTest {
    @Unique private boolean guishop$tested;
    @Unique private boolean guishop$prepared;
    @Unique private ItemEntity guishop$readinessProbe;

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void guishop$test(BooleanSupplier timeLeft, CallbackInfo callback) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (guishop$tested || server.getTickCount() < 2) return;
        if (!guishop$prepared) {
            server.overworld().setChunkForced(0, 0, true);
            server.overworld().getChunk(0, 0);
            guishop$readinessProbe = new ItemEntity(server.overworld(), 8, 128, 8, new ItemStack(Items.STONE));
            if (!server.overworld().addFreshEntity(guishop$readinessProbe)) {
                throw new AssertionError("Could not add test chunk readiness probe");
            }
            guishop$prepared = true;
            return;
        }
        // Loading a chunk does not guarantee its entities are visible yet, particularly on CI.
        boolean probeVisible = false;
        for (Entity entity : server.overworld().getAllEntities()) {
            if (entity == guishop$readinessProbe) probeVisible = true;
        }
        if (!probeVisible) return;
        guishop$readinessProbe.discard();
        guishop$tested = true;
        ServerLevel level = server.overworld();
        level.getChunk(0, 0);
        ServerPlayer player = new ServerPlayer(server, level,
            new GameProfile(UUID.fromString("4812eb9e-d832-46a5-a3e9-963d1391b8a4"), "ShopTest"),
            ClientInformation.createDefault());
        player.setPos(8, 128, 8);

        guishop$checkDelivery(player, level, false, 0, 3, 3, 0);
        guishop$checkDelivery(player, level, true, 63, 3, 64, 2);
        guishop$checkDelivery(player, level, true, 0, 3, 0, 3);
        guishop$checkDelivery(player, level, true, 0, 130, 0, 130);

        int offersBefore = GuiShop.CONFIG.enchantments.size();
        EnchantmentShopService.availableEnchantments(player, ShopGui.Mode.BUY);
        if (GuiShop.CONFIG.enchantments.size() != offersBefore) {
            throw new AssertionError("Opening the enchantment shop discovered new offers");
        }

        if (EconomyExploitScanner.audit(server).recipesChecked() <= 0) {
            throw new AssertionError("Recipe audit did not find vanilla recipes");
        }
        var dispatcher = server.getCommands().getDispatcher();
        if (dispatcher.getRoot().getChild("adminshop") != null
            || dispatcher.getRoot().getChild("ident") != null
            || dispatcher.getCompletionSuggestions(dispatcher.parse("admin", server.createCommandSourceStack()))
                .join().getList().stream().anyMatch(suggestion -> suggestion.getText().equals("adminshop"))) {
            throw new AssertionError("Retired admin command remains registered or suggested");
        }
        for (String command : new String[]{"shop", "bal", "sellhand", "worth"}) {
            if (dispatcher.getRoot().getChild(command) == null) {
                throw new AssertionError("Missing retained player command: " + command);
            }
        }
        System.out.println("[ClassicGUIShop compatibility test] PASS: inventory delivery; recipe audit; player commands retained; admin and identification commands absent; no enchantment discovery");
    }

    @Unique
    private static void guishop$checkDelivery(ServerPlayer player, ServerLevel level, boolean fill,
                                            int initialDiamonds, int quantity,
                                            int expectedInventory, int expectedDrops) {
        player.getInventory().clearContent();
        if (fill) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }
        }
        if (initialDiamonds > 0) player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, initialDiamonds));
        int before = guishop$droppedDiamonds(level);
        ShopService.give(player, new ItemStack(Items.DIAMOND), quantity);
        int inventory = ShopService.count(player, new ItemStack(Items.DIAMOND));
        int dropped = guishop$droppedDiamonds(level) - before;
        if (inventory != expectedInventory || dropped != expectedDrops) {
            throw new AssertionError("Delivery lost or duplicated items: inventory=" + inventory + ", drops=" + dropped);
        }
    }

    @Unique
    private static int guishop$droppedDiamonds(ServerLevel level) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item && item.getItem().is(Items.DIAMOND)) {
                count += item.getItem().getCount();
            }
        }
        return count;
    }
}
