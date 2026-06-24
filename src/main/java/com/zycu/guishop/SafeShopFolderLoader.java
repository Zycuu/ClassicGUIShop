package com.zycu.guishop;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class SafeShopFolderLoader {
    private static final Gson GSON = new Gson();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("guishop");
    private static final Path FILE = DIRECTORY.resolve("folders.json");
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC);

    private SafeShopFolderLoader() {}

    public static ShopFolderStore load() {
        preserveMalformedFile();
        return ShopFolderStore.load();
    }

    private static void preserveMalformedFile() {
        if (!Files.exists(FILE)) return;

        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            GSON.fromJson(json, ShopFolderStore.class);
        } catch (Exception exception) {
            try {
                Files.createDirectories(DIRECTORY);
                String timestamp = BACKUP_TIME.format(Instant.now());
                Path backup = DIRECTORY.resolve("folders.json.corrupt-" + timestamp + ".backup");
                Files.move(FILE, backup, StandardCopyOption.REPLACE_EXISTING);
                System.err.println("[ClassicGUIShop] Malformed folders.json was preserved as "
                    + backup.getFileName() + ". A new folder layout will be generated.");
            } catch (Exception backupException) {
                System.err.println("[ClassicGUIShop] Could not preserve malformed folders.json. "
                    + "The existing file was left untouched.");
                backupException.printStackTrace();
                throw new IllegalStateException("Could not safely load folders.json", backupException);
            }
        }
    }
}
