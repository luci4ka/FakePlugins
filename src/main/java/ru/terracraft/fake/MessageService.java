package ru.terracraft.fake;

import com.tchristofferson.configupdater.ConfigUpdater;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessageService {

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> cache = new HashMap<>();
    private boolean strict;
    private MiniMessage mm;

    private static final String DEFAULT_LANG = "ru";

    private static final List<String> IGNORED_SECTIONS = List.of(
            "prefix", "help", "plugins-header", "real-plugins-header",
            "fake-plugins-header", "hidden-plugins-header", "plugin-line",
            "reload-done", "hidden-added", "hidden-removed", "hidden-not-found",
            "hidden-already", "hidden-list-empty", "added", "already-exists",
            "deleted", "not-found", "renamed", "rename-failed", "enabled",
            "disabled", "no-permission", "usage", "lang-changed", "lang-same",
            "lang-unknown"
    );

    public MessageService(JavaPlugin plugin, boolean strict) {
        this.plugin = plugin;
        this.strict = strict;
        rebuildMiniMessage();
    }

    private void rebuildMiniMessage() {
        this.mm = strict
                ? MiniMessage.miniMessage()
                : MiniMessage.builder().strict(false).build();
    }

    // ---------------- Загрузка ----------------

    private File fileFor(String lang) {
        return new File(plugin.getDataFolder(), "messages/" + lang + ".yml");
    }

    private YamlConfiguration cfgFor(String lang) {
        return cache.computeIfAbsent(lang, l -> {
            File file = fileFor(l);
            if (!file.exists()) {
                // Файла нет — пробуем сохранить дефолт из ресурсов
                try {
                    plugin.saveResource("messages/" + l + ".yml", false);
                } catch (IllegalArgumentException ignored) {}
            }
            if (!file.exists()) {
                // Всё ещё нет — используем ru
                return cfgFor(DEFAULT_LANG);
            }
            try {
                ConfigUpdater.update(plugin, "messages/" + l + ".yml", file, IGNORED_SECTIONS);
            } catch (IOException e) {
                plugin.getLogger().warning("ConfigUpdater (" + l + "): " + e.getMessage());
            }
            return YamlConfiguration.loadConfiguration(file);
        });
    }

    /** Язык для игрока: локаль клиента, если файл есть, иначе ru. */
    private String langFor(Player player) {
        if (player == null) return DEFAULT_LANG;
        String code = player.locale().getLanguage().toLowerCase(Locale.ROOT);
        if (code.isEmpty()) return DEFAULT_LANG;

        // Проверяем, есть ли файл messages/{code}.yml в ресурсах или на диске
        File file = fileFor(code);
        if (file.exists()) return code;
        // Проверяем ресурс (если файл ещё не сохранён)
        if (plugin.getResource("messages/" + code + ".yml") != null) return code;

        return DEFAULT_LANG;
    }

    public void reload() {
        cache.clear();
    }

    public void setStrict(boolean strict) {
        if (this.strict == strict) return;
        this.strict = strict;
        rebuildMiniMessage();
    }

    public boolean strict() { return strict; }

    // ---------------- Сообщения ----------------

    /** Для игрока — локаль клиента с fallback на ru. */
    public Component get(Player player, String key, Map<String, String> placeholders) {
        return getFor(langFor(player), key, placeholders);
    }

    public Component get(Player player, String key) {
        return get(player, key, Map.of());
    }

    /** Для консоли и случаев без игрока — ru. */
    public Component get(String key, Map<String, String> placeholders) {
        return getFor(DEFAULT_LANG, key, placeholders);
    }

    public Component get(String key) {
        return get(key, Map.of());
    }

    private Component getFor(String lang, String key, Map<String, String> placeholders) {
        YamlConfiguration cfg = cfgFor(lang);
        String raw = convertLegacyHex(cfg.getString(key, "<red>missing: " + key));

        TagResolver.Builder resolver = TagResolver.builder();
        placeholders.forEach((k, v) ->
                resolver.resolver(Placeholder.parsed(k, convertLegacyHex(v))));

        return mm.deserialize(raw, resolver.build());
    }

    public static String escape(String input) {
        if (input == null) return null;
        return MiniMessage.miniMessage().escapeTags(input);
    }

    private static String convertLegacyHex(String s) {
        if (s == null) return null;
        return s.replaceAll("&#([0-9a-fA-F]{6})(?![0-9a-fA-F])", "<#$1>");
    }
}