package de.kesuaheli.keepinventorycost.commands;

import de.kesuaheli.keepinventorycost.KeepInventoryCost;
import de.kesuaheli.keepinventorycost.config.PluginConfig;
import de.kesuaheli.keepinventorycost.economy.CostCalculator;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

public class KeepInventoryAdminCommand implements CommandExecutor {

    private final KeepInventoryCost plugin;

    public KeepInventoryAdminCommand(KeepInventoryCost plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            return false;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                boolean valid = this.plugin.reloadPlugin();
                if (valid) {
                    this.plugin.sendTemplate(sender, "message.reload",
                            "<green>Configuration reloaded successfully!", Map.of());
                } else {
                    this.plugin.sendTemplate(sender, "message.reload-invalid",
                            "<red>Configuration reloaded with errors! Check console; keep mechanic paused.", Map.of());
                }
                return true;
            }
            case "status" -> {
                sendAdminStatus(sender);
                return true;
            }
            case "check" -> {
                if (args.length != 2) {
                    return false;
                }
                handleCheck(sender, args[1]);
                return true;
            }
            case "set" -> {
                if (args.length != 3) {
                    return false;
                }
                String boolRaw = args[2].toLowerCase(Locale.ROOT);
                if (!boolRaw.equals("true") && !boolRaw.equals("false")) {
                    return false;
                }
                handleAdminSet(sender, args[1], boolRaw.equals("true"));
                return true;
            }
            case "debug" -> {
                if (args.length != 2) {
                    return false;
                }
                String boolRaw = args[1].toLowerCase(Locale.ROOT);
                if (!boolRaw.equals("true") && !boolRaw.equals("false")) {
                    return false;
                }
                boolean enable = boolRaw.equals("true");
                this.plugin.setDebug(enable);
                this.plugin.sendRaw(sender, "Debug mode set to " + this.plugin.getStateLabel(enable) + "<reset>.");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void sendAdminStatus(CommandSender sender) {
        PluginConfig cfg = this.plugin.getPluginConfig();
        Economy eco = this.plugin.getEconomy();
        String ecoName = eco != null ? eco.getName() : "NONE";
        String summary = "<gold>CookieKeepInv-V2 v" + this.plugin.getDescription().getVersion() + "<reset> | "
                + "Valid: " + cfg.valid() + " | "
                + "Eco: <aqua>" + ecoName + "<reset> | "
                + "Mode: <yellow>" + cfg.costMode() + " (" + cfg.costNumber() + ")<reset> | "
                + "Default: " + cfg.defaultEnabled() + " | "
                + "PvP: " + cfg.keepOnPvp() + " | "
                + "Cooldown: " + cfg.cooldownSeconds() + "s | "
                + "DisabledWorlds: " + cfg.disabledWorlds().size() + " | "
                + "CachedPlayers: " + this.plugin.getPlayersConfig().getCachedCount() + " | "
                + "Debug: " + cfg.debug();
        this.plugin.sendRaw(sender, summary);
    }

    private void handleCheck(CommandSender sender, String targetName) {
        OfflinePlayer target = this.plugin.resolveOfflinePlayer(targetName);
        String resolvedName = target.getName() != null ? target.getName() : targetName;
        PluginConfig cfg = this.plugin.getPluginConfig();
        boolean enabled = this.plugin.getPlayersConfig().isEnabled(target.getUniqueId(), cfg.defaultEnabled());

        Economy eco = this.plugin.getEconomy();
        String costInfo = "N/A";
        String balanceInfo = "N/A";
        if (eco != null) {
            double balance = eco.getBalance(target);
            double discount = target instanceof Player online ? this.plugin.getPlayerDiscount(online) : 0.0;
            CostCalculator.Result calc = CostCalculator.calculate(cfg, balance, discount, eco.currencyNamePlural());
            balanceInfo = String.format(Locale.US, "%.2f", balance);
            costInfo = calc.display();
        }

        this.plugin.sendRaw(sender, "Player <yellow>" + resolvedName + "<reset> -> KeepInv: "
                + this.plugin.getStateLabel(enabled) + "<reset>, Balance: <aqua>" + balanceInfo
                + "<reset>, Est. Cost: <gold>" + costInfo + "<reset>.");
    }

    private void handleAdminSet(CommandSender sender, String targetName, boolean enable) {
        OfflinePlayer target = this.plugin.resolveOfflinePlayer(targetName);
        String resolvedName = target.getName() != null ? target.getName() : targetName;
        this.plugin.getPlayersConfig().setEnabled(target.getUniqueId(), enable);
        this.plugin.sendRaw(sender, "Updated KeepInv for <yellow>" + resolvedName
                + "<reset> to " + this.plugin.getStateLabel(enable) + "<reset>.");
    }
}
