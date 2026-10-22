package ru.terracraft.fake;

import com.tchristofferson.configupdater.ConfigUpdater;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class FakePlugins extends JavaPlugin implements Listener {

    /** Замени на свой pluginId с https://bstats.org */
    private static final int BSTATS_ID = 12345;

    private FakeStore store;
    private MessageService msg;
    private final Set<String> hiddenPlugins = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfig();
        loadHidden();

        boolean strict = getConfig().getBoolean("min-message-strict", false);

        this.msg = new MessageService(this, strict);
        this.store = new FakeStore(this, msg, hiddenPlugins);
        this.store.load();

        new Metrics(this, BSTATS_ID);

        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                    "fakepl",
                    "Управление фейковым списком плагинов",
                    List.of("fpl", "fp"),
                    new FakeCommand(this, store, msg, hiddenPlugins)
            );
        });

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("FakePlugins v" + getPluginMeta().getVersion()
                + " включён. Strict: " + msg.strict()
                + ", фейков: " + store.size()
                + ", скрыто: " + hiddenPlugins.size());
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
        if (hiddenPlugins != null) saveHidden();
    }

    public FakeStore store() { return store; }
    public MessageService messages() { return msg; }

    // ---------- Обновление config.yml ----------

    private void updateConfig() {
        File cfg = new File(getDataFolder(), "config.yml");
        try {
            ConfigUpdater.update(this, "config.yml", cfg,
                    List.of("hide-real-list", "min-message-strict", "hidden-plugins"));
        } catch (IOException e) {
            getLogger().warning("ConfigUpdater (config): " + e.getMessage());
        }
        reloadConfig();
    }

    // ---------- Скрытые ----------

    private void loadHidden() {
        hiddenPlugins.clear();
        List<String> list = getConfig().getStringList("hidden-plugins");
        if (list != null) hiddenPlugins.addAll(list);
    }

    public void saveHidden() {
        getConfig().set("hidden-plugins", new ArrayList<>(hiddenPlugins));
        saveConfig();
    }

    // ---------- Reload ----------

    public void reloadAll() {
        updateConfig();
        loadHidden();

        boolean strict = getConfig().getBoolean("min-message-strict", false);
        msg.setStrict(strict);
        msg.reload();

        store.reload(hiddenPlugins);

        getLogger().info("FakePlugins: конфигурация перезагружена.");
    }

    // ---------- Перехват /pl и /plugins ----------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (e.getPlayer().hasPermission("fakeplugins.reallist")) return;

        String raw = e.getMessage();
        if (raw == null || raw.isEmpty() || raw.charAt(0) != '/') return;

        String body = raw.substring(1).trim();
        int space = body.indexOf(' ');
        String label = (space < 0 ? body : body.substring(0, space)).toLowerCase(Locale.ROOT);

        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);

        if (!label.equals("pl") && !label.equals("plugins")) return;

        if (!getConfig().getBoolean("hide-real-list", true)) return;

        e.setCancelled(true);
        store.sendMixedList(e.getPlayer());
    }
}