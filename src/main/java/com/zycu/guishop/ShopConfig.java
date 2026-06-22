package com.zycu.guishop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class ShopConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("guishop");
    private static final Path FILE = DIRECTORY.resolve("shop.json");

    public String currencySymbol = "$";
    public double startingBalance = 500.0;
    public double priceMultiplier = 1.0;
    public List<Category> categories = new ArrayList<>();

    public static ShopConfig load() {
        try {
            Files.createDirectories(DIRECTORY);
            if (Files.notExists(FILE)) {
                try (InputStream stream = ShopConfig.class.getResourceAsStream("/default_shop.json")) {
                    if (stream == null) {
                        throw new IOException("Missing bundled default_shop.json");
                    }
                    Files.copy(stream, FILE, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            ShopConfig config = GSON.fromJson(json, ShopConfig.class);
            if (config == null) {
                throw new IOException("shop.json was empty");
            }
            config.normalize();
            return config;
        } catch (Exception exception) {
            System.err.println("[GUIShop] Could not load shop.json. Using a minimal in-memory configuration.");
            exception.printStackTrace();
            ShopConfig fallback = new ShopConfig();
            fallback.normalize();
            return fallback;
        }
    }

    public Category category(String id) {
        for (Category category : categories) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return null;
    }

    public String money(double amount) {
        return currencySymbol + String.format("%,.2f", amount);
    }

    private void normalize() {
        if (currencySymbol == null) currencySymbol = "$";
        if (categories == null) categories = new ArrayList<>();
        if (!Double.isFinite(startingBalance) || startingBalance < 0) startingBalance = 0;
        if (!Double.isFinite(priceMultiplier) || priceMultiplier < 0) priceMultiplier = 1;

        for (Category category : categories) {
            if (category.id == null) category.id = "category";
            if (category.name == null) category.name = category.id;
            if (category.icon == null) category.icon = "minecraft:chest";
            if (category.items == null) category.items = new ArrayList<>();
            for (ShopItem item : category.items) {
                if (item.item == null) item.item = "minecraft:barrier";
                if (item.name == null) item.name = item.item;
                if (!Double.isFinite(item.buy) || item.buy < 0) item.buy = 0;
                if (!Double.isFinite(item.sell) || item.sell < 0) item.sell = 0;
            }
        }
    }

    public static final class Category {
        public String id;
        public String name;
        public String icon;
        public List<ShopItem> items = new ArrayList<>();
    }

    public static final class ShopItem {
        public String item;
        public String name;
        public double buy;
        public double sell;
    }
}
