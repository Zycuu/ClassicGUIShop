package com.zycu.guishop.client;

import com.zycu.guishop.ShopConfig;
import com.zycu.guishop.admin.AdminDocument;
import com.zycu.guishop.admin.AdminEditorService;
import com.zycu.guishop.admin.AdminPayloads;
import com.zycu.guishop.admin.TransactionJournal;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AdminShopClient implements ClientModInitializer {
    private static final Map<String, Pending> PENDING = new HashMap<>();
    private static KeyMapping open;
    private static AdminScreen editor;
    private static long nextRequestAt;
    private static boolean openRequested;
    private record Deferred(String action, String revision, String body, IconPickerScreen picker) {}
    private static Deferred deferred;
    private static final class Pending {
        final String action, filter; final com.zycu.guishop.admin.AdminNetworking.Query query; final long started = System.nanoTime();
        final com.zycu.guishop.admin.DetectedContent.Query detection;
        final IconPickerScreen picker;
        AdminPayloads.Assembly assembly;
        Pending(String action, String body, IconPickerScreen picker) {
            this.action = action;
            this.picker = picker;
            detection = action.equals("detected") ? AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.DetectedContent.Query.class) : null;
            query = action.equals("logs") ? AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.AdminNetworking.Query.class) : null;
            filter = action.equals("players") ? AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.AdminNetworking.PlayersQuery.class).filter() : "";
        }
    }
    @Override public void onInitializeClient() {
        open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.guishop.adminshop", InputConstants.KEY_F8,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("guishop", "adminshop"))));
        ClientPlayNetworking.registerGlobalReceiver(AdminPayloads.Reply.TYPE, (payload, context) -> receive(payload.message()));
        ClientPlayNetworking.registerGlobalReceiver(AdminPayloads.Open.TYPE, (payload, context) -> {
            var screen = Minecraft.getInstance().gui.screen();
            if (!(screen instanceof AdminScreen) && !(screen instanceof IconPickerScreen)) openRequested = true;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { PENDING.clear(); editor = null; deferred = null; nextRequestAt = 0; openRequested = false; });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (deferred != null) {
                if (deferred.picker() != null && client.gui.screen() != deferred.picker()) deferred = null;
                else if (System.nanoTime() >= nextRequestAt && PENDING.isEmpty()) {
                    var request = deferred; deferred = null;
                    send(request.action(), request.revision(), request.body(), request.picker());
                }
            }
            while (open.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) openRequested = true;
            }
            if (openRequested && client.player != null) {
                if (request("open", "", "") || !ClientPlayNetworking.canSend(AdminPayloads.Request.TYPE)) openRequested = false;
            }
            boolean timedOut = PENDING.values().removeIf(pending -> {
                if (System.nanoTime() - pending.started <= 30_000_000_000L) return false;
                if (pending.picker != null) pending.picker.failure("The server did not respond. Close and reopen the picker to try again.");
                return true;
            });
            if (timedOut && !(client.gui.screen() instanceof IconPickerScreen)) {
                if (editor != null) editor.failure("The server did not respond. Your draft has been preserved.");
                else notice("The server did not respond to the Adminshop request.");
            }
            if (client.gui.screen() instanceof IconPickerScreen picker) picker.tickNetworking();
            else if (editor != null) editor.tickNetworking();
        });
    }
    public static boolean request(String action, String revision, String body) {
        return request(action, revision, body, null);
    }
    static boolean detect(IconPickerScreen picker, com.zycu.guishop.admin.DetectedContent.Query query) {
        return request("detected", "", AdminDocument.JSON.toJson(query), picker);
    }
    static void cancelDetection(IconPickerScreen picker) {
        if (deferred != null && deferred.picker() == picker) deferred = null;
    }
    private static boolean request(String action, String revision, String body, IconPickerScreen picker) {
        var client = Minecraft.getInstance();
        if (client.getConnection() == null || !ClientPlayNetworking.canSend(AdminPayloads.Request.TYPE)) {
            notice("The Adminshop versions do not match. Update ClassicGUIShop on the server and admin client together."); return false;
        }
        if (!PENDING.isEmpty() || deferred != null) {
            if (editor != null) editor.status("Waiting for the previous server request.");
            return false;
        }
        if (System.nanoTime() < nextRequestAt) { deferred = new Deferred(action, revision, body, picker); return true; }
        return send(action, revision, body, picker);
    }
    private static boolean send(String action, String revision, String body, IconPickerScreen picker) {
        String id = UUID.randomUUID().toString();
        try {
            var chunks = AdminPayloads.chunks(id, action, revision, body);
            PENDING.put(id, new Pending(action, body, picker));
            nextRequestAt = System.nanoTime() + 250_000_000L;
            for (var chunk : chunks) ClientPlayNetworking.send(new AdminPayloads.Request(chunk));
            return true;
        } catch (Exception exception) {
            PENDING.remove(id);
            if (editor != null) editor.failure(exception.getMessage()); else notice(exception.getMessage());
            return false;
        }
    }
    private static void receive(AdminPayloads.Message message) {
        Pending pending = PENDING.get(message.id()); if (pending == null) return;
        try {
            if (message.index() == 0) pending.assembly = new AdminPayloads.Assembly(message);
            if (pending.assembly == null || !pending.assembly.add(message)) return;
            PENDING.remove(message.id());
            // Pace from receipt, not game ticks: client catch-up ticks must not burst editor requests.
            nextRequestAt = System.nanoTime() + 250_000_000L;
            String body = pending.assembly.body();
            switch (message.action()) {
                case "variant_sources" -> { if(editor!=null)editor.variantSources(AdminDocument.JSON.fromJson(body,com.zycu.guishop.admin.VariantSources.Catalog.class)); }
                case "trending" -> { if(Minecraft.getInstance().gui.screen() instanceof TrendingScreen screen)screen.response(AdminDocument.JSON.fromJson(body,com.zycu.guishop.admin.Trending.Result.class)); }
                case "portable" -> { if(Minecraft.getInstance().gui.screen() instanceof PresetLibraryScreen screen)screen.response(AdminDocument.JSON.fromJson(body,com.zycu.guishop.admin.PortablePresets.Response.class)); }
                case "denied" -> {
                    // Never keep an old editor visible after permission revocation.
                    editor = null; deferred = null; openRequested = false; notice(AdminEditorService.DENIED);
                }
                case "error" -> { if(Minecraft.getInstance().gui.screen() instanceof TrendingScreen screen)screen.failure(body); else if(Minecraft.getInstance().gui.screen() instanceof PresetLibraryScreen screen)screen.failure(body); else if (pending.picker != null) pending.picker.failure(body); else if (editor != null) editor.failure(body); else notice(body); }
                case "snapshot", "saved" -> {
                    AdminDocument document = AdminDocument.JSON.fromJson(body, AdminDocument.class);
                    if (editor == null || !pending.action.equals("apply") && !pending.action.equals("open")) {
                        editor = new AdminScreen(document, message.revision());
                    } else editor.load(document, message.revision(), message.action().equals("saved"));
                    Minecraft.getInstance().gui.setScreen(editor);
                }
                case "detected" -> {
                    var page = AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.DetectedContent.Page.class);
                    if (pending.picker != null) pending.picker.detected(page, pending.detection);
                    else if (editor != null) editor.detected(page, pending.detection);
                }
                case "held" -> { if (editor != null) editor.addHeld(AdminDocument.JSON.fromJson(body, ShopConfig.ShopItem.class)); }
                case "restored" -> {
                    if (editor != null) {
                        editor.restore(AdminDocument.JSON.fromJson(body, AdminEditorService.RestoreDraft.class));
                        Minecraft.getInstance().gui.setScreen(editor);
                    }
                }
                case "logs" -> { if (editor != null) editor.logs(AdminDocument.JSON.fromJson(body, TransactionJournal.Page.class), pending.query); }
                case "players" -> { if (editor != null) editor.players(AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.AdminPlayers.Page.class), pending.filter); }
                case "player_logs" -> { if (editor != null) editor.playerHistory(AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.AdminPlayers.History.class), pending.query); }
                case "account_saved" -> { if (editor != null) editor.accountResult(AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.AccountOperations.Result.class)); }
                case "mode_catalog" -> { if (editor != null) editor.modeCatalog(AdminDocument.JSON.fromJson(body, com.zycu.guishop.ShopModes.Catalog.class)); }
                case "preset" -> { if (editor != null) editor.presetResult(AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.ShopPresets.Plan.class)); }
                default -> throw new IllegalArgumentException("Unsupported editor response");
            }
        } catch (Exception exception) {
            PENDING.remove(message.id());
            if (editor != null) editor.failure("Could not read the editor response. " + exception.getMessage());
            else notice("Could not read the editor response.");
        }
    }
    private static void notice(String message) {
        Minecraft.getInstance().gui.setScreen(new AdminNoticeScreen(message, null));
    }
}
