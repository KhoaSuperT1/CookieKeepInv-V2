package de.kesuaheli.keepinventorycost.commands;

import de.kesuaheli.keepinventorycost.KeepInventoryCost;
import de.kesuaheli.keepinventorycost.config.PluginConfig;
import de.kesuaheli.keepinventorycost.economy.CostCalculator;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

public class KeepInventoryCommand implements CommandExecutor {

    private final KeepInventoryCost plugin;

    public KeepInventoryCommand(KeepInventoryCost plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            this.plugin.sendRaw(sender, "<red>Only players can execute this command!");
            return true;
        }

        PluginConfig cfg = this.plugin.getPluginConfig();
        boolean currentEnabled = this.plugin.getPlayersConfig().isEnabled(player.getUniqueId(), cfg.defaultEnabled());

        if (args.length == 0) {
            return handleSet(player, cfg, currentEnabled, !currentEnabled);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "get", "status" -> {
                if (args.length != 1) {
                    return false;
                }
                sendStatus(player, cfg, currentEnabled);
                return true;
            }
            case "cost" -> {
                if (args.length != 1) {
                    return false;
                }
                sendCostPreview(player, cfg);
                return true;
            }
            case "toggle" -> {
                if (args.length != 1) {
                    return false;
                }
                return handleSet(player, cfg, currentEnabled, !currentEnabled);
            }
            case "on" -> {
                if (args.length != 1) {
                    return false;
                }
                return handleSet(player, cfg, currentEnabled, true);
            }
            case "off" -> {
                if (args.length != 1) {
                    return false;
                }
                return handleSet(player, cfg, currentEnabled, false);
            }
            case "set" -> {
                if (args.length != 2) {
                    return false;
                }
                String val = args[1].toLowerCase(Locale.ROOT);
                if (!val.equals("true") && !val.equals("false")) {
                    return false;
                }
                return handleSet(player, cfg, currentEnabled, val.equals("true"));
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleSet(Player player, PluginConfig cfg, boolean currentEnabled, boolean newState) {
        if (!cfg.allowPlayerToggle()) {
            this.plugin.sendTemplate(
                    player,
                    "message.toggle-disabled",
                    "<red>Personal KeepInventory toggling is disabled by the server.",
                    Map.of("player", player.getName())
            );
            return true;
        }

        String stateLabel = this.plugin.getStateLabel(newState);
        if (newState != currentEnabled) {
            this.plugin.getPlayersConfig().setEnabled(player.getUniqueId(), newState);
            this.plugin.sendTemplate(
                    player,
                    "message.setting.set",
                    "Your KeepInv setting is now {state}!",
                    Map.of("state", stateLabel),
                    stateLabel
            );
        } else {
            this.plugin.sendTemplate(
                    player,
                    "message.setting.set_refuse",
                    "Your KeepInv setting is already {state}!",
                    Map.of("state", stateLabel),
                    stateLabel
            );
        }
        return true;
    }

    private void sendStatus(Player player, PluginConfig cfg, boolean enabled) {
        String stateLabel = this.plugin.getStateLabel(enabled);
        String costText = buildCostText(player, cfg);
        this.plugin.sendTemplate(
                player,
                "message.setting.get",
                "Your KeepInv setting is currently {state}. Estimated next death cost: <gold>{cost}<reset>.",
                Map.of("state", stateLabel, "cost", costText),
                stateLabel,
                costText
        );
    }

    private void sendCostPreview(Player player, PluginConfig cfg) {
        if (player.hasPermission("cookiekeepinv.bypass")) {
            this.plugin.sendTemplate(
                    player,
                    "message.cost.bypass",
                    "<green>You have free KeepInventory bypass permission!",
                    Map.of("player", player.getName())
            );
            return;
        }

        Economy eco = this.plugin.getEconomy();
        if (eco == null) {
            this.plugin.sendRaw(player, "<red>Economy provider is currently unavailable.");
            return;
        }
        double balance = eco.getBalance(player);
        double discount = this.plugin.getPlayerDiscount(player);
        CostCalculator.Result result = CostCalculator.calculate(cfg, balance, discount, eco.currencyNamePlural());

        this.plugin.sendTemplate(
                player,
                "message.cost.preview",
                "Estimated death cost: <gold>{cost}<reset> (Balance: <yellow>{balance}<reset>, Discount: <aqua>{discount}%<reset>).",
                Map.of(
                        "cost", result.display(),
                        "balance", String.format(Locale.US, "%.2f", balance),
                        "discount", String.format(Locale.US, "%.0f", result.discountPercent())
                ),
                result.display()
        );
    }

    private String buildCostText(Player player, PluginConfig cfg) {
        if (player.hasPermission("cookiekeepinv.bypass")) {
            return "FREE (bypass)";
        }
        Economy eco = this.plugin.getEconomy();
        if (eco == null) {
            return "N/A";
        }
        double balance = eco.getBalance(player);
        double discount = this.plugin.getPlayerDiscount(player);
        return CostCalculator.calculate(cfg, balance, discount, eco.currencyNamePlural()).display();
    }
}
