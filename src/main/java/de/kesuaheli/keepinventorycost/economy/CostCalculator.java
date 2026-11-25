package de.kesuaheli.keepinventorycost.economy;

import de.kesuaheli.keepinventorycost.config.PluginConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class CostCalculator {

    public record Result(
            double amount,
            double rawAmount,
            double discountPercent,
            boolean blockedByBalance,
            boolean affordable,
            String display
    ) {
    }

    private CostCalculator() {
    }

    public static Result calculate(PluginConfig cfg, double balance, double discountPercent, String currencyName) {
        String safeCurrency = currencyName == null ? "" : currencyName.trim();
        double clampedDiscount = Double.isFinite(discountPercent)
                ? Math.max(0.0, Math.min(100.0, discountPercent))
                : 0.0;

        if (!Double.isFinite(balance)) {
            return new Result(0.0, 0.0, clampedDiscount, true, false, formatCurrency(0.0, safeCurrency));
        }

        if (cfg.requirePositiveBalance() && balance <= 0.0) {
            return new Result(0.0, 0.0, clampedDiscount, true, false, formatCurrency(0.0, safeCurrency));
        }

        double rawAmount;
        if (cfg.costMode() == PluginConfig.CostMode.PERCENT) {
            rawAmount = balance * (cfg.costNumber() / 100.0);
            if (rawAmount < cfg.minPercentCost()) {
                rawAmount = cfg.minPercentCost();
            }
            if (cfg.maxPercentCost() > 0.0 && rawAmount > cfg.maxPercentCost()) {
                rawAmount = cfg.maxPercentCost();
            }
        } else {
            rawAmount = cfg.costNumber();
            if (rawAmount < cfg.minMoneyCost()) {
                rawAmount = cfg.minMoneyCost();
            }
        }

        rawAmount = roundTwoDecimals(Math.max(0.0, rawAmount));
        double finalAmount = roundTwoDecimals(Math.max(0.0, rawAmount * (1.0 - clampedDiscount / 100.0)));
        boolean affordable = finalAmount <= 0.0 || balance >= finalAmount;

        String moneyText = formatCurrency(finalAmount, safeCurrency);
        String display;
        if (cfg.costMode() == PluginConfig.CostMode.PERCENT) {
            if (clampedDiscount > 0.0) {
                display = String.format(Locale.US, "%.1f%% -%.0f%% (%s)", cfg.costNumber(), clampedDiscount, moneyText);
            } else {
                display = String.format(Locale.US, "%.1f%% (%s)", cfg.costNumber(), moneyText);
            }
        } else {
            display = moneyText;
        }

        return new Result(finalAmount, rawAmount, clampedDiscount, false, affordable, display);
    }

    private static double roundTwoDecimals(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String formatCurrency(double amount, String currency) {
        String numberPart = String.format(Locale.US, "%.2f", amount);
        return currency.isEmpty() ? numberPart : numberPart + " " + currency;
    }
}
