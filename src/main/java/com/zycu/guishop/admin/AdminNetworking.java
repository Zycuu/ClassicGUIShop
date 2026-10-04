package com.zycu.guishop.admin;

import com.zycu.guishop.GuiShop;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AdminNetworking {
    private static final Map<UUID, AdminPayloads.Assembly> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static java.util.concurrent.ThreadPoolExecutor journalWorker;
    private AdminNetworking() {}
    public record Query(String filter, int offset, boolean changes, String playerUuid) {
        public Query(String filter, int offset, boolean changes) { this(filter, offset, changes, null); }
    }
    public record PlayersQuery(String filter, int offset) {}

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(AdminPayloads.Request.TYPE, AdminPayloads.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdminPayloads.Reply.TYPE, AdminPayloads.Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AdminPayloads.Request.TYPE,
            (payload, context) -> receive(context.player(), payload.message()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PENDING.remove(handler.player.getUUID()); LAST_REQUEST.remove(handler.player.getUUID());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> GuiShop.PLAYERS.remember(handler.player));
    }
    public static synchronized void clear() {
        PENDING.clear(); LAST_REQUEST.clear();
        if (journalWorker != null) { journalWorker.shutdownNow(); journalWorker = null; }
    }
    private static synchronized java.util.concurrent.Executor journalWorker() {
        if (journalWorker == null) {
            journalWorker = new java.util.concurrent.ThreadPoolExecutor(1, 1, 30, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(16), task -> {
                    Thread thread = new Thread(task, "ClassicGUIShop journal reader"); thread.setDaemon(true); return thread;
                });
            journalWorker.allowCoreThreadTimeOut(true);
        }
        return journalWorker;
    }

    private static void receive(ServerPlayer player, AdminPayloads.Message message) {
        if (!ServerPlayNetworking.canSend(player, AdminPayloads.Reply.TYPE)) return;
        if (!AdminEditorService.allowed(player)) {
            PENDING.remove(player.getUUID());
            long now = System.nanoTime(), previous = LAST_REQUEST.getOrDefault(player.getUUID(), 0L);
            if (now - previous > 1_000_000_000L) {
                LAST_REQUEST.put(player.getUUID(), now);
                reply(player, message.id(), "denied", "", AdminEditorService.DENIED);
            }
            return;
        }
        try {
            if (message.index() == 0) {
                long now = System.nanoTime(), previous = LAST_REQUEST.getOrDefault(player.getUUID(), 0L);
                if (now - previous < 200_000_000L) throw new IllegalArgumentException("Please wait briefly before another editor request.");
                LAST_REQUEST.put(player.getUUID(), now);
                PENDING.put(player.getUUID(), new AdminPayloads.Assembly(message));
            }
            var assembly = PENDING.get(player.getUUID());
            if (assembly == null) throw new IllegalArgumentException("Editor packet sequence was not started");
            if (!assembly.add(message)) return;
            PENDING.remove(player.getUUID());
            switch (message.action()) {
                case "open" -> {
                    var snapshot = AdminEditorService.snapshot(player);
                    reply(player, message.id(), "snapshot", snapshot.revision(), AdminDocument.JSON.toJson(snapshot.document()));
                }
                case "apply" -> {
                    var draft = AdminDocument.JSON.fromJson(assembly.body(), AdminDocument.class);
                    var saved = AdminEditorService.apply(player, message.revision(), draft);
                    reply(player, message.id(), "saved", saved.revision(), AdminDocument.JSON.toJson(saved.document()));
                }
                case "held" -> reply(player, message.id(), "held", "", AdminDocument.JSON.toJson(AdminEditorService.heldListing(player)));
                case "restore" -> reply(player, message.id(), "restored", "", AdminDocument.JSON.toJson(AdminEditorService.restoreDraft(player, assembly.body())));
                case "logs" -> {
                    var query = AdminDocument.JSON.fromJson(assembly.body(), Query.class);
                    if (query == null) throw new IllegalArgumentException("Missing history query");
                    queryJournal(player, message.id(), query);
                }
                case "players" -> {
                    var query = AdminDocument.JSON.fromJson(assembly.body(), PlayersQuery.class);
                    if (query == null) throw new IllegalArgumentException("Missing player query");
                    reply(player, message.id(), "players", "", AdminDocument.JSON.toJson(AdminPlayers.list(player, query.filter(), query.offset())));
                }
                default -> throw new IllegalArgumentException("Unknown editor request");
            }
        } catch (SecurityException denied) {
            PENDING.remove(player.getUUID()); reply(player, message.id(), "denied", "", AdminEditorService.DENIED);
        } catch (Exception exception) {
            PENDING.remove(player.getUUID());
            String error = exception.getMessage() == null ? "The editor request failed. Check the server log." : exception.getMessage();
            reply(player, message.id(), "error", "", error.substring(0, Math.min(2000, error.length())));
            if (!(exception instanceof IllegalArgumentException) && !(exception instanceof IllegalStateException)) exception.printStackTrace();
        }
    }
    private static void queryJournal(ServerPlayer player, String id, Query query) {
        var server = player.createCommandSourceStack().getServer();
        String selected = query.playerUuid() == null || query.playerUuid().isBlank() ? null : UUID.fromString(query.playerUuid()).toString();
        if (selected != null && query.changes()) throw new IllegalArgumentException("Invalid player history query");
        // Journal searches can read multiple daily files. Keep that work off the tick thread.
        journalWorker().execute(() -> {
            String body, action;
            try { body = AdminDocument.JSON.toJson(TransactionJournal.query(query.filter(), query.offset(), query.changes(), selected)); action = "logs"; }
            catch (Exception failure) { body = "Could not read server records: " + failure.getMessage(); action = "error"; }
            final String responseBody = body, responseAction = action;
            server.execute(() -> {
                if (server.getPlayerList().getPlayer(player.getUUID()) != player) return;
                if (!AdminEditorService.allowed(player)) reply(player, id, "denied", "", AdminEditorService.DENIED);
                else if (selected != null && responseAction.equals("logs")) {
                    var history = AdminDocument.JSON.fromJson(responseBody, TransactionJournal.Page.class);
                    reply(player, id, "player_logs", "", AdminDocument.JSON.toJson(new AdminPlayers.History(AdminPlayers.player(player, selected), history)));
                } else reply(player, id, responseAction, "", responseBody);
            });
        });
    }
    private static void reply(ServerPlayer player, String id, String action, String revision, String body) {
        for (var part : AdminPayloads.chunks(id, action, revision, body)) {
            ServerPlayNetworking.send(player, new AdminPayloads.Reply(part));
        }
    }
}
