package com.realmmc.controller.spigot.entities.actions;

import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class Actions {
    private static final Map<ActionType, Action> registry = new HashMap<>();

    static {
        registry.put(ActionType.MESSAGE, (player, ctx) -> {});
        registry.put(ActionType.TITLE, (player, ctx) -> {});
        registry.put(ActionType.PLAYER_COMMAND, (player, ctx) -> {});
        registry.put(ActionType.CONSOLE_COMMAND, (player, ctx) -> {});
        registry.put(ActionType.SOUND, (player, ctx) -> {});
    }

    public static void runAll(Player player, DisplayEntry entry, Location location) {
        List<String> actions = entry != null ? entry.getActions() : null;
        runAll(player, entry, location, actions);
    }

    public static void runAll(Player player, DisplayEntry entry, Location location, List<String> actions) {
        if (player == null || actions == null || actions.isEmpty()) return;
        ActionContext ctx = new ActionContext(player, entry, location);
        for (String raw : actions) {
            try {
                Parsed p = parse(raw);
                if (p == null) continue;
                switch (p.type) {
                    case MESSAGE -> handleMessage(player, ctx, p);
                    case TITLE -> handleTitle(player, ctx, p);
                    case PLAYER_COMMAND -> handlePlayerCommand(player, ctx, p);
                    case CONSOLE_COMMAND -> handleConsoleCommand(player, ctx, p);
                    case SOUND -> handleSound(player, ctx, p);
                }
            } catch (Exception ignored) {}
        }
    }

    private static void handleMessage(Player player, ActionContext ctx, Parsed p) {
        String msg = applyPlaceholders(p.arg0(), player, ctx);
        player.sendMessage(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(msg)));
    }

    private static void handleTitle(Player player, ActionContext ctx, Parsed p) {
        String title = applyPlaceholders(p.arg0(), player, ctx);
        String subtitle = applyPlaceholders(p.arg1(), player, ctx);
        var mm = MiniMessage.miniMessage();
        player.showTitle(Title.title(
                mm.deserialize(title != null ? title : ""),
                mm.deserialize(subtitle != null ? subtitle : "")
        ));
    }

    private static void handlePlayerCommand(Player player, ActionContext ctx, Parsed p) {
        String cmd = applyPlaceholders(p.arg0(), player, ctx);
        if (cmd == null || cmd.isBlank()) return;
        player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }

    private static void handleConsoleCommand(Player player, ActionContext ctx, Parsed p) {
        String cmd = applyPlaceholders(p.arg0(), player, ctx);
        if (cmd == null || cmd.isBlank()) return;
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        Bukkit.dispatchCommand(console, cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }

    private static void handleSound(Player player, ActionContext ctx, Parsed p) {
        try {
            Sound s = Sound.valueOf(p.arg0().toUpperCase(Locale.ROOT));
            float vol = p.argF(1, 1.0f);
            float pit = p.argF(2, 1.0f);
            player.playSound(player.getLocation(), s, vol, pit);
        } catch (Exception ignored) {}
    }

    private record Parsed(ActionType type, List<String> args) {
        String arg0() { return args.size() > 0 ? args.get(0) : null; }
        String arg1() { return args.size() > 1 ? args.get(1) : null; }
        float argF(int idx, float def) {
            try { return Float.parseFloat(args.get(idx)); } catch (Exception e) { return def; }
        }
    }

    private static Parsed parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        int idx = s.indexOf(':');
        if (idx <= 0) return null;
        String typeStr = s.substring(0, idx).trim().toUpperCase(Locale.ROOT);
        String rest = s.substring(idx + 1).trim();
        ActionType type;
        try { type = ActionType.valueOf(typeStr.replace(" ", "_")); } catch (Exception e) { return null; }
        String[] parts = rest.split(";");
        List<String> args = new ArrayList<>();
        for (String part : parts) args.add(part.trim());
        return new Parsed(type, args);
    }

    private static String applyPlaceholders(String s, Player player, ActionContext ctx) {
        if (s == null) return null;
        String res = s.replace("{player}", player.getName());
        if (ctx.getLocation().isPresent()) {
            Location l = ctx.getLocation().get();
            res = res.replace("{x}", String.valueOf(l.getX()))
                    .replace("{y}", String.valueOf(l.getY()))
                    .replace("{z}", String.valueOf(l.getZ()))
                    .replace("{world}", l.getWorld() != null ? l.getWorld().getName() : "world");
        }
        if (ctx.getEntry().isPresent()) {
            DisplayEntry e = ctx.getEntry().get();
            if (e.getId() != null) res = res.replace("{id}", e.getId());
            if (e.getItem() != null) res = res.replace("{item}", e.getItem());
        }
        return res;
    }
}