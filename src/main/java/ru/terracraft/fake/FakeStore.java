package ru.terracraft.fake;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FakeStore {

    private final Map<String, Boolean> fakes = new LinkedHashMap<>();
    private final FakePlugins plugin;
    private final MessageService msg;
    private final Path file;
    private Set<String> hidden;

    public FakeStore(FakePlugins plugin, MessageService msg, Set<String> hidden) {
        this.plugin = plugin;
        this.msg = msg;
        this.hidden = hidden;
        this.file = plugin.getDataFolder().toPath().resolve("plugins.txt");
    }

    public void reload(Set<String> hidden) {
        this.hidden = hidden;
        load();
    }

    public void load() {
        fakes.clear();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                try {
                    plugin.saveResource("plugins.txt", false);
                } catch (IllegalArgumentException ignored) {
                    // ресурса нет — создаём пустой
                    Files.writeString(file, "", StandardCharsets.UTF_8);
                }
            }

            if (!Files.exists(file)) return;

            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                String[] parts = s.split("\\s*:\\s*", 2);
                String name = parts[0].trim();
                if (name.isEmpty()) continue;
                boolean en = parts.length < 2
                        || !parts[1].toLowerCase(Locale.ROOT).startsWith("dis");
                fakes.put(name, en);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось прочитать plugins.txt: " + ex.getMessage());
        }
    }

    public void save() {
        StringBuilder sb = new StringBuilder();
        fakes.forEach((n, e) ->
                sb.append(n).append(" : ").append(e ? "enable" : "disable").append('\n'));
        try {
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось записать plugins.txt: " + ex.getMessage());
        }
    }

    public int size() { return fakes.size(); }
    public Map<String, Boolean> all() { return fakes; }
    public Set<String> names() { return fakes.keySet(); }

    public boolean add(String n) {
        if (fakes.containsKey(n)) return false;
        fakes.put(n, true); save(); return true;
    }
    public boolean remove(String n) {
        if (fakes.remove(n) == null) return false;
        save(); return true;
    }
    public boolean rename(String o, String nw) {
        if (!fakes.containsKey(o) || fakes.containsKey(nw)) return false;
        boolean e = fakes.remove(o);
        fakes.put(nw, e); save(); return true;
    }
    public boolean setEnabled(String n, boolean e) {
        if (!fakes.containsKey(n)) return false;
        fakes.put(n, e); save(); return true;
    }

    // ---------- Вывод ----------

    private Component line(String name, boolean enabled) {
        String safe = MessageService.escape(name);
        return msg.get("plugin-line", Map.of(
                "name", safe,
                "name-colored", (enabled ? "<green>" : "<red>") + safe
        ));
    }

    /**
     * Смешанный список: реальные (кроме скрытых) + фейковые, отсортированные по имени.
     */
    public void sendMixedList(CommandSender to) {
        // Собираем в один TreeMap: имя → enabled. Реальные и фейковые смешиваются.
        Map<String, Boolean> all = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (hidden.contains(p.getName())) continue;
            all.put(p.getName(), p.isEnabled());
        }
        all.putAll(fakes);

        to.sendMessage(msg.get("plugins-header",
                Map.of("count", String.valueOf(all.size()))));
        all.forEach((name, en) -> to.sendMessage(line(name, en)));
    }

    public void sendRealList(CommandSender to) {
        List<Plugin> visible = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (!hidden.contains(p.getName())) visible.add(p);
        }
        to.sendMessage(msg.get("real-plugins-header",
                Map.of("count", String.valueOf(visible.size()))));
        visible.forEach(p -> to.sendMessage(line(p.getName(), p.isEnabled())));
    }

    public void sendFakeList(CommandSender to) {
        to.sendMessage(msg.get("fake-plugins-header",
                Map.of("count", String.valueOf(fakes.size()))));
        fakes.forEach((n, en) -> to.sendMessage(line(n, en)));
    }
}