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
    public record Query(String filter, int offset, boolean changes, String playerUuid, String zoneId, long generation) {
        public Query(String filter, int offset, boolean changes) { this(filter, offset, changes, null, "UTC", 0); }
        public Query(String filter, int offset, boolean changes, String playerUuid) { this(filter, offset, changes, playerUuid, "UTC", 0); }
    }
    public record PlayersQuery(String filter, int offset) {}

    public static int openEditor(net.minecraft.commands.CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Run /adminshop in game to open the client Adminshop tools.")); return 0;
        }
        if (!ServerPlayNetworking.canSend(player, AdminPayloads.Open.TYPE)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal(AdminEditorService.allowed(player)
                ? "Install the matching ClassicGUIShop mod on your client to use /adminshop or F8."
                : AdminEditorService.DENIED)); return 0;
        }
        // The client requests a fresh, permission-checked snapshot. This sends no configuration itself.
        ServerPlayNetworking.send(player, new AdminPayloads.Open()); return 1;
    }

    public static void register() {
        DetectedContent.register();
        PayloadTypeRegistry.serverboundPlay().register(AdminPayloads.Request.TYPE, AdminPayloads.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdminPayloads.Reply.TYPE, AdminPayloads.Reply.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdminPayloads.Open.TYPE, AdminPayloads.Open.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AdminPayloads.Request.TYPE,
            (payload, context) -> receive(context.player(), payload.message()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            com.zycu.guishop.ShopModes.disconnected(handler.player);
            PENDING.remove(handler.player.getUUID()); LAST_REQUEST.remove(handler.player.getUUID());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> GuiShop.PLAYERS.remember(handler.player));
    }
    public static synchronized void clear() {
        PENDING.clear(); LAST_REQUEST.clear(); DetectedContent.clear();
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
                case "detected" -> {
                    var query = AdminDocument.JSON.fromJson(assembly.body(), DetectedContent.Query.class);
                    if (query == null) throw new IllegalArgumentException("Missing detection query");
                    reply(player, message.id(), "detected", "", AdminDocument.JSON.toJson(DetectedContent.list(player, query)));
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
                case "account" -> {
                    var change = AdminDocument.JSON.fromJson(assembly.body(), AccountOperations.Change.class);
                    reply(player, message.id(), "account_saved", "", AdminDocument.JSON.toJson(AccountOperations.change(player, change)));
                }
                case "trending" -> queryTrending(player,message.id(),AdminDocument.JSON.fromJson(assembly.body(),Trending.Request.class));
                case "variant_sources" -> reply(player,message.id(),"variant_sources","",AdminDocument.JSON.toJson(VariantSources.detect(player)));
                case "mode_catalog" -> reply(player, message.id(), "mode_catalog", "", AdminDocument.JSON.toJson(com.zycu.guishop.ShopModes.catalog(player)));
                case "portable" -> reply(player,message.id(),"portable","",AdminDocument.JSON.toJson(PortablePresets.handle(player,AdminDocument.JSON.fromJson(assembly.body(),PortablePresets.Request.class))));
                case "preset" -> reply(player, message.id(), "preset", "", AdminDocument.JSON.toJson(ShopPresets.generate(player, AdminDocument.JSON.fromJson(assembly.body(), ShopPresets.Request.class))));
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
        if (query.zoneId() != null && query.zoneId().length() > 80) throw new IllegalArgumentException("Invalid timezone");
        java.time.ZoneId.of(query.zoneId() == null || query.zoneId().isBlank() ? "UTC" : query.zoneId());
        // Journal searches can read multiple daily files. Keep that work off the tick thread.
        journalWorker().execute(() -> {
            String body, action;
            try { body = AdminDocument.JSON.toJson(TransactionJournal.query(query.filter(), query.offset(), query.changes(), selected, query.zoneId())); action = "logs"; }
            catch (Exception failure) { body = "Could not read server records: " + failure.getMessage(); action = "error"; }
            final String responseBody = body, responseAction = action;
            server.execute(() -> {
                if (server.getPlayerList().getPlayer(player.getUUID()) != player) return;
                if (!AdminEditorService.allowed(player)) reply(player, id, "denied", "", AdminEditorService.DENIED);
                else if (selected != null && responseAction.equals("logs")) {
                    var history = AdminDocument.JSON.fromJson(responseBody, TransactionJournal.Page.class);
                    reply(player, id, "player_logs", "", AdminDocument.JSON.toJson(new AdminPlayers.History(AdminPlayers.player(player, selected), history, GuiShop.CONFIG.currencySymbol, GuiShop.CONFIG.startingBalance)));
                } else reply(player, id, responseAction, "", responseBody);
            });
        });
    }
    private static void queryTrending(ServerPlayer player,String id,Trending.Request request) {
        var server=player.createCommandSourceStack().getServer();
        journalWorker().execute(() -> {
            String body,action;
            try{body=AdminDocument.JSON.toJson(Trending.query(request));action="trending";}catch(Exception error){body="Trending failed: "+error.getMessage();action="error";}
            final String response=body,kind=action;
            server.execute(() -> {if(server.getPlayerList().getPlayer(player.getUUID())!=player)return; if(!AdminEditorService.allowed(player))reply(player,id,"denied","",AdminEditorService.DENIED);else reply(player,id,kind,"",response);});
        });
    }
    private static void reply(ServerPlayer player, String id, String action, String revision, String body) {
        for (var part : AdminPayloads.chunks(id, action, revision, body)) {
            ServerPlayNetworking.send(player, new AdminPayloads.Reply(part));
        }
    }
}
