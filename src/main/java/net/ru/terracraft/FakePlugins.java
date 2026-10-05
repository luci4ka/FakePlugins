package net.ru.terracraft;

import com.tchristofferson.configupdater.ConfigUpdater;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class FakePlugins extends JavaPlugin implements Listener {

    private static final int BSTATS_ID = 12345; // ← замени на свой pluginId

    private FakeStore store;
    private MessageService msg;
    private final Set<String> hiddenPlugins = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1) Сначала регистрируем права (иначе hasPermission вернёт false для OP)
        registerPermissions();

        // 2) Обновляем config.yml, не теряя пользовательские значения
        updateConfig();
        loadHidden();

        String lang = getConfig().getString("language", "ru");
        boolean strict = getConfig().getBoolean("min-message-strict", false);

        this.msg = new MessageService(this, lang, strict);
        this.store = new FakeStore(this, msg, hiddenPlugins);
        this.store.load();

        // 3) bStats
        new Metrics(this, BSTATS_ID);

        // 4) Brigadier-регистрация команды
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
        getLogger().info("FakePlugins v5 включён. Язык: " + msg.language()
                + ", strict: " + msg.strict()
                + ", фейков: " + store.size()
                + ", скрыто: " + hiddenPlugins.size());
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
        saveHidden();
    }

    public FakeStore store() { return store; }
    public MessageService messages() { return msg; }

    // ---------- Регистрация прав ----------

    private void registerPermissions() {
        PluginManager pm = getServer().getPluginManager();
        registerPerm(pm, "fakeplugins.list",     "Просмотр смешанного списка",     PermissionDefault.OP);
        registerPerm(pm, "fakeplugins.reallist", "Просмотр реального списка",      PermissionDefault.OP);
        registerPerm(pm, "fakeplugins.fakelist", "Просмотр фейкового списка",      PermissionDefault.OP);
        registerPerm(pm, "fakeplugins.add",      "Добавление фейка",               PermissionDefault.OP);
        registerPerm(pm, "fakeplugins.delete",   "Удаление фейка",                 PermissionDefault.OP);
        registerPerm(pm, "fakeplugins.rename",   "Переименование фейка",           PermissionDefault.OP);
        registerPerm(pm, "fakeplugins.enable",   "Включение фейка",                PermissionDefault.OP);
        registerPerm(pm, "fakeplugins.disable",  "Отключение фейка",               PermissionDefault.OP);
        registerPerm(pm, "fakeplugins.hide",     "Скрытие реального плагина",      PermissionDefault.OP);
        registerPerm(pm, "fakeplugins.reload",   "Перезагрузка конфигов",          PermissionDefault.OP);
        registerPerm(pm, "fakeplugins.help",     "Помощь",                         PermissionDefault.TRUE);
    }

    private static void registerPerm(PluginManager pm, String node, String desc, PermissionDefault def) {
        if (pm.getPermission(node) != null) return;
        pm.addPermission(new Permission(node, desc, def));
    }

    // ---------- Обновление config.yml ----------

    private void updateConfig() {
        File cfg = new File(getDataFolder(), "config.yml");
        try {
            ConfigUpdater.update(this, "config.yml", cfg,
                    // НЕ перезаписываем пользовательские значения
                    List.of("language", "hide-real-list", "min-message-strict", "hidden-plugins"));
        } catch (IOException e) {
            getLogger().warning("ConfigUpdater (config): " + e.getMessage());
        }
        reloadConfig();
    }

    // ---------- Скрытые ----------

    private void loadHidden() {
        hiddenPlugins.clear();
        hiddenPlugins.addAll(getConfig().getStringList("hidden-plugins"));
    }

    public void saveHidden() {
        getConfig().set("hidden-plugins", new ArrayList<>(hiddenPlugins));
        saveConfig();
    }

    // ---------- Reload ----------

    public void reloadAll() {
        updateConfig();
        loadHidden();

        String lang = getConfig().getString("language", "ru");
        boolean strict = getConfig().getBoolean("min-message-strict", false);

        msg.setStrict(strict);
        msg.setLanguageInternal(lang);
        msg.reload();

        store.reload(hiddenPlugins);

        getLogger().info("FakePlugins: конфигурация перезагружена.");
    }

    /** Используется /fp setlanguage. */
    public boolean setLanguage(String lang) {
        boolean changed = msg.setLanguage(lang);
        store.reload(hiddenPlugins);
        return changed;
    }

    // ---------- Перехват /pl и /plugins ----------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String raw = e.getMessage();
        if (raw == null || raw.isEmpty() || raw.charAt(0) != '/') return;

        String body = raw.substring(1).trim();
        int space = body.indexOf(' ');
        String label = (space < 0 ? body : body.substring(0, space)).toLowerCase(Locale.ROOT);

        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);

        if (!label.equals("pl") && !label.equals("plugins")) return;

        boolean hide = getConfig().getBoolean("hide-real-list", true);
        if (!hide && e.getPlayer().hasPermission("fakeplugins.reallist")) return;

        e.setCancelled(true);
        store.sendMixedList(e.getPlayer());
    }
}