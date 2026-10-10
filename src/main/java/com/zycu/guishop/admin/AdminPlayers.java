package com.zycu.guishop.admin;

import com.zycu.guishop.GuiShop;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Names and current balances come from the server, independently of the journal's recent window. */
public final class AdminPlayers {
    private AdminPlayers() {}
    public record Player(String uuid, String name, double balance, boolean online) {}
    public record Page(List<Player> players, int offset, boolean more, String currencySymbol, double defaultBalance) {
        public Page(List<Player> players, int offset, boolean more) { this(players, offset, more, "$", 0); }
    }
    public record History(Player player, TransactionJournal.Page history, String currencySymbol, double defaultBalance) {}

    private static void authorize(ServerPlayer viewer) {
        if (!AdminEditorService.allowed(viewer)) throw new SecurityException(AdminEditorService.DENIED);
    }
    public static Player player(ServerPlayer viewer, String id) {
        authorize(viewer);
        UUID uuid = UUID.fromString(id);
        var server = viewer.createCommandSourceStack().getServer();
        var online = server.getPlayerList().getPlayer(uuid);
        String name = online == null ? GuiShop.PLAYERS.displayName(uuid) : online.getName().getString();
        return new Player(uuid.toString(), name, GuiShop.ECONOMY.readBalance(uuid), online != null);
    }
    public static Page list(ServerPlayer viewer, String filter, int offset) {
        authorize(viewer);
        String search = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        if (search.length() > 128 || offset < 0 || offset > 10_000) throw new IllegalArgumentException("Invalid player filter or page");
        var server = viewer.createCommandSourceStack().getServer();
        Map<UUID, String> names = new HashMap<>(GuiShop.PLAYERS.knownPlayers());
        for (UUID uuid : GuiShop.ECONOMY.knownPlayers()) names.putIfAbsent(uuid, uuid.toString());
        for (var online : server.getPlayerList().getPlayers()) names.put(online.getUUID(), online.getName().getString());
        List<Player> result = new ArrayList<>();
        names.entrySet().stream()
            .filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).contains(search) || entry.getKey().toString().contains(search))
            .sorted(Comparator.<Map.Entry<UUID, String>, String>comparing(Map.Entry::getValue, String.CASE_INSENSITIVE_ORDER).thenComparing(entry -> entry.getKey().toString()))
            .skip(offset).limit(101).forEach(entry -> result.add(new Player(entry.getKey().toString(), entry.getValue(),
                GuiShop.ECONOMY.readBalance(entry.getKey()), server.getPlayerList().getPlayer(entry.getKey()) != null)));
        return new Page(result.size() > 100 ? List.copyOf(result.subList(0, 100)) : List.copyOf(result), offset, result.size() > 100, GuiShop.CONFIG.currencySymbol, GuiShop.CONFIG.startingBalance);
    }
}
