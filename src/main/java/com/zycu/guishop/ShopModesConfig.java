package com.zycu.guishop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Independent earning switches. Unknown registry content stays disabled until priced. */
public final class ShopModesConfig {
    public boolean passiveEnabled, mobsEnabled, advancementsEnabled, warriorEnabled;
    public double passiveAmount = 10, mobDefault = 0, advancementDefault = 100, warriorReward = 100, warriorDeduction = 50;
    public int passiveSeconds = 600, mobCooldownSeconds = 5, warriorCooldownSeconds = 3600, warriorMinimumOnlineSeconds = 600;
    public boolean allowSpawnerFarming, allowNegativeBalance, blockSameAddress = true;
    public String passiveColor = "aqua", mobsColor = "gold", advancementsColor = "light_purple", warriorColor = "red";
    public Map<String, Double> mobPrices = new LinkedHashMap<>(), advancementPrices = new LinkedHashMap<>();
    public void validate() {
        for (double value : new double[]{passiveAmount, mobDefault, advancementDefault, warriorReward, warriorDeduction}) amount(value);
        if (passiveSeconds < 1 || passiveSeconds > 86400 || mobCooldownSeconds < 0 || mobCooldownSeconds > 86400
            || warriorCooldownSeconds < 1 || warriorCooldownSeconds > 604800 || warriorMinimumOnlineSeconds < 0 || warriorMinimumOnlineSeconds > 86400) throw new IllegalArgumentException("Invalid mode interval or cooldown");
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
