package de.kesuaheli.keepinventorycost.events;

import de.kesuaheli.keepinventorycost.KeepInventoryCost;
import de.kesuaheli.keepinventorycost.config.PluginConfig;
import de.kesuaheli.keepinventorycost.economy.CostCalculator;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;
import java.util.Map;

public class PlayerDeathListener implements Listener {

    private final KeepInventoryCost plugin;

    public PlayerDeathListener(KeepInventoryCost plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PluginConfig cfg = this.plugin.getPluginConfig();

        this.plugin.debugLog("PlayerDeathEvent fired for " + player.getName()
                + " in world " + player.getWorld().getName());

        if (cfg == null || !cfg.valid()) {
            this.plugin.getLogger().warning("Config is invalid; skipping keep mechanic.");
            return;
        }

        if (event.getKeepInventory()) {
            this.plugin.debugLog("keepInventory already true for " + player.getName() + ", skipping.");
            return;
        }

        String worldName = player.getWorld().getName();
        if (cfg.disabledWorlds().contains(worldName.toLowerCase(Locale.ROOT))) {
            this.plugin.debugLog("World '" + worldName + "' is disabled, skipping.");
            return;
        }

        Player killer = player.getKiller();
        if (!cfg.keepOnPvp() && killer != null && !killer.equals(player)) {
            this.plugin.debugLog("PvP death by " + killer.getName() + " and keep-on-pvp is false, skipping.");
            return;
        }

        if (!this.plugin.getPlayersConfig().isEnabled(player.getUniqueId(), cfg.defaultEnabled())) {
            this.plugin.debugLog("KeepInventory is disabled for " + player.getName() + ", skipping.");
            return;
        }

        if (!cfg.chargeOnEmptyInventory() && isEmptyDeath(event, player)) {
            this.plugin.debugLog("Player " + player.getName() + " died with empty inventory and no XP, skipping charge.");
            return;
        }

        if (player.hasPermission("cookiekeepinv.bypass")) {
            applyKeep(event, player, cfg);
            this.plugin.playSound(player, cfg.paidSound());
            this.plugin.sendTemplate(
                    player,
                    "message.death.bypass",
                    "<green>Your items and XP were kept for free (bypass)!",
                    Map.of("player", player.getName(), "world", worldName)
            );
            this.plugin.debugLog("Applied free bypass for " + player.getName());
            return;
        }

        if (cfg.cooldownSeconds() > 0) {
            long remaining = this.plugin.getRemainingCooldownSeconds(player.getUniqueId(), cfg.cooldownSeconds());
            if (remaining > 0) {
                this.plugin.playSound(player, cfg.noMoneySound());
                this.plugin.sendTemplate(
                        player,
                        "message.death.cooldown",
                        "<red>KeepInventory is on cooldown for <gold>{cooldown}s<red>. Your items were dropped.",
                        Map.of(
                                "cooldown", String.valueOf(remaining),
                                "player", player.getName(),
                                "world", worldName
                        ),
                        String.valueOf(remaining)
                );
                this.plugin.debugLog("Cooldown active for " + player.getName() + ": " + remaining + "s left.");
                return;
            }
        }

        Economy eco = this.plugin.getEconomy();
        if (eco == null) {
            if (!this.plugin.setupEconomy()) {
                this.plugin.getLogger().severe("Economy provider is null!");
                if (cfg.autoDisableOnEconomyError()) {
                    this.plugin.clearEconomyOnFailure();
                    this.plugin.getLogger().severe("Economy unavailable; keep mechanic paused until reload.");
                }
                return;
            }
            eco = this.plugin.getEconomy();
        }

        double balance = eco.getBalance(player);
        double discount = this.plugin.getPlayerDiscount(player);
        String currencyName = eco.currencyNamePlural();
        CostCalculator.Result calc = CostCalculator.calculate(cfg, balance, discount, currencyName);

        this.plugin.debugLog("Balance=" + balance + ", discount=" + discount + "%, computedCost=" + calc.amount());

        if (calc.blockedByBalance()) {
            this.plugin.debugLog("Player " + player.getName() + " blocked by balance guard (" + balance + ").");
            return;
        }

        Map<String, String> placeholders = Map.of(
                "cost", calc.display(),
                "currency", currencyName == null ? "" : currencyName,
                "balance", String.format(Locale.US, "%.2f", balance),
                "discount", String.format(Locale.US, "%.0f", calc.discountPercent()),
                "player", player.getName(),
                "world", worldName
        );

        if (calc.amount() > 0.0) {
            if (!calc.affordable() || !eco.has(player, calc.amount())) {
                this.plugin.playSound(player, cfg.noMoneySound());
                this.plugin.sendTemplate(
                        player,
                        "message.death.no_money",
                        "<red>You didn't have enough {currency} to keep your items. Required: <gold>{cost}<red>.",
                        placeholders,
                        currencyName,
                        calc.display()
                );
                return;
            }

            EconomyResponse response = eco.withdrawPlayer(player, calc.amount());
            if (!response.transactionSuccess()) {
                this.plugin.getLogger().warning("Withdrawal FAILED for " + player.getName() + ": " + response.errorMessage);
                this.plugin.playSound(player, cfg.noMoneySound());
                this.plugin.sendTemplate(
                        player,
                        "message.death.no_money",
                        "<red>You didn't have enough {currency} to keep your items. Required: <gold>{cost}<red>.",
                        placeholders,
                        currencyName,
                        calc.display()
                );
                return;
            }
        }

        if (cfg.cooldownSeconds() > 0) {
            this.plugin.recordCooldown(player.getUniqueId());
        }

        applyKeep(event, player, cfg);
        this.plugin.playSound(player, cfg.paidSound());
        this.plugin.sendTemplate(
                player,
                "message.death.paid",
                "<green>You paid <gold>{cost}<green> to keep your items and XP!",
                placeholders,
                calc.display()
        );
    }

    private void applyKeep(PlayerDeathEvent event, Player player, PluginConfig cfg) {
        if (cfg.keepInventory()) {
            if (cfg.respectVanishingCurse()) {
                PlayerInventory inv = player.getInventory();
                for (int slot = 0; slot < inv.getSize(); slot++) {
                    ItemStack item = inv.getItem(slot);
                    if (item != null && !item.getType().isAir()
                            && item.containsEnchantment(Enchantment.VANISHING_CURSE)) {
                        inv.setItem(slot, null);
                    }
                }
            }
            event.setKeepInventory(true);
            event.getDrops().clear();
        }
        if (cfg.keepXp()) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
    }

    private boolean isEmptyDeath(PlayerDeathEvent event, Player player) {
        if (!event.getDrops().isEmpty()) {
            return false;
        }
        if (event.getDroppedExp() > 0 || player.getLevel() > 0 || player.getExp() > 0.0f) {
            return false;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
