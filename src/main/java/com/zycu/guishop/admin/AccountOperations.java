package com.zycu.guishop.admin;

import com.zycu.guishop.GuiShop;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable, replayable account operations; the existing balances.json format stays unchanged. */
public final class AccountOperations {
    private AccountOperations() {}
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("guishop/account-operations.json");
    private static volatile State state;
    private static boolean completing;
    private static final double MAX = 1_000_000_000_000.0;
    private static final class State {
        int version = 1;
        Map<String, Receipt> rewards = new LinkedHashMap<>();
        Map<String, Long> historyGenerations = new HashMap<>();
        Pending pending;
    }
    private record Receipt(String uuid, double amount, String operationId) {}
    private record Pending(String operationId, String kind, String uuid, String name, double before,
                           double after, String actor, String source, String reference, String reason,
                           String time, long generation) {}
    public record Change(String uuid, double expectedBalance, double balance, double expectedDefault, boolean reset) {}
    public record Result(AdminPlayers.Player player, boolean reset, String message) {}
    public record RewardResult(boolean credited, double balance, String name) {}

    public static synchronized void initialize() {
        state = null; completing = false; load(); ensureReady();
    }
    private static State load() {
        if (state != null) return state;
        try {
            State value = Files.exists(FILE) ? AdminDocument.JSON.fromJson(Files.readString(FILE), State.class) : new State();
            if (value == null || value.version != 1 || value.rewards == null || value.historyGenerations == null) throw new IllegalStateException("Invalid account operation ledger");
            for (long generation : value.historyGenerations.values()) if (generation < 0) throw new IllegalStateException("Invalid history generation");
            state = value; return value;
        } catch (Exception error) { throw new IllegalStateException("Could not load account operations. Restore the ledger or fix server file permissions before changing balances.", error); }
    }
    public static long historyGeneration(String uuid) {
        State value = state;
        if (value == null) synchronized (AccountOperations.class) { value = load(); }
        return value.historyGenerations.getOrDefault(uuid, 0L);
    }
    /** Complete a recorded operation before another balance mutation can overwrite its recovery value. */
    public static synchronized void ensureReady() {
        if (completing) return;
        if (load().pending != null) complete();
    }
    public static synchronized <T> T mutate(java.util.function.Supplier<T> action) { ensureReady(); return action.get(); }
    private static State copy() {
        var current = load(); var next = new State();
        next.rewards.putAll(current.rewards); next.historyGenerations.putAll(current.historyGenerations); next.pending = current.pending;
        return next;
    }
    private static void save(State value) {
        try { writeAtomic(FILE, AdminDocument.JSON.toJson(value)); state = value; }
        catch (Exception error) { throw new IllegalStateException("Account operation is pending. Fix server disk space or permissions before retrying. Do not change reward references.", error); }
    }
    public static void writeAtomic(Path file, String body) throws Exception {
        Files.createDirectories(file.getParent()); Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            var bytes = java.nio.ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static double money(double amount, boolean positive) {
        if (!Double.isFinite(amount) || amount < 0 || amount > MAX) throw new IllegalArgumentException("Balance or reward must be a finite amount between 0 and 1 trillion.");
        double rounded = Math.round(amount * 100) / 100.0;
        if (positive && rounded < 0.01) throw new IllegalArgumentException("Reward must be at least 0.01.");
        return rounded;
    }
    private static void authorize(CommandSourceStack actor) {
        if (!actor.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))) throw new SecurityException(AdminEditorService.DENIED);
    }
    private static UUID known(CommandSourceStack actor, String id) {
        UUID uuid = UUID.fromString(id);
        if (actor.getServer().getPlayerList().getPlayer(uuid) == null && !GuiShop.PLAYERS.knownPlayers().containsKey(uuid) && !GuiShop.ECONOMY.knownPlayers().contains(uuid)) throw new IllegalArgumentException("That player is unknown. Refresh the player list.");
        return uuid;
    }
    public static synchronized Result change(ServerPlayer viewer, Change change) {
        authorize(viewer.createCommandSourceStack()); ensureReady();
        if (change == null) throw new IllegalArgumentException("Missing balance change");
        UUID uuid = known(viewer.createCommandSourceStack(), change.uuid());
        double before = GuiShop.ECONOMY.readBalance(uuid);
        if (!Double.isFinite(change.expectedBalance()) || Double.compare(before, change.expectedBalance()) != 0) throw new IllegalStateException("This player's balance changed. Refresh and open the popup again.");
        if (change.reset() && Double.compare(GuiShop.CONFIG.startingBalance, change.expectedDefault()) != 0) throw new IllegalStateException("The server default balance changed. Refresh before resetting.");
        double after = money(change.reset() ? GuiShop.CONFIG.startingBalance : change.balance(), false);
        var player = AdminPlayers.player(viewer, uuid.toString());
        begin(new Pending(UUID.randomUUID().toString(), change.reset() ? "RESET" : "SET", uuid.toString(), player.name(), before, after,
            viewer.getName().getString() + " (" + viewer.getUUID() + ")", "admin", "", "", java.time.Instant.now().toString(), historyGeneration(uuid.toString())));
        return new Result(AdminPlayers.player(viewer, uuid.toString()), change.reset(), change.reset() ? "Balance reset to the server default; active transaction history cleared." : "Player balance updated on the server.");
    }
    public static synchronized RewardResult reward(CommandSourceStack actor, String target, double amount, String source, String reference, String reason) {
        authorize(actor); ensureReady(); amount = money(amount, true);
        source = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if (!source.matches("[a-z0-9][a-z0-9._:-]{0,39}") || reference == null || !reference.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}")) throw new IllegalArgumentException("Use a source up to 40 characters and a unique reference up to 96 characters; letters, numbers, dots, underscores, colons and dashes only.");
        if (reason == null) reason = "";
        if (reason.length() > 160 || reason.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Reward note must be at most 160 characters without control characters.");
        var resolved = GuiShop.PLAYERS.resolve(actor.getServer(), target);
        if (resolved == null) throw new IllegalArgumentException("That player is unknown. They must have joined the server at least once.");
        UUID uuid = known(actor, resolved.uuid().toString());
        var prior = load().rewards.get(source + "/" + reference);
        if (prior != null) {
            if (!prior.uuid().equals(uuid.toString()) || Double.compare(prior.amount(), amount) != 0) throw new IllegalArgumentException("This reward reference was already used for a different player or amount.");
            return new RewardResult(false, GuiShop.ECONOMY.readBalance(uuid), resolved.name());
        }
        double before = GuiShop.ECONOMY.readBalance(uuid), after = money(before + amount, false);
        begin(new Pending(UUID.randomUUID().toString(), "REWARD", uuid.toString(), resolved.name(), before, after, actor.getTextName(), source, reference, reason, java.time.Instant.now().toString(), historyGeneration(uuid.toString())));
        if (resolved.onlinePlayer() != null) resolved.onlinePlayer().sendSystemMessage(Component.literal("[Reward] Received " + GuiShop.CONFIG.money(amount) + " from " + source + ".").withStyle(net.minecraft.ChatFormatting.GREEN));
        return new RewardResult(true, after, resolved.name());
    }
    private static void begin(Pending pending) {
        State next = copy(); next.pending = pending; save(next); complete();
    }
    private static void complete() {
        Pending pending = load().pending; if (pending == null) return;
        completing = true;
        try {
            GuiShop.ECONOMY.setBalanceChecked(UUID.fromString(pending.uuid()), money(pending.after(), false));
            String details = "By " + pending.actor() + " | Previous " + pending.before() + " | " + pending.source() + " | " + pending.reference() + " | " + pending.reason();
            var record = new TransactionJournal.Entry(pending.time(), pending.name(), pending.uuid(), pending.kind().equals("REWARD") ? "REWARD" : "BALANCE_" + pending.kind(),
                pending.kind().equals("REWARD") ? pending.source() + ":" + pending.reference() : "Admin balance adjustment", "", 0, 0,
                pending.kind().equals("REWARD") ? money(pending.after() - pending.before(), true) : pending.after(), pending.after(), details, pending.operationId(), pending.generation());
            if (pending.kind().equals("RESET")) TransactionJournal.appendAccountAudit(record);
            else TransactionJournal.appendOperation(record);
            State next = copy();
            if (pending.kind().equals("RESET")) next.historyGenerations.put(pending.uuid(), Math.incrementExact(pending.generation()));
            if (pending.kind().equals("REWARD")) next.rewards.put(pending.source() + "/" + pending.reference(), new Receipt(pending.uuid(), money(pending.after() - pending.before(), true), pending.operationId()));
            next.pending = null; save(next);
        } catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Account operation is pending. Fix the server's disk space or file permissions before retrying.", error); }
        finally { completing = false; }
    }
}
