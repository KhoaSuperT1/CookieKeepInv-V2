package de.kesuaheli.keepinventorycost;

import de.kesuaheli.keepinventorycost.commands.KeepInventoryAdminCommand;
import de.kesuaheli.keepinventorycost.commands.KeepInventoryAdminTab;
import de.kesuaheli.keepinventorycost.commands.KeepInventoryCommand;
import de.kesuaheli.keepinventorycost.commands.KeepInventoryTab;
import de.kesuaheli.keepinventorycost.config.PluginConfig;
import de.kesuaheli.keepinventorycost.events.PlayerDeathListener;
import de.kesuaheli.keepinventorycost.files.PlayersConfig;
import de.kesuaheli.keepinventorycost.messages.MessageService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class KeepInventoryCost extends JavaPlugin {

    private volatile Economy eco;
    private volatile PluginConfig pluginConfig;
    private PlayersConfig playersConfig;
    private MessageService messageService;
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Logger logger = getLogger();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.reloadConfig();

        this.messageService = new MessageService();
        this.playersConfig = new PlayersConfig(this);
        this.pluginConfig = PluginConfig.load(this.getConfig(), this.logger);

        if (!setupEconomy()) {
            this.logger.warning("Vault economy provider not found! Disabling CookieKeepInv-V2.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);

        PluginCommand keepInvCmd = getCommand("keepinventory");
        Objects.requireNonNull(keepInvCmd, "Command 'keepinventory' not found in plugin.yml");
        keepInvCmd.setExecutor(new KeepInventoryCommand(this));
        keepInvCmd.setTabCompleter(new KeepInventoryTab());

        PluginCommand adminCmd = getCommand("keepinventoryadmin");
        Objects.requireNonNull(adminCmd, "Command 'keepinventoryadmin' not found in plugin.yml");
        adminCmd.setExecutor(new KeepInventoryAdminCommand(this));
        adminCmd.setTabCompleter(new KeepInventoryAdminTab());

        logConfigSummary();
        this.logger.info("CookieKeepInv-V2 v" + getDescription().getVersion() + " enabled (Adventure: "
                + this.messageService.isAdventureAvailable() + ").");
    }

    @Override
    public void onDisable() {
        if (this.playersConfig != null) {
            this.playersConfig.flushAndShutdown();
        }
        this.cooldowns.clear();
        this.logger.info("CookieKeepInv-V2 disabled.");
    }

    public boolean setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager()
                .getRegistration(Economy.class);
        if (economyProvider == null) {
            return false;
        }
        this.eco = economyProvider.getProvider();
        this.logger.info("Economy provider hooked: " + this.eco.getName());
        return true;
    }

    public boolean reloadPlugin() {
        this.reloadConfig();
        if (this.playersConfig != null) {
            this.playersConfig.reloadConfig();
        }
        this.pluginConfig = PluginConfig.load(this.getConfig(), this.logger);
        if (this.eco == null) {
            setupEconomy();
        }
        logConfigSummary();
        return this.pluginConfig.valid();
    }

    private void logConfigSummary() {
        PluginConfig cfg = this.pluginConfig;
        if (!cfg.valid()) {
            this.logger.warning("Invalid config detected, cost mechanic paused until fixed.");
            return;
        }
        this.logger.info("Config loaded — mode: " + cfg.costMode()
                + ", number: " + cfg.costNumber()
                + ", defaultEnabled: " + cfg.defaultEnabled()
                + ", keepPvp: " + cfg.keepOnPvp()
                + ", cooldown: " + cfg.cooldownSeconds() + "s"
                + ", disabledWorlds: " + cfg.disabledWorlds().size());
    }

    public PluginConfig getPluginConfig() {
        return this.pluginConfig;
    }

    public Economy getEconomy() {
        return this.eco;
    }

    public void clearEconomyOnFailure() {
        this.eco = null;
    }

    public PlayersConfig getPlayersConfig() {
        return this.playersConfig;
    }

    public void setDebug(boolean enabled) {
        this.getConfig().set("debug", enabled);
        this.saveConfig();
        if (this.pluginConfig != null) {
            this.pluginConfig = this.pluginConfig.withDebug(enabled);
        }
    }

    public void debugLog(String message) {
        PluginConfig cfg = this.pluginConfig;
        if (cfg != null && cfg.debug()) {
            this.logger.info("[DEBUG] " + message);
        }
    }

    public double getPlayerDiscount(Player player) {
        if (player == null || this.pluginConfig == null) {
            return 0.0;
        }
        double best = 0.0;
        for (Map.Entry<String, Double> entry : this.pluginConfig.discountTiers().entrySet()) {
            if (player.hasPermission(entry.getKey()) && entry.getValue() > best) {
                best = entry.getValue();
            }
        }
        return best;
    }

    public long getRemainingCooldownSeconds(UUID uuid, int cooldownSeconds) {
        if (uuid == null || cooldownSeconds <= 0) {
            return 0L;
        }
        Long lastUsed = this.cooldowns.get(uuid);
        if (lastUsed == null) {
            return 0L;
        }
        long elapsedMs = System.currentTimeMillis() - lastUsed;
        long totalMs = cooldownSeconds * 1000L;
        if (elapsedMs >= totalMs) {
            return 0L;
        }
        return (totalMs - elapsedMs + 999L) / 1000L;
    }

    public void recordCooldown(UUID uuid) {
        if (uuid != null) {
            this.cooldowns.put(uuid, System.currentTimeMillis());
        }
    }

    public void playSound(Player player, String soundKey) {
        PluginConfig cfg = this.pluginConfig;
        if (player == null || cfg == null || !cfg.soundEnabled() || soundKey == null || soundKey.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);
        } catch (RuntimeException ex) {
            debugLog("Failed to play sound '" + soundKey + "': " + ex.getMessage());
        }
    }

    public String getConfigString(String path, String def) {
        return this.getConfig().getString(path, def);
    }

    public String getStateLabel(boolean enabled) {
        return enabled
                ? getConfigString("message.enabled", "<green>Enabled<reset>")
                : getConfigString("message.disabled", "<red>Disabled<reset>");
    }

    public void sendRaw(CommandSender receiver, String message) {
        String prefix = this.pluginConfig != null ? this.pluginConfig.prefix() : "";
        this.messageService.send(receiver, prefix, message);
    }

    public void sendTemplate(CommandSender receiver, String path, String def, Map<String, String> named, String... legacyArgs) {
        String template = getConfigString(path, def);
        String formatted = MessageService.formatPlaceholders(template, named, legacyArgs);
        sendRaw(receiver, formatted);
    }

    public OfflinePlayer resolveOfflinePlayer(String targetName) {
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            return online;
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(targetName)) {
                return offline;
            }
        }
        return Bukkit.getOfflinePlayer(targetName);
    }
}
