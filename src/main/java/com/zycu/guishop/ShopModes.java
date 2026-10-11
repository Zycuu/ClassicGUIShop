package com.zycu.guishop;

import com.zycu.guishop.admin.AccountOperations;
import com.zycu.guishop.admin.AdminDocument;
import com.zycu.guishop.admin.AdminEditorService;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import java.nio.file.*;
import java.util.*;

/** Server-only event handling. Disabled modes do not inspect or alter player wallets. */
public final class ShopModes {
    private ShopModes() {}
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("guishop/mode-progress.json");
    private static State state;
    private static int ticks;
    private static long lastError;
    private static final Map<UUID, Integer> sessionSeconds = new HashMap<>();
    private static final class Progress { int seconds, pendingSeconds; String pendingPrefix; long sequence; double pendingAmount; }
    private static final class State { int version = 1; Map<String, Progress> passive = new HashMap<>(); }
    public record Offer(String id, String name, double suggested) {}
    public record Catalog(List<Offer> entities, List<Offer> advancements) {}
    public static void initialize() {
        ticks = 0; sessionSeconds.clear();
        GuiShop.CONFIG.modes.validate();
        try {
            state = Files.exists(FILE) ? AdminDocument.JSON.fromJson(Files.readString(FILE), State.class) : new State();
            if (state == null || state.version != 1 || state.passive == null) throw new IllegalStateException("Invalid mode progress file");
            for (var value : state.passive.values()) if (value == null || value.seconds < 0 || value.sequence < 0 || !Double.isFinite(value.pendingAmount) || value.pendingAmount < 0) throw new IllegalStateException("Invalid passive income progress");
        } catch (Exception error) { throw new IllegalStateException("Could not load Shop Modes progress. Restore its backup before enabling modes.", error); }
    }
    private static void save() throws Exception { AccountOperations.writeAtomic(FILE, AdminDocument.JSON.toJson(state)); }
    public static void checkpoint() {
        if (state == null || state.passive.isEmpty()) return;
        try { save(); } catch (Exception error) { report(error); }
    }
    public static void disconnected(ServerPlayer player) { sessionSeconds.remove(player.getUUID()); checkpoint(); }
    private static boolean eligible(ServerPlayer player) { return !player.isSpectator() && !player.isCreative(); }
    public static void tick(MinecraftServer server) {
        if (state == null || ++ticks % 20 != 0) return;
        var config = GuiShop.CONFIG.modes;
        var present = new HashSet<UUID>();
        for (var player : server.getPlayerList().getPlayers()) {
            present.add(player.getUUID());
            int seconds = sessionSeconds.getOrDefault(player.getUUID(), 0); sessionSeconds.put(player.getUUID(), Math.min(86400, seconds + 1));
            if (!eligible(player)) continue;
            try {
                var progress = state.passive.get(player.getUUID().toString());
                if (!config.passiveEnabled && (progress == null || progress.pendingAmount == 0)) continue;
                if (progress == null) { progress = new Progress(); state.passive.put(player.getUUID().toString(), progress); }
                if (progress.pendingAmount == 0 && config.passiveEnabled && config.passiveAmount >= .01) {
                    progress.seconds = Math.min(86400, progress.seconds + 1);
                    if (progress.seconds >= config.passiveInterval()) { progress.pendingAmount = Math.round(config.passiveAmount * 100) / 100.0; progress.pendingSeconds = config.passiveInterval(); progress.pendingPrefix = config.passivePrefix; save(); }
                }
                if (progress.pendingAmount > 0) {
                    double amount = progress.pendingAmount;
                    String paidPrefix = progress.pendingPrefix == null ? config.passivePrefix : progress.pendingPrefix;
                    boolean paid = AccountOperations.mode(player, "passive", player.getUUID() + ":" + progress.sequence, amount, progress.pendingSeconds > 0 ? "Played for " + minutes(progress.pendingSeconds) : "Online interval (duration not recorded)", null, 0, false, null, 0, progress.pendingPrefix);
                    progress.seconds = 0; progress.sequence++; progress.pendingAmount = 0; progress.pendingSeconds = 0; progress.pendingPrefix = null; save();
                    if (paid) message(player, paidPrefix, config.passiveColor, "Received " + GuiShop.CONFIG.money(amount) + ".");
                }
            } catch (Exception error) { report(error); }
        }
        sessionSeconds.keySet().retainAll(present);
        if (config.passiveEnabled && ticks % 1200 == 0) try { save(); } catch (Exception error) { report(error); }
    }
    public static void killed(LivingEntity victim) {
        killed(victim, victim.getKillCredit());
    }
    public static void killed(LivingEntity victim, LivingEntity credit) {
        if (state == null || GuiShop.CONFIG == null || !(credit instanceof ServerPlayer killer) || !eligible(killer)) return;
        var config = GuiShop.CONFIG.modes;
        try {
            if (victim instanceof ServerPlayer player) {
                if (!config.warriorEnabled || !eligible(player) || player.getUUID().equals(killer.getUUID())) return;
                if (sessionSeconds.getOrDefault(killer.getUUID(), 0) < config.warriorMinimumOnline() || sessionSeconds.getOrDefault(player.getUUID(), 0) < config.warriorMinimumOnline()) return;
                if (config.blockSameAddress && player.getIpAddress().equals(killer.getIpAddress())) return;
                if (config.warriorReward < .01) return;
                // Unordered pair prevents bypass by taking turns as killer and victim.
                String pair = java.util.stream.Stream.of(killer.getUUID().toString(), player.getUUID().toString()).sorted().reduce((a, b) -> a + ":" + b).orElseThrow();
                double before = GuiShop.ECONOMY.readBalance(player.getUUID());
                boolean paid = AccountOperations.mode(killer, "warrior", player.getUUID() + ":" + player.level().getGameTime(), config.warriorReward, "Killed " + player.getName().getString(), player, config.warriorDeduction, config.allowNegativeBalance, "warrior:" + pair, config.warriorCooldown());
                if (paid) {
                    message(killer, config.warriorPrefix, config.warriorColor, "Received " + GuiShop.CONFIG.money(config.warriorReward) + " for defeating " + player.getName().getString() + ".");
                    double deduction = before - GuiShop.ECONOMY.readBalance(player.getUUID());
                    if (deduction > 0) message(player, config.warriorPrefix, config.warriorColor, "Lost " + GuiShop.CONFIG.money(deduction) + ".");
                }
            } else if (config.mobsEnabled && rewardMob(victim)) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
                double amount = config.mobPrices.getOrDefault(id,id.startsWith("minecraft:")
                    ? config.mobDefault>0?config.mobDefault:suggestedMob(victim.getType()):0);
                if (amount < .01) return;
                // Quick farming is an explicit global bypass, including spawner farms.
                int cooldown = config.allowSpawnerFarming ? 0 : config.mobCooldown();
                if (AccountOperations.mode(killer, "mobs", victim.getUUID().toString(), amount, "Killed " + victim.getType().getDescription().getString() + " [" + id + "]", null, 0, false, cooldown == 0 ? null : "mobs:" + killer.getUUID() + ":" + id, cooldown)) message(killer, config.mobsPrefix, config.mobsColor, "Received " + GuiShop.CONFIG.money(amount) + " for " + victim.getType().getDescription().getString() + ".");
            }
        } catch (Exception error) { report(error); }
    }
    public static void died(ServerPlayer player,net.minecraft.world.damagesource.DamageSource source){
        if(state==null||GuiShop.CONFIG==null||!eligible(player)||!GuiShop.CONFIG.modes.mishapEnabled)return;
        var config=GuiShop.CONFIG.modes;if(config.mishapAmount<.01)return;
        try{
            String reference=player.getUUID()+":"+player.level().getGameTime()+":"+player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
            double before=GuiShop.ECONOMY.readBalance(player.getUUID());
            if(AccountOperations.modeCharge(player,reference,config.mishapAmount,config.mishapAllowDebt,source.getLocalizedDeathMessage(player).getString(),config.mishapPrefix))
                message(player,config.mishapPrefix,config.mishapColor,"Death charge: "+GuiShop.CONFIG.money(before-GuiShop.ECONOMY.readBalance(player.getUUID()))+".");
        }catch(Exception error){report(error);}
    }
    public static void advancement(ServerPlayer player, AdvancementHolder advancement) {
        if (state == null || GuiShop.CONFIG == null || !GuiShop.CONFIG.modes.advancementsEnabled || !eligible(player)) return;
        String id = advancement.id().toString();
        if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) return;
        var config = GuiShop.CONFIG.modes;
        double amount = config.advancementPrices.getOrDefault(id, suggested(advancement));
        if (amount < .01) return;
        try {
            String reference = player.getUUID() + ":" + ItemStackData.shortHash(id);
            if (AccountOperations.mode(player, "advancement", reference, amount, "Completed " + advancement.value().name().map(Component::getString).orElse(id) + " [" + id + "]", null, 0, false, null, 0)) message(player, config.advancementsPrefix, config.advancementsColor, "Received " + GuiShop.CONFIG.money(amount) + " for " + advancement.value().name().map(Component::getString).orElse(id) + ".");
        } catch (Exception error) { report(error); }
    }
    private static double suggested(AdvancementHolder value) {
        if (value.value().display().isEmpty()) return 0;
        if (!value.id().getNamespace().equals("minecraft")) return GuiShop.CONFIG.modes.advancementDefault;
        // Recipes and invisible roots have no vanilla payout; explicitly priced entries can opt in.
        return value.value().display().map(display -> switch (display.type()) { case TASK -> 25.0; case GOAL -> 100.0; case CHALLENGE -> 500.0; }).orElse(0.0);
    }
    public static Catalog catalog(ServerPlayer player) {
        if (!AdminEditorService.allowed(player)) throw new SecurityException(AdminEditorService.DENIED);
        var entities = new ArrayList<Offer>();
        for(var id:BuiltInRegistries.ENTITY_TYPE.keySet().stream().sorted(Comparator.comparing(Object::toString)).toList()) {
            var type=BuiltInRegistries.ENTITY_TYPE.getValue(id);if(!type.canSummon())continue;
            try {
                // Classify without spawning, ticks, equipment initialization,
                // or adding anything to the world.
                if(rewardMob(type.create(player.level(),net.minecraft.world.entity.EntitySpawnReason.COMMAND)))
                    entities.add(new Offer(id.toString(),type.getDescription().getString(),suggestedMob(type)));
            }catch(Exception error){System.err.println("[ClassicGUIShop] Could not classify detected mob "+id+": "+error.getMessage());}
            if(entities.size()>=10000)break;
        }
        var advancements = player.level().getServer().getAdvancements().getAllAdvancements().stream().sorted(Comparator.comparing(value -> value.id().toString())).limit(10000)
            .map(value -> new Offer(value.id().toString(), value.value().name().map(Component::getString).orElse(value.id().toString()), suggested(value))).toList();
        return new Catalog(entities, advancements);
    }
    private static boolean rewardMob(net.minecraft.world.entity.Entity entity) {
        return entity instanceof net.minecraft.world.entity.Mob
            ||entity instanceof LivingEntity&&!(entity instanceof net.minecraft.world.entity.player.Player)
                &&entity.getType().getCategory()!=net.minecraft.world.entity.MobCategory.MISC;
    }
    private static double suggestedMob(net.minecraft.world.entity.EntityType<?> type) {
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if(!id.getNamespace().equals("minecraft"))return 0;
        return switch(id.getPath()) {
            case "villager","wandering_trader" -> 0;
            case "ender_dragon","wither","warden" -> 50;
            case "elder_guardian","ravager" -> 25;
            case "evoker","piglin_brute" -> 20;
            default -> switch(type.getCategory()) {
                case MONSTER -> 10;
                case AMBIENT -> 2;
                case WATER_AMBIENT,WATER_CREATURE,UNDERGROUND_WATER_CREATURE,AXOLOTLS -> 3;
                default -> 5;
            };
        };
    }
    private static String minutes(int seconds) { double value = seconds / 60.0; return (value == Math.rint(value) ? Long.toString((long)value) : String.format(Locale.ROOT, "%.3f", value)) + (seconds == 60 ? " minute" : " minutes"); }
    private static void message(ServerPlayer player, String mode, String color, String text) {
        player.sendSystemMessage(Component.literal("[" + mode + "] ").withStyle(ChatFormatting.valueOf(color.toUpperCase(Locale.ROOT))).append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }
    private static void report(Exception error) {
        long now = System.currentTimeMillis(); if (now - lastError < 10000) return; lastError = now;
        System.err.println("[ClassicGUIShop Shop Modes] Mode operation refused: " + error.getMessage());
    }
}
