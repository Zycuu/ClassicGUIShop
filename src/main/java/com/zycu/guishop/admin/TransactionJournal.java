package com.zycu.guishop.admin;

import com.zycu.guishop.GuiShop;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.RandomAccessFile;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Successful operations only, recorded on the server without requiring an open editor. */
public final class TransactionJournal {
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("guishop/transactions");
    private static volatile String error = "";
    private TransactionJournal() {}
    public record Entry(String time, String player, String uuid, String action, String listing,
                        String item, int quantity, double unitPrice, double total, double balance,
                        String details) {}
    public record Page(List<Entry> entries, int offset, boolean more, String warning) {}

    public static void record(ServerPlayer player, String action, String listing, String item,
                              int quantity, double unitPrice, double total, String details) {
        append(new Entry(Instant.now().toString(), player.getName().getString(), player.getUUID().toString(),
            action, listing, item, quantity, unitPrice, total,
            GuiShop.ECONOMY.balance(player.getUUID()), details));
    }
    public static synchronized void append(Entry entry) {
        try {
            Files.createDirectories(DIRECTORY);
            Path file = DIRECTORY.resolve(LocalDate.now(ZoneOffset.UTC) + ".jsonl");
            Files.writeString(file, AdminDocument.JSON.toJson(entry) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            error = "";
        } catch (Exception exception) {
            error = "Transaction journal could not be written. Check server disk space and file permissions.";
            System.err.println("[ClassicGUIShop] " + error + " " + exception.getMessage());
        }
    }

    /** Bounded recent history; older complete records remain in the daily JSONL files. */
    public static Page query(String filter, int offset, boolean changes) throws Exception {
        String search = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        if (search.length() > 128 || offset < 0 || offset > 10_000) throw new IllegalArgumentException("Invalid history filter or page");
        if (!Files.isDirectory(DIRECTORY)) return new Page(List.of(), offset, false, error);
        List<Path> files;
        try (var paths = Files.list(DIRECTORY)) {
            files = paths.filter(path -> path.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}\\.jsonl"))
                .sorted(Comparator.reverseOrder()).limit(30).toList();
        }
        List<Entry> matches = new ArrayList<>();
        int skipped = 0;
        for (Path file : files) {
            for (String line : recentLines(file)) {
                try {
                    Entry entry = AdminDocument.JSON.fromJson(line, Entry.class);
                    if (entry == null || entry.action() == null || changes != entry.action().equals("CONFIG_APPLY")) continue;
                    if (!search.isBlank() && !line.toLowerCase(Locale.ROOT).contains(search)) continue;
                    if (skipped++ < offset) continue;
                    matches.add(entry);
                    if (matches.size() > 100) return new Page(matches.subList(0, 100), offset, true, error);
                } catch (com.google.gson.JsonParseException ignored) {
                    // Ignore an interrupted final line; retain the original journal for inspection.
                }
            }
        }
        return new Page(matches, offset, false, error);
    }
    private static List<String> recentLines(Path file) throws Exception {
        try (RandomAccessFile reader = new RandomAccessFile(file.toFile(), "r")) {
            long end = reader.length(), start = Math.max(0, end - 2_000_000);
            reader.seek(start);
            byte[] bytes = new byte[(int) (end - start)]; reader.readFully(bytes);
            String tail = new String(bytes, StandardCharsets.UTF_8);
            if (start > 0) { int newline = tail.indexOf('\n'); tail = newline < 0 ? "" : tail.substring(newline + 1); }
            List<String> lines = new ArrayList<>(List.of(tail.split("\n")));
            Collections.reverse(lines); return lines;
        }
    }
}
