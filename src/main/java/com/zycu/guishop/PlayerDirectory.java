package com.zycu.guishop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PlayerDirectory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("guishop");
    private final Path file = directory.resolve("players.json");
    private final Map<String, String> names = new HashMap<>();

    public PlayerDirectory() {
        load();
    }

    public synchronized void remember(ServerPlayer player) {
        String name = player.getName().getString();
        String key = name.toLowerCase(Locale.ROOT);
        String uuid = player.getUUID().toString();
        if (!uuid.equals(names.get(key))) {
            names.put(key, uuid);
            save();
        }
    }

    public synchronized ResolvedPlayer resolve(MinecraftServer server, String input) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(input);
        if (online != null) {
            remember(online);
            return new ResolvedPlayer(online.getUUID(), online.getName().getString(), online);
        }

        try {
            UUID uuid = UUID.fromString(input);
            return new ResolvedPlayer(uuid, displayName(uuid), null);
        } catch (IllegalArgumentException ignored) {
        }

        String raw = names.get(input.toLowerCase(Locale.ROOT));
        if (raw == null) return null;

        try {
            UUID uuid = UUID.fromString(raw);
            return new ResolvedPlayer(uuid, displayName(uuid), null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public synchronized String displayName(UUID uuid) {
        String target = uuid.toString();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            if (target.equals(entry.getValue())) return entry.getKey();
        }
        return target;
    }

    private synchronized void load() {
        try {
            Files.createDirectories(directory);
            if (Files.notExists(file)) return;
            Map<String, String> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP_TYPE);
            if (loaded != null) {
                loaded.forEach((name, uuid) -> {
                    if (name != null && uuid != null) names.put(name.toLowerCase(Locale.ROOT), uuid);
                });
            }
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not load players.json");
            exception.printStackTrace();
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve("players.json.tmp");
            Files.writeString(temporary, GSON.toJson(names), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not save players.json");
            exception.printStackTrace();
        }
    }

    public record ResolvedPlayer(UUID uuid, String name, ServerPlayer onlinePlayer) {}
}
