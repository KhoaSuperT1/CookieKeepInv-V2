package de.kesuaheli.keepinventorycost.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public record PluginConfig(
        boolean valid,
        boolean debug,
        boolean defaultEnabled,
        boolean allowPlayerToggle,
        CostMode costMode,
        double costNumber,
        boolean requirePositiveBalance,
        double minPercentCost,
        double maxPercentCost,
        double minMoneyCost,
        boolean autoDisableOnEconomyError,
        boolean keepInventory,
        boolean keepXp,
        boolean keepOnPvp,
        boolean chargeOnEmptyInventory,
        boolean respectVanishingCurse,
        int cooldownSeconds,
        Set<String> disabledWorlds,
        Map<String, Double> discountTiers,
        boolean soundEnabled,
        String paidSound,
        String noMoneySound,
        String prefix
) {

    public enum CostMode {
        PERCENT,
        MONEY
    }

    public static PluginConfig load(FileConfiguration config, Logger logger) {
        boolean valid = true;
        boolean debug = config.getBoolean("debug", false);

        boolean defaultEnabled = config.getBoolean("default-enabled", true);
        if (config.isSet("require-opt-in") && config.getBoolean("require-opt-in", false)
                && !config.isSet("default-enabled")) {
            defaultEnabled = false;
        }
        boolean allowPlayerToggle = config.getBoolean("allow-player-toggle", true);

        CostMode costMode = CostMode.PERCENT;
        String costRaw = config.getString("cost", "");
        if (costRaw == null || costRaw.isBlank()) {
            logger.severe("Invalid config: cost must be 'percent' or 'money'");
            valid = false;
        } else {
            switch (costRaw.toLowerCase(Locale.ROOT).trim()) {
                case "percent" -> costMode = CostMode.PERCENT;
                case "money" -> costMode = CostMode.MONEY;
                default -> {
                    logger.severe("Invalid config: cost must be 'percent' or 'money' (got '" + costRaw + "')");
                    valid = false;
                }
            }
        }

        double costNumber = 0.0;
        if (!config.isDouble("number") && !config.isInt("number")) {
            logger.severe("Invalid config: number must be numeric");
            valid = false;
        } else {
            costNumber = config.getDouble("number", 0.0);
            if (!Double.isFinite(costNumber) || costNumber < 0.0) {
                logger.severe("Invalid config: number cannot be negative or non-finite (got " + costNumber + ")");
                valid = false;
            } else if (valid && costMode == CostMode.PERCENT && costNumber > 100.0) {
                logger.severe("Invalid config: percent cannot exceed 100 (got " + costNumber + ")");
                valid = false;
            }
        }

        boolean requirePositiveBalance = config.getBoolean("require-positive-balance", true);
        boolean autoDisableOnEconomyError = config.getBoolean("auto-disable-on-economy-error", true);

        double minPercentCost = config.getDouble("min-percent-cost", 1.0);
        if (!Double.isFinite(minPercentCost) || minPercentCost < 0.0) {
            logger.severe("Invalid config: min-percent-cost cannot be negative (got " + minPercentCost + ")");
            valid = false;
        }

        double maxPercentCost = config.getDouble("max-percent-cost", 0.0);
        if (!Double.isFinite(maxPercentCost) || maxPercentCost < 0.0) {
            logger.severe("Invalid config: max-percent-cost cannot be negative (got " + maxPercentCost + ")");
            valid = false;
        } else if (maxPercentCost > 0.0 && maxPercentCost < minPercentCost) {
            logger.severe("Invalid config: max-percent-cost (" + maxPercentCost
                    + ") cannot be lower than min-percent-cost (" + minPercentCost + ")");
            valid = false;
        }

        double minMoneyCost = config.getDouble("min-money-cost", 1.0);
        if (!Double.isFinite(minMoneyCost) || minMoneyCost < 0.0) {
            logger.severe("Invalid config: min-money-cost cannot be negative (got " + minMoneyCost + ")");
            valid = false;
        }

        boolean keepInventory = config.getBoolean("keep-inventory", true);
        boolean keepXp = config.getBoolean("keep-xp", true);
        boolean keepOnPvp = config.getBoolean("keep-on-pvp", true);
        boolean chargeOnEmptyInventory = config.getBoolean("charge-on-empty-inventory", false);
        boolean respectVanishingCurse = config.getBoolean("respect-vanishing-curse", true);

        int cooldownSeconds = config.getInt("cooldown-seconds", 0);
        if (cooldownSeconds < 0) {
            logger.severe("Invalid config: cooldown-seconds cannot be negative (got " + cooldownSeconds + ")");
            valid = false;
            cooldownSeconds = 0;
        }

        Set<String> disabledWorldsSet = new HashSet<>();
        List<String> rawWorlds = config.getStringList("disabled-worlds");
        for (String world : rawWorlds) {
            if (world != null && !world.isBlank()) {
                disabledWorldsSet.add(world.trim().toLowerCase(Locale.ROOT));
            }
        }

        Map<String, Double> discounts = new LinkedHashMap<>();
        ConfigurationSection discountSection = config.getConfigurationSection("discount-tiers");
        if (discountSection != null) {
            for (String key : discountSection.getKeys(true)) {
                if (discountSection.isConfigurationSection(key)) {
                    continue;
                }
                if (!discountSection.isDouble(key) && !discountSection.isInt(key)) {
                    continue;
                }
                double pct = discountSection.getDouble(key, 0.0);
                if (!Double.isFinite(pct) || pct < 0.0 || pct > 100.0) {
                    logger.warning("Ignoring invalid discount tier '" + key + "' value: " + pct);
                    continue;
                }
                discounts.put(key, pct);
            }
        }

        boolean soundEnabled = config.getBoolean("sound.enabled", true);
        String paidSound = config.getString("sound.paid", "entity.experience_orb.pickup");
        String noMoneySound = config.getString("sound.no-money", "entity.villager.no");
        String prefix = config.getString("message.prefix", "[<dark_purple>CookieKeepInv<reset>]");

        return new PluginConfig(
                valid,
                debug,
                defaultEnabled,
                allowPlayerToggle,
                costMode,
                costNumber,
                requirePositiveBalance,
                minPercentCost,
                maxPercentCost,
                minMoneyCost,
                autoDisableOnEconomyError,
                keepInventory,
                keepXp,
                keepOnPvp,
                chargeOnEmptyInventory,
                respectVanishingCurse,
                cooldownSeconds,
                Collections.unmodifiableSet(disabledWorldsSet),
                Collections.unmodifiableMap(discounts),
                soundEnabled,
                paidSound != null ? paidSound.trim() : "",
                noMoneySound != null ? noMoneySound.trim() : "",
                prefix != null ? prefix : ""
        );
    }

    public PluginConfig withDebug(boolean newDebug) {
        return new PluginConfig(
                valid,
                newDebug,
                defaultEnabled,
                allowPlayerToggle,
                costMode,
                costNumber,
                requirePositiveBalance,
                minPercentCost,
                maxPercentCost,
                minMoneyCost,
                autoDisableOnEconomyError,
                keepInventory,
                keepXp,
                keepOnPvp,
                chargeOnEmptyInventory,
                respectVanishingCurse,
                cooldownSeconds,
                disabledWorlds,
                discountTiers,
                soundEnabled,
                paidSound,
                noMoneySound,
                prefix
        );
    }
}
