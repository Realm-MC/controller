package com.realmmc.controller.spigot.entities.actions;

import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Actions {
    private static final Map<ActionType, Action> registry = new HashMap<>();

    static {
        registry.put(ActionType.MESSAGE, (player, ctx) -> {
        });
        registry.put(ActionType.TITLE, (player, ctx) -> {
        });
        registry.put(ActionType.PLAYER_COMMAND, (player, ctx) -> {
        });
        registry.put(ActionType.CONSOLE_COMMAND, (player, ctx) -> {
        });
        registry.put(ActionType.SOUND, (player, ctx) -> {
        });
    }

    public static void runAll(Player player, DisplayEntry entry, Location location) {
        List<String> actions = entry != null ? entry.getActions() : null;
        runAll(player, entry, location, actions);
    }

    public static void runAll(Player player, DisplayEntry entry, Location location, List<String> actions) {
        if (player == null || actions == null || actions.isEmpty()) return;
        for (String raw : actions) {
            try {
                if (!isLabelStyle(raw)) continue;
                runLabelStyle(player, entry, location, raw);
            } catch (Exception ignored) {
            }
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
        } catch (Exception ignored) {
        }
    }

    private record Parsed(ActionType type, List<String> args) {
        String arg0() {
            return args.size() > 0 ? args.get(0) : null;
        }

        String arg1() {
            return args.size() > 1 ? args.get(1) : null;
        }

        float argF(int idx, float def) {
            try {
                return Float.parseFloat(args.get(idx));
            } catch (Exception e) {
                return def;
            }
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
        try {
            type = ActionType.valueOf(typeStr.replace(" ", "_"));
        } catch (Exception e) {
            return null;
        }
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
        for (Map.Entry<String, String> en : ctx.getLabels().entrySet()) {
            String k = en.getKey();
            String v = en.getValue();
            if (k != null && v != null) res = res.replace("{" + k + "}", v);
        }
        return res;
    }

    private static boolean isLabelStyle(String raw) {
        return raw != null && raw.contains("=") && raw.contains(";");
    }

    private static void runLabelStyle(Player player, DisplayEntry entry, Location location, String raw) {
        Map<String, String> labels = parseLabels(raw);
        long delayTicks = parseDelayToTicks(labels.getOrDefault("delay", "2s"));
        ActionContext baseCtx = new ActionContext(player, entry, location, labels);
        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, String> e : labels.entrySet()) {
            resolved.put(e.getKey(), applyPlaceholders(e.getValue(), player, baseCtx));
        }
        ActionContext ctx = new ActionContext(player, entry, location, resolved);

        String actionCall = labels.get("action");
        if (actionCall == null || actionCall.isBlank()) return;

        Runnable task = () -> dispatchLabelAction(player, ctx, actionCall);
        try {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), task, delayTicks);
        } catch (Throwable t) {
            task.run();
        }
    }

    private static Map<String, String> parseLabels(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        String[] parts = raw.split(";");
        for (String part : parts) {
            String seg = part.trim();
            if (seg.isEmpty()) continue;
            int i = seg.indexOf('=');
            if (i <= 0) continue;
            String k = seg.substring(0, i).trim();
            String v = seg.substring(i + 1).trim();
            if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                v = v.substring(1, v.length() - 1);
            }
            map.put(k, v);
        }
        return map;
    }

    private static long parseDelayToTicks(String val) {
        if (val == null || val.isBlank()) return 40L;
        String s = val.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.endsWith("ms")) {
                double ms = Double.parseDouble(s.substring(0, s.length() - 2));
                return Math.max(0L, Math.round(ms / 50.0));
            }
            if (s.endsWith("s")) {
                double sec = Double.parseDouble(s.substring(0, s.length() - 1));
                return Math.max(0L, Math.round(sec * 20.0));
            }
            double sec = Double.parseDouble(s);
            return Math.max(0L, Math.round(sec * 20.0));
        } catch (Exception e) {
            return 40L;
        }
    }

    private static void dispatchLabelAction(Player player, ActionContext ctx, String actionCall) {
        String s = actionCall.trim();
        int p = s.indexOf('(');
        int q = s.lastIndexOf(')');
        String name = p > 0 ? s.substring(0, p).trim().toLowerCase(Locale.ROOT) : s.toLowerCase(Locale.ROOT);
        String inside = (p >= 0 && q > p) ? s.substring(p + 1, q) : "";
        List<String> args = new ArrayList<>();
        if (!inside.isEmpty()) {
            for (String part : inside.split(",")) {
                String v = part.trim();
                if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                    v = v.substring(1, v.length() - 1);
                }
                args.add(applyPlaceholders(v, player, ctx));
            }
        }

        switch (name) {
            case "message" ->
                    handleMessage(player, ctx, new Parsed(ActionType.MESSAGE, List.of(args.isEmpty() ? "" : args.get(0))));
            case "title" -> {
                String t = args.size() > 0 ? args.get(0) : "";
                String st = args.size() > 1 ? args.get(1) : "";
                handleTitle(player, ctx, new Parsed(ActionType.TITLE, List.of(t, st)));
            }
            case "playercmd", "player_command" ->
                    handlePlayerCommand(player, ctx, new Parsed(ActionType.PLAYER_COMMAND, List.of(args.isEmpty() ? "" : args.get(0))));
            case "consolecmd", "console_command" ->
                    handleConsoleCommand(player, ctx, new Parsed(ActionType.CONSOLE_COMMAND, List.of(args.isEmpty() ? "" : args.get(0))));
            case "sound" -> {
                String snd = args.size() > 0 ? args.get(0) : "ENTITY_PLAYER_LEVELUP";
                String vol = args.size() > 1 ? args.get(1) : "1.0";
                String pit = args.size() > 2 ? args.get(2) : "1.0";
                handleSound(player, ctx, new Parsed(ActionType.SOUND, List.of(snd, vol, pit)));
            }
            case "openmenu" -> {
                if (!args.isEmpty()) {
                    String menu = args.get(0);
                    handlePlayerCommand(player, ctx, new Parsed(ActionType.PLAYER_COMMAND, List.of("menu open " + menu)));
                }
            }
            case "tp", "teleport" -> {
                try {
                    double x = Double.parseDouble(args.get(0));
                    double y = Double.parseDouble(args.get(1));
                    double z = Double.parseDouble(args.get(2));
                    World w = player.getWorld();
                    if (args.size() > 3 && args.get(3) != null && !args.get(3).isEmpty()) {
                        World maybe = Bukkit.getWorld(args.get(3));
                        if (maybe != null) w = maybe;
                    }
                    player.teleport(new Location(w, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch()));
                } catch (Exception ignored) {
                }
            }
            case "give" -> {
                try {
                    String matStr = args.get(0).toUpperCase(Locale.ROOT);
                    int amount = args.size() > 1 ? Integer.parseInt(args.get(1)) : 1;
                    Material m = Material.valueOf(matStr);
                    ItemStack it = new ItemStack(m, Math.max(1, amount));
                    Map<Integer, ItemStack> left = player.getInventory().addItem(it);
                    if (!left.isEmpty()) {
                        left.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                    }
                } catch (Exception ignored) {
                }
            }
            case "broadcast" -> {
                try {
                    String msg = args.isEmpty() ? "" : args.get(0);
                    Bukkit.broadcast(MiniMessage.miniMessage().deserialize(msg));
                } catch (Exception ignored) {
                }
            }
            default -> { /* no-op or hook for custom handlers */ }
        }
    }
}
