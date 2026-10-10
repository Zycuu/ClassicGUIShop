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
                        String details, String operationId, long historyGeneration) {
        public Entry(String time, String player, String uuid, String action, String listing, String item,
                     int quantity, double unitPrice, double total, double balance, String details) {
            this(time, player, uuid, action, listing, item, quantity, unitPrice, total, balance, details, null, -1);
        }
    }
    public record Page(List<Entry> entries, int offset, boolean more, String warning) {}

    public static void record(ServerPlayer player, String action, String listing, String item,
                              int quantity, double unitPrice, double total, String details) {
        GuiShop.PLAYERS.remember(player);
        append(new Entry(Instant.now().toString(), player.getName().getString(), player.getUUID().toString(),
            action, listing, item, quantity, unitPrice, total,
            GuiShop.ECONOMY.balance(player.getUUID()), details));
    }
    public static synchronized void append(Entry entry) {
        try {
            appendChecked(journalFile(entry), entry, false);
            error = "";
        } catch (Exception exception) {
            error = "Transaction journal could not be written. Check server disk space and file permissions.";
            System.err.println("[ClassicGUIShop] " + error + " " + exception.getMessage());
        }
    }
    private static Path journalFile(Entry entry) { return DIRECTORY.resolve(Instant.parse(entry.time()).atZone(ZoneOffset.UTC).toLocalDate() + ".jsonl"); }
    public static synchronized void appendOperation(Entry entry) throws Exception { appendChecked(journalFile(entry), entry, true); }
    public static synchronized void appendAccountAudit(Entry entry) throws Exception { appendChecked(DIRECTORY.getParent().resolve("account-audit.jsonl"), entry, true); }
    private static void appendChecked(Path file, Entry entry, boolean once) throws Exception {
        Files.createDirectories(file.getParent());
        String operationId = entry.operationId();
        if (once && Files.exists(file)) try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            if (lines.anyMatch(line -> {
                try { var prior = AdminDocument.JSON.fromJson(line, Entry.class); return prior != null && java.util.Objects.equals(prior.operationId(), operationId); }
                catch (RuntimeException invalid) { return false; }
            })) return;
        }
        if (entry.historyGeneration() < 0) entry = new Entry(entry.time(), entry.player(), entry.uuid(), entry.action(), entry.listing(), entry.item(), entry.quantity(), entry.unitPrice(), entry.total(), entry.balance(), entry.details(), entry.operationId(), AccountOperations.historyGeneration(entry.uuid()));
        boolean separator = false;
        if (Files.exists(file) && Files.size(file) > 0) try (RandomAccessFile reader = new RandomAccessFile(file.toFile(), "r")) { reader.seek(reader.length() - 1); separator = reader.read() != '\n'; }
        byte[] body = ((separator ? "\n" : "") + AdminDocument.JSON.toJson(entry) + "\n").getBytes(StandardCharsets.UTF_8);
        try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            var buffer = java.nio.ByteBuffer.wrap(body); while (buffer.hasRemaining()) channel.write(buffer);
            if (once) channel.force(true);
        }
    }

    /** Bounded recent history; older complete records remain in the daily JSONL files. */
    public static Page query(String filter, int offset, boolean changes) throws Exception {
        return query(filter, offset, changes, null);
    }

    public static Page query(String filter, int offset, boolean changes, String playerUuid) throws Exception {
        return query(filter, offset, changes, playerUuid, "UTC");
    }
    public static Page query(String filter, int offset, boolean changes, String playerUuid, String zoneId) throws Exception {
        var local = java.time.ZoneId.of(zoneId == null || zoneId.isBlank() ? "UTC" : zoneId);
        String player = playerUuid == null || playerUuid.isBlank() ? null : java.util.UUID.fromString(playerUuid).toString();
        if (changes && player != null) throw new IllegalArgumentException("Player history cannot contain configuration changes");
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
                    if (player != null && !player.equals(entry.uuid())) continue;
                    if (!changes && entry.historyGeneration() < AccountOperations.historyGeneration(entry.uuid())) continue;
                    if (!search.isBlank() && !line.toLowerCase(Locale.ROOT).contains(search) && !JournalDates.matches(entry.time(), search, local)) continue;
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
