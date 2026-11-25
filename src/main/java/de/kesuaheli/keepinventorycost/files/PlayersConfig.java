package de.kesuaheli.keepinventorycost.files;

import de.kesuaheli.keepinventorycost.KeepInventoryCost;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class PlayersConfig {

    private final KeepInventoryCost plugin;
    private final ConcurrentHashMap<UUID, Boolean> cache = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private final ScheduledExecutorService saveExecutor;
    private volatile boolean closed = false;
    private FileConfiguration dataConfig = null;
    private File configFile = null;

    public PlayersConfig(KeepInventoryCost plugin) {
        this.plugin = plugin;
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CookieKeepInv-PlayersIO");
            thread.setDaemon(true);
            return thread;
        });
        saveDefaultConfig();
        reloadConfig();
    }

    public void reloadConfig() {
        synchronized (ioLock) {
            if (this.configFile == null) {
                this.configFile = new File(this.plugin.getDataFolder(), "players.yml");
            }
            this.dataConfig = YamlConfiguration.loadConfiguration(this.configFile);
            InputStream defaultStream = this.plugin.getResource("players.yml");
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                this.dataConfig.setDefaults(defaultConfig);
            }
            this.cache.clear();
            for (String key : this.dataConfig.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    if (this.dataConfig.contains(key + ".enabled")) {
                        this.cache.put(uuid, this.dataConfig.getBoolean(key + ".enabled"));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            this.dirty.set(false);
        }
    }

    public boolean isEnabled(UUID uuid, boolean defaultValue) {
        if (uuid == null) {
            return defaultValue;
        }
        return this.cache.getOrDefault(uuid, defaultValue);
    }

    public boolean hasCustomSetting(UUID uuid) {
        return uuid != null && this.cache.containsKey(uuid);
    }

    public void setEnabled(UUID uuid, boolean enabled) {
        if (uuid == null) {
            return;
        }
        this.cache.put(uuid, enabled);
        synchronized (ioLock) {
            if (this.dataConfig == null) {
                reloadConfig();
            }
            this.dataConfig.set(uuid.toString() + ".enabled", enabled);
            this.dirty.set(true);
        }
        scheduleAsyncSave();
    }

    public int getCachedCount() {
        return this.cache.size();
    }

    public FileConfiguration getConfig() {
        synchronized (ioLock) {
            if (this.dataConfig == null) {
                reloadConfig();
            }
            return this.dataConfig;
        }
    }

    public void saveConfig() {
        writeToDiskIfDirty();
    }

    public void saveDefaultConfig() {
        synchronized (ioLock) {
            if (this.configFile == null) {
                this.configFile = new File(this.plugin.getDataFolder(), "players.yml");
            }
            if (!this.configFile.exists()) {
                this.plugin.saveResource("players.yml", false);
            }
        }
    }

    public void flushAndShutdown() {
        this.closed = true;
        this.saveExecutor.shutdown();
        try {
            if (!this.saveExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                this.saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.saveExecutor.shutdownNow();
        }
        writeToDiskIfDirty();
    }

    private void scheduleAsyncSave() {
        if (this.closed) {
            writeToDiskIfDirty();
            return;
        }
        if (this.saveScheduled.compareAndSet(false, true)) {
            try {
                this.saveExecutor.schedule(() -> {
                    this.saveScheduled.set(false);
                    writeToDiskIfDirty();
                }, 500, TimeUnit.MILLISECONDS);
            } catch (RuntimeException ignored) {
                this.saveScheduled.set(false);
                writeToDiskIfDirty();
            }
        }
    }

    private void writeToDiskIfDirty() {
        synchronized (ioLock) {
            if (this.dataConfig == null || this.configFile == null || !this.dirty.compareAndSet(true, false)) {
                return;
            }
            try {
                this.dataConfig.save(this.configFile);
            } catch (IOException e) {
                this.dirty.set(true);
                this.plugin.getLogger().log(Level.SEVERE, "Could not save players.yml to " + this.configFile, e);
            }
        }
    }
}
