package com.zycu.guishop;
import net.minecraft.server.MinecraftServer;
import java.util.*;
public final class PublicBalances {
    private PublicBalances() {}
    public record Balance(UUID uuid, String name, double amount) {}
    public static boolean visible(MinecraftServer server, UUID uuid) {
        if (GuiShop.CONFIG.showOperatorBalances) return true;
        return server.getPlayerList().getOps().getEntries().stream().noneMatch(entry -> entry.getUser().id().equals(uuid));
    }
    public static List<Balance> top(MinecraftServer server) {
        var names = new HashMap<UUID,String>(GuiShop.PLAYERS.knownPlayers());
        for (var uuid : GuiShop.ECONOMY.knownPlayers()) names.putIfAbsent(uuid, GuiShop.PLAYERS.displayName(uuid));
        for (var player : server.getPlayerList().getPlayers()) names.put(player.getUUID(),player.getName().getString());
        return names.entrySet().stream().filter(entry -> visible(server,entry.getKey()))
            .map(entry -> new Balance(entry.getKey(),entry.getValue(),GuiShop.ECONOMY.readBalance(entry.getKey())))
            .sorted(Comparator.comparingDouble(Balance::amount).reversed().thenComparing(Balance::name,String.CASE_INSENSITIVE_ORDER).thenComparing(entry -> entry.uuid().toString())).limit(5).toList();
    }
}
