package com.zycu.guishop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EconomyStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Double>>() {}.getType();
    private final Path directory = FabricLoader.getInstance().getConfigDir().resolve("guishop");
    private final Path file = directory.resolve("balances.json");
    private final ShopConfig config;
    private final Map<String, Double> balances = new HashMap<>();

    public EconomyStore(ShopConfig config) {
        this.config = config;
        load();
    }

    public synchronized double balance(UUID player) {
        String key = player.toString();
        return balances.computeIfAbsent(key, ignored -> config.startingBalance);
    }

    public synchronized boolean withdraw(UUID player, double amount) {
        if (!validAmount(amount)) return false;
        double current = balance(player);
        if (current + 0.000001 < amount) return false;
        balances.put(player.toString(), round(current - amount));
        save();
        return true;
    }

    public synchronized void deposit(UUID player, double amount) {
        if (!validAmount(amount)) return;
        balances.put(player.toString(), round(balance(player) + amount));
        save();
    }

    public synchronized boolean transfer(UUID from, UUID to, double amount) {
        if (!validAmount(amount) || from.equals(to)) return false;
        double current = balance(from);
        if (current + 0.000001 < amount) return false;
        balances.put(from.toString(), round(current - amount));
        balances.put(to.toString(), round(balance(to) + amount));
        save();
        return true;
    }

    private boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount > 0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private synchronized void load() {
        try {
            Files.createDirectories(directory);
            if (Files.notExists(file)) return;
            Map<String, Double> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAP_TYPE);
            if (loaded != null) {
                loaded.forEach((key, value) -> {
                    if (value != null && Double.isFinite(value) && value >= 0) {
                        balances.put(key, round(value));
                    }
                });
            }
        } catch (Exception exception) {
            System.err.println("[GUIShop] Could not load balances.json");
            exception.printStackTrace();
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve("balances.json.tmp");
            Files.writeString(temporary, GSON.toJson(balances), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            System.err.println("[GUIShop] Could not save balances.json");
            exception.printStackTrace();
        }
    }
}
