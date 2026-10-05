package net.ru.terracraft;

import com.tchristofferson.configupdater.ConfigUpdater;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class MessageService {

    private final JavaPlugin plugin;
    private YamlConfiguration cfg;
    private String lang;
    private boolean strict;
    private MiniMessage mm;

    /**
     * Ключи верхнего уровня, которые ConfigUpdater НЕ должен перезаписывать.
     * Это все ключи, которые пользователь может кастомизировать.
     */
    private static final List<String> IGNORED_SECTIONS = List.of(
            "prefix",
            "help",
            "plugins-header",
            "real-plugins-header",
            "fake-plugins-header",
            "hidden-plugins-header",
            "plugin-line",
            "reload-done",
            "hidden-added",
            "hidden-removed",
            "hidden-not-found",
            "hidden-already",
            "hidden-list-empty",
            "added",
            "already-exists",
            "deleted",
            "not-found",
            "renamed",
            "rename-failed",
            "enabled",
            "disabled",
            "no-permission",
            "usage",
            "lang-changed",
            "lang-same",
            "lang-unknown"
    );

    public MessageService(JavaPlugin plugin, String lang, boolean strict) {
        this.plugin = plugin;
        this.strict = strict;
        rebuildMiniMessage();
        load(lang);
    }

    private void rebuildMiniMessage() {
        this.mm = strict
                ? MiniMessage.miniMessage()
                : MiniMessage.builder().strict(false).build();
    }

    // ---------------- Загрузка / обновление ----------------

    private File fileFor(String lang) {
        return new File(plugin.getDataFolder(), "messages/messages_" + lang + ".yml");
    }

    private void load(String lang) {
        this.lang = lang;
        File file = fileFor(lang);

        if (!file.exists()) {
            try {
                plugin.saveResource("messages/messages_" + lang + ".yml", false);
            } catch (IllegalArgumentException ignored) {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("Не создать " + file + ": " + e.getMessage());
                }
            }
        }

        // Подтягиваем только НОВЫЕ ключи; существующие (переведённые) не трогаем
        try {
            ConfigUpdater.update(plugin, "messages/messages_" + lang + ".yml",
                    file, IGNORED_SECTIONS);
        } catch (IOException e) {
            plugin.getLogger().warning("ConfigUpdater (messages): " + e.getMessage());
        }

        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    /** Перезагрузить с диска. */
    public void reload() {
        load(lang);
    }

    /** Сменить язык, сохранить в config.yml. */
    public boolean setLanguage(String newLang) {
        if (!newLang.equals("ru") && !newLang.equals("en")) return false;
        boolean changed = !newLang.equals(this.lang);
        plugin.getConfig().set("language", newLang);
        plugin.saveConfig();
        load(newLang);
        return changed;
    }

    /** Сменить язык БЕЗ записи в config.yml (для /fp reload). */
    public void setLanguageInternal(String newLang) {
        if (!newLang.equals("ru") && !newLang.equals("en")) return;
        if (newLang.equals(this.lang)) return;
        load(newLang);
    }

    public void setStrict(boolean strict) {
        if (this.strict == strict) return;
        this.strict = strict;
        rebuildMiniMessage();
    }

    public String language() { return lang; }
    public boolean strict()   { return strict; }

    // ---------------- Сообщения ----------------

    public Component get(String key, Map<String, String> placeholders) {
        String raw = convertLegacyHex(cfg.getString(key, "<red>missing: " + key));

        TagResolver.Builder resolver = TagResolver.builder();
        placeholders.forEach((k, v) ->
                resolver.resolver(Placeholder.parsed(k, convertLegacyHex(v))));

        return mm.deserialize(raw, resolver.build());
    }

    public Component get(String key) {
        return get(key, Map.of());
    }

    /** Экранирует < > в пользовательском вводе. */
    public static String escape(String input) {
        if (input == null) return null;
        return MiniMessage.miniMessage().escapeTags(input);
    }

    /** &#RRGGBB → <#RRGGBB> */
    private static String convertLegacyHex(String s) {
        if (s == null) return null;
        return s.replaceAll("&#([0-9a-fA-F]{6})", "<#$1>");
    }
}