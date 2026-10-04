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
    private static final class Pending {
        final String action, filter; final com.zycu.guishop.admin.AdminNetworking.Query query; final long started = System.nanoTime();
        final com.zycu.guishop.admin.DetectedContent.Query detection;
        AdminPayloads.Assembly assembly;
        Pending(String action, String body) {
            this.action = action;
            detection = action.equals("detected") ? AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.DetectedContent.Query.class) : null;
            query = action.equals("logs") ? AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.AdminNetworking.Query.class) : null;
            filter = action.equals("players") ? AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.AdminNetworking.PlayersQuery.class).filter() : "";
        }
    }
    @Override public void onInitializeClient() {
        open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.guishop.adminshop", InputConstants.KEY_F8,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("guishop", "adminshop"))));
        ClientPlayNetworking.registerGlobalReceiver(AdminPayloads.Reply.TYPE, (payload, context) -> receive(payload.message()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { PENDING.clear(); editor = null; });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) request("open", "", "");
            }
            boolean timedOut = PENDING.values().removeIf(pending -> System.nanoTime() - pending.started > 30_000_000_000L);
            if (timedOut) {
                if (editor != null) editor.failure("The server did not respond. Your draft has been preserved.");
                else notice("The server did not respond to the Adminshop request.");
            }
            if (editor != null) editor.tickNetworking();
        });
    }
    public static boolean request(String action, String revision, String body) {
        var client = Minecraft.getInstance();
        if (client.getConnection() == null || !ClientPlayNetworking.canSend(AdminPayloads.Request.TYPE)) {
            notice("This server does not support the ClassicGUIShop 1.4.0 Alpha editor."); return false;
        }
        if (!PENDING.isEmpty()) {
            if (editor != null) editor.status("Waiting for the previous server request.");
            return false;
        }
        String id = UUID.randomUUID().toString();
        try {
            var chunks = AdminPayloads.chunks(id, action, revision, body);
            PENDING.put(id, new Pending(action, body));
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
            String body = pending.assembly.body();
            switch (message.action()) {
                case "denied" -> {
                    // Never keep an old editor visible after permission revocation.
                    editor = null; notice(AdminEditorService.DENIED);
                }
                case "error" -> { if (editor != null) editor.failure(body); else notice(body); }
                case "snapshot", "saved" -> {
                    AdminDocument document = AdminDocument.JSON.fromJson(body, AdminDocument.class);
                    if (editor == null || !pending.action.equals("apply") && !pending.action.equals("open")) {
                        editor = new AdminScreen(document, message.revision());
                    } else editor.load(document, message.revision(), message.action().equals("saved"));
                    Minecraft.getInstance().gui.setScreen(editor);
                }
                case "detected" -> { if (editor != null) editor.detected(AdminDocument.JSON.fromJson(body, com.zycu.guishop.admin.DetectedContent.Page.class), pending.detection); }
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
