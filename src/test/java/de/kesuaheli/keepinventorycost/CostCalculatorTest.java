package de.kesuaheli.keepinventorycost;

import de.kesuaheli.keepinventorycost.config.PluginConfig;
import de.kesuaheli.keepinventorycost.economy.CostCalculator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostCalculatorTest {

    private PluginConfig baseConfig(PluginConfig.CostMode mode, double number, double minPercent, double maxPercent,
                                  double minMoney, boolean requirePositive) {
        return new PluginConfig(
                true, false, true, true,
                mode, number, requirePositive,
                minPercent, maxPercent, minMoney,
                true, true, true, true, false, true,
                0, Set.of(), Map.of(),
                true, "entity.experience_orb.pickup", "entity.villager.no",
                "[<dark_purple>CookieKeepInv<reset>]"
        );
    }

    @Test
    void calculatesPercentWithFloorAndCap() {
        PluginConfig cfg = baseConfig(PluginConfig.CostMode.PERCENT, 10.0, 5.0, 250.0, 1.0, true);

        CostCalculator.Result low = CostCalculator.calculate(cfg, 20.0, 0.0, "Coins");
        assertFalse(low.blockedByBalance());
        assertEquals(5.0, low.amount());
        assertTrue(low.affordable());

        CostCalculator.Result normal = CostCalculator.calculate(cfg, 1000.0, 0.0, "Coins");
        assertEquals(100.0, normal.amount());
        assertEquals("10.0% (100.00 Coins)", normal.display());

        CostCalculator.Result capped = CostCalculator.calculate(cfg, 10000.0, 0.0, "Coins");
        assertEquals(250.0, capped.amount());
    }

    @Test
    void appliesDiscountAndFlatMoneyFloor() {
        PluginConfig percentCfg = baseConfig(PluginConfig.CostMode.PERCENT, 10.0, 1.0, 0.0, 1.0, true);
        CostCalculator.Result discounted = CostCalculator.calculate(percentCfg, 1000.0, 25.0, "Dollars");
        assertEquals(100.0, discounted.rawAmount());
        assertEquals(75.0, discounted.amount());
        assertEquals(25.0, discounted.discountPercent());

        PluginConfig moneyCfg = baseConfig(PluginConfig.CostMode.MONEY, 2.0, 1.0, 0.0, 10.0, true);
        CostCalculator.Result floored = CostCalculator.calculate(moneyCfg, 5.0, 0.0, "Dollars");
        assertEquals(10.0, floored.amount());
        assertFalse(floored.affordable());
    }

    @Test
    void blocksZeroNegativeAndNonFiniteBalances() {
        PluginConfig cfg = baseConfig(PluginConfig.CostMode.PERCENT, 5.0, 1.0, 0.0, 1.0, true);

        assertTrue(CostCalculator.calculate(cfg, 0.0, 0.0, "Coins").blockedByBalance());
        assertTrue(CostCalculator.calculate(cfg, -50.0, 0.0, "Coins").blockedByBalance());
        assertTrue(CostCalculator.calculate(cfg, Double.NaN, 0.0, "Coins").blockedByBalance());
        assertTrue(CostCalculator.calculate(cfg, Double.POSITIVE_INFINITY, 0.0, "Coins").blockedByBalance());
    }

    @Test
    void handlesBlankCurrencyWithoutTrailingSpace() {
        PluginConfig cfg = baseConfig(PluginConfig.CostMode.MONEY, 42.5, 1.0, 0.0, 1.0, false);
        CostCalculator.Result res = CostCalculator.calculate(cfg, 100.0, 0.0, null);
        assertEquals("42.50", res.display());
    }
}
