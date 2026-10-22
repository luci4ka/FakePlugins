package ru.terracraft.fake;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

public class FakeCommand implements BasicCommand {

    private final FakePlugins plugin;
    private final FakeStore store;
    private final MessageService msg;
    private final Set<String> hidden;

    public FakeCommand(FakePlugins plugin, FakeStore store, MessageService msg, Set<String> hidden) {
        this.plugin = plugin;
        this.store = store;
        this.msg = msg;
        this.hidden = hidden;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender s = stack.getSender();

        if (args.length == 0) { help(s); return; }
        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help" -> help(s);

            case "list" -> { if (!perm(s, "fakeplugins.list")) return; store.sendMixedList(s); }
            case "reallist" -> { if (!perm(s, "fakeplugins.reallist")) return; store.sendRealList(s); }
            case "fakelist" -> { if (!perm(s, "fakeplugins.fakelist")) return; store.sendFakeList(s); }

            case "add" -> {
                if (!perm(s, "fakeplugins.add")) return;
                if (args.length < 2) { usage(s, "add <имя>"); return; }
                if (store.add(args[1]))
                    s.sendMessage(msg(s, "added", Map.of("name", MessageService.escape(args[1]))));
                else s.sendMessage(msg(s, "already-exists"));
            }
            case "delete", "remove", "del" -> {
                if (!perm(s, "fakeplugins.delete")) return;
                if (args.length < 2) { usage(s, "delete <имя>"); return; }
                if (store.remove(args[1]))
                    s.sendMessage(msg(s, "deleted", Map.of("name", MessageService.escape(args[1]))));
                else s.sendMessage(msg(s, "not-found"));
            }
            case "rename" -> {
                if (!perm(s, "fakeplugins.rename")) return;
                if (args.length < 3) { usage(s, "rename <старое> <новое>"); return; }
                if (store.rename(args[1], args[2]))
                    s.sendMessage(msg(s, "renamed", Map.of(
                            "old", MessageService.escape(args[1]),
                            "new", MessageService.escape(args[2])
                    )));
                else s.sendMessage(msg(s, "rename-failed"));
            }
            case "enable" -> {
                if (!perm(s, "fakeplugins.enable")) return;
                if (args.length < 2) { usage(s, "enable <имя>"); return; }
                if (store.setEnabled(args[1], true))
                    s.sendMessage(msg(s, "enabled", Map.of("name", MessageService.escape(args[1]))));
                else s.sendMessage(msg(s, "not-found"));
            }
            case "disable" -> {
                if (!perm(s, "fakeplugins.disable")) return;
                if (args.length < 2) { usage(s, "disable <имя>"); return; }
                if (store.setEnabled(args[1], false))
                    s.sendMessage(msg(s, "disabled", Map.of("name", MessageService.escape(args[1]))));
                else s.sendMessage(msg(s, "not-found"));
            }

            case "hide" -> {
                if (!perm(s, "fakeplugins.hide")) return;
                if (args.length < 2) { usage(s, "hide <плагин>"); return; }
                String name = args[1];
                if (Bukkit.getPluginManager().getPlugin(name) == null) {
                    s.sendMessage(msg(s, "hidden-not-found",
                            Map.of("name", MessageService.escape(name))));
                    return;
                }
                if (hidden.contains(name)) {
                    s.sendMessage(msg(s, "hidden-already",
                            Map.of("name", MessageService.escape(name))));
                    return;
                }
                hidden.add(name);
                plugin.saveHidden();
                s.sendMessage(msg(s, "hidden-added",
                        Map.of("name", MessageService.escape(name))));
            }
            case "unhide", "show" -> {
                if (!perm(s, "fakeplugins.hide")) return;
                if (args.length < 2) { usage(s, "unhide <плагин>"); return; }
                if (hidden.remove(args[1])) {
                    plugin.saveHidden();
                    s.sendMessage(msg(s, "hidden-removed",
                            Map.of("name", MessageService.escape(args[1]))));
                } else {
                    s.sendMessage(msg(s, "hidden-not-found",
                            Map.of("name", MessageService.escape(args[1]))));
                }
            }
            case "hidden" -> {
                if (!perm(s, "fakeplugins.hide")) return;
                if (hidden.isEmpty()) { s.sendMessage(msg(s, "hidden-list-empty")); return; }
                s.sendMessage(msg(s, "hidden-plugins-header",
                        Map.of("count", String.valueOf(hidden.size()))));
                hidden.forEach(n -> {
                    String safe = MessageService.escape(n);
                    s.sendMessage(msg(s, "plugin-line", Map.of(
                            "name", safe,
                            "name-colored", safe
                    )));
                });
            }

            case "reload" -> {
                if (!perm(s, "fakeplugins.reload")) return;
                plugin.reloadAll();
                s.sendMessage(msg(s, "reload-done"));
            }

            default -> help(s);
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (args.length <= 1) {
            String p = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return Stream.of("help","list","reallist","fakelist",
                            "add","delete","rename","enable","disable",
                            "hide","unhide","hidden","reload")
                    .filter(x -> x.startsWith(p)).toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String p = args[1].toLowerCase(Locale.ROOT);

            if (Set.of("delete","rename","enable","disable").contains(sub)) {
                return store.names().stream()
                        .filter(x -> x.toLowerCase(Locale.ROOT).startsWith(p)).toList();
            }
            if (Set.of("hide","unhide","show").contains(sub)) {
                return Stream.of(Bukkit.getPluginManager().getPlugins())
                        .map(Plugin::getName)
                        .filter(x -> x.toLowerCase(Locale.ROOT).startsWith(p)).toList();
            }
        }
        return List.of();
    }

    // ---------- Утилиты ----------

    /** Сообщение на языке отправителя: для игрока — локаль клиента, для консоли — ru. */
    private Component msg(CommandSender s, String key, Map<String, String> placeholders) {
        return s instanceof Player player
                ? msg.get(player, key, placeholders)
                : msg.get(key, placeholders);
    }

    private Component msg(CommandSender s, String key) {
        return msg(s, key, Map.of());
    }

    private boolean perm(CommandSender s, String node) {
        if (s.hasPermission(node)) return true;
        s.sendMessage(msg(s, "no-permission"));
        return false;
    }

    private void usage(CommandSender s, String rest) {
        s.sendMessage(msg(s, "usage", Map.of("usage", "/fakepl " + rest)));
    }

    private void help(CommandSender s) {
        s.sendMessage(msg(s, "help.header"));
        Stream.of("list","reallist","fakelist","add","delete","rename","enable","disable",
                        "hide","unhide","hidden","reload")
                .forEach(k -> s.sendMessage(msg(s, "help." + k)));
    }
}