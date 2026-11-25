package de.kesuaheli.keepinventorycost;

import de.kesuaheli.keepinventorycost.config.PluginConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigTest {

    private static final Logger TEST_LOGGER = Logger.getLogger("PluginConfigTest");

    @Test
    void loadsValidConfigAndNormalizesWorlds() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("cost", "percent");
        yaml.set("number", 5.0);
        yaml.set("min-percent-cost", 1.0);
        yaml.set("max-percent-cost", 500.0);
        yaml.set("disabled-worlds", java.util.List.of("PvP_Arena", "Nether_Event"));
        yaml.set("discount-tiers.cookiekeepinv.discount.vip", 25.0);
        yaml.set("discount-tiers.cookiekeepinv.discount.invalid", 150.0);

        PluginConfig cfg = PluginConfig.load(yaml, TEST_LOGGER);
        assertTrue(cfg.valid());
        assertEquals(PluginConfig.CostMode.PERCENT, cfg.costMode());
        assertTrue(cfg.disabledWorlds().contains("pvp_arena"));
        assertTrue(cfg.disabledWorlds().contains("nether_event"));
        assertEquals(1, cfg.discountTiers().size());
        assertEquals(25.0, cfg.discountTiers().get("cookiekeepinv.discount.vip"));
    }

    @Test
    void loadsRealConfigYmlDiscountTiersFromString() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                cost: percent
                number: 5.0
                discount-tiers:
                  cookiekeepinv.discount.vip: 25.0
                  cookiekeepinv.discount.mvp: 50.0
                """);

        PluginConfig cfg = PluginConfig.load(yaml, TEST_LOGGER);
        assertTrue(cfg.valid());
        assertEquals(2, cfg.discountTiers().size());
        assertEquals(25.0, cfg.discountTiers().get("cookiekeepinv.discount.vip"));
        assertEquals(50.0, cfg.discountTiers().get("cookiekeepinv.discount.mvp"));
    }

    @Test
    void respectsLegacyRequireOptInWhenDefaultEnabledMissing() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("cost", "money");
        yaml.set("number", 50);
        yaml.set("require-opt-in", true);

        PluginConfig cfg = PluginConfig.load(yaml, TEST_LOGGER);
        assertTrue(cfg.valid());
        assertFalse(cfg.defaultEnabled());
    }

    @Test
    void marksConfigInvalidWhenMaxBelowMinOrUnknownMode() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("cost", "gems");
        yaml.set("number", 10);
        yaml.set("min-percent-cost", 20.0);
        yaml.set("max-percent-cost", 5.0);

        PluginConfig cfg = PluginConfig.load(yaml, TEST_LOGGER);
        assertFalse(cfg.valid());
    }
}
