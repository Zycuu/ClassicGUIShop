package com.zycu.guishop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Independent earning switches. Unknown registry content stays disabled until priced. */
public final class ShopModesConfig {
    public String passivePrefix = "Passive Income", mobsPrefix = "Cash Mobs", advancementsPrefix = "Advancement Hunter", warriorPrefix = "Warrior Mode";
    public double passiveMinutes = -1, mobCooldownMinutes = -1, warriorCooldownMinutes = -1, warriorMinimumOnlineMinutes = -1;
    public boolean passiveEnabled, mobsEnabled, advancementsEnabled, warriorEnabled;
    public double passiveAmount = 10, mobDefault = 0, advancementDefault = 100, warriorReward = 100, warriorDeduction = 50;
    public int passiveSeconds = 600, mobCooldownSeconds = 5, warriorCooldownSeconds = 3600, warriorMinimumOnlineSeconds = 600;
    public boolean allowSpawnerFarming, allowNegativeBalance, blockSameAddress = true;
    public String passiveColor = "aqua", mobsColor = "gold", advancementsColor = "light_purple", warriorColor = "red";
    public Map<String, Double> mobPrices = new LinkedHashMap<>(), advancementPrices = new LinkedHashMap<>();
    public void migrateMinutes() {
        if (passiveMinutes < 0) passiveMinutes = passiveSeconds / 60.0;
        if (mobCooldownMinutes < 0) mobCooldownMinutes = mobCooldownSeconds / 60.0;
        if (warriorCooldownMinutes < 0) warriorCooldownMinutes = warriorCooldownSeconds / 60.0;
        if (warriorMinimumOnlineMinutes < 0) warriorMinimumOnlineMinutes = warriorMinimumOnlineSeconds / 60.0;
    }
    public int passiveInterval() { return seconds(passiveMinutes, passiveSeconds); }
    public int mobCooldown() { return seconds(mobCooldownMinutes, mobCooldownSeconds); }
    public int warriorCooldown() { return seconds(warriorCooldownMinutes, warriorCooldownSeconds); }
    public int warriorMinimumOnline() { return seconds(warriorMinimumOnlineMinutes, warriorMinimumOnlineSeconds); }
    private static int seconds(double minutes, int legacy) { return minutes < 0 ? legacy : (int)Math.round(minutes * 60); }
    public String prefix(String source) { return switch(source) { case "passive" -> passivePrefix; case "mobs" -> mobsPrefix; case "advancement" -> advancementsPrefix; case "warrior" -> warriorPrefix; default -> source; }; }
    public void validate() {
        for (String name : new String[]{passivePrefix, mobsPrefix, advancementsPrefix, warriorPrefix})
            if (name == null || name.isBlank() || name.length() > 32 || name.chars().anyMatch(c -> Character.isISOControl(c) || c == 167)) throw new IllegalArgumentException("Mode prefix must contain 1 to 32 plain characters");
        for (double minutes : new double[]{passiveMinutes, mobCooldownMinutes, warriorCooldownMinutes, warriorMinimumOnlineMinutes})
            if (!Double.isFinite(minutes) || minutes < -1 || minutes > 10080 || minutes >= 0 && Math.abs(minutes * 60 - Math.rint(minutes * 60)) > .000001) throw new IllegalArgumentException("Mode minutes must resolve to whole seconds");
        for (double value : new double[]{passiveAmount, mobDefault, advancementDefault, warriorReward, warriorDeduction}) amount(value);
        if (passiveInterval() < 1 || passiveInterval() > 86400 || mobCooldown() < 0 || mobCooldown() > 86400
            || warriorCooldown() < 1 || warriorCooldown() > 604800 || warriorMinimumOnline() < 0 || warriorMinimumOnline() > 86400) throw new IllegalArgumentException("Invalid mode interval or cooldown");
        var colors = Set.of("aqua", "gold", "light_purple", "red", "green", "yellow", "blue", "white", "gray", "dark_aqua", "dark_green", "dark_red", "dark_purple", "black", "dark_blue", "dark_gray");
        for (String color : new String[]{passiveColor, mobsColor, advancementsColor, warriorColor}) if (!colors.contains(color == null ? "" : color)) throw new IllegalArgumentException("Invalid mode message color");
        for (var map : java.util.Arrays.asList(mobPrices, advancementPrices)) {
            if (map == null || map.size() > 10000) throw new IllegalArgumentException("Invalid mode payout list");
            for (var entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getKey().length() > 200 || !entry.getKey().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || entry.getValue() == null) throw new IllegalArgumentException("Invalid detected mode identifier");
                amount(entry.getValue());
            }
        }
    }
    private static void amount(double value) { if (!Double.isFinite(value) || value < 0 || value > 1_000_000) throw new IllegalArgumentException("Mode payouts must be between 0 and 1,000,000"); }
}
