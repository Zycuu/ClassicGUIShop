package com.zycu.guishop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class ImportReminderConsoleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("guishop")
        .resolve("import-reminder.json");

    private ImportReminderConsoleConfig() {}

    public static boolean importReminderInConsole() {
        return load().importReminderInConsole();
    }

    private static ReminderFile load() {
        try {
            Files.createDirectories(FILE.getParent());
            ReminderFile file = Files.exists(FILE)
                ? GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), ReminderFile.class)
                : new ReminderFile();
            if (file == null) file = new ReminderFile();
            file.normalize();
            atomicWrite(GSON.toJson(file));
            return file;
        } catch (Exception exception) {
            System.err.println("[ClassicGUIShop] Could not load import reminder configuration. Using safe defaults.");
            exception.printStackTrace();
            return new ReminderFile();
        }
    }

    private static void atomicWrite(String content) throws Exception {
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
            Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class ReminderFile {
        List<String> _notes = List.of(
            "import_reminder_in_console controls whether ClassicGUIShop prints the external mod/data-pack import reminder in the server console after world loading and once every hour."
        );

        @SerializedName("import_reminder_in_console")
        Boolean importReminderInConsole = true;

        private void normalize() {
            if (importReminderInConsole == null) importReminderInConsole = true;
        }

        private boolean importReminderInConsole() {
            return !Boolean.FALSE.equals(importReminderInConsole);
        }
    }
}
