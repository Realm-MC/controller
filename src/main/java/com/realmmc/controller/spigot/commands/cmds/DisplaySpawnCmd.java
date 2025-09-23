package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.entities.config.DisplayConfigLoader;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

@Cmd(cmd = "displayspawn", aliases = {})
public class DisplaySpawnCmd implements CommandInterface {
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSomente jogadores.");
            return;
        }
        Player p = (Player) sender;
        DisplayItemService service = Main.getInstance().getDisplayItemService();
        DisplayConfigLoader loader = Main.getInstance().getDisplayConfigLoader();

        if (args.length == 0) {
            p.sendMessage("§eUso: /" + label + " <id|all>");
            return;
        }

        if (args[0].equalsIgnoreCase("all")) {
            List<DisplayEntry> entries = loader.getEntries();
            if (entries.isEmpty()) {
                p.sendMessage("§cNenhuma entry em displays.yml");
                return;
            }
            double radius = 1.2;
            double angleStep = Math.PI * 2 / entries.size();
            int idx = 0;
            for (DisplayEntry e : entries) {
                if (e.getType() != DisplayEntry.Type.DISPLAY_ITEM) continue;
                Location base = getEntryLocationOr(p, e, p.getLocation().clone().add(Math.cos(idx * angleStep) * radius, 0.2, Math.sin(idx * angleStep) * radius));
                spawnFromEntry(p, service, e, base);
                idx++;
            }
            p.sendMessage("§aSpawnei " + entries.size() + " displays ao redor.");
            return;
        }

        int id;
        try { id = Integer.parseInt(args[0]); } catch (NumberFormatException ex) {
            p.sendMessage("§cID inválido.");
            return;
        }
        DisplayEntry entry = loader.getById(id);
        if (entry == null) {
            p.sendMessage("§cEntry não encontrada no displays.yml: id=" + id);
            return;
        }
        Location base = getEntryLocationOr(p, entry, p.getLocation().clone().add(p.getLocation().getDirection().normalize().multiply(1.0)).add(0, 0.2, 0));
        spawnFromEntry(p, service, entry, base);
        p.sendMessage("§aDisplay id=" + id + " spawnado.");
    }

    private void spawnFromEntry(Player p, DisplayItemService service, DisplayEntry entry, Location base) {
        ItemStack item = parseItem(entry.getItem());
        if (item == null) item = new ItemStack(Material.DIAMOND);

        List<String> lines = new ArrayList<>();
        if (entry.getMessage() != null && !entry.getMessage().isEmpty()) {
            lines.add(entry.getMessage());
        }
        service.show(p, base, item, lines, false);
    }

    private ItemStack parseItem(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            Material m = Material.matchMaterial(s, true);
            if (m != null) return new ItemStack(m);
        } catch (Throwable ignored) {}
        // TODO: resolver por ID custom futuramente
        return null;
    }

    private Location getEntryLocationOr(Player fallbackPlayer, DisplayEntry e, Location fallback) {
        if (e.getWorld() == null || e.getX() == null || e.getY() == null || e.getZ() == null) return fallback;
        var w = fallbackPlayer.getServer().getWorld(e.getWorld());
        if (w == null) return fallback;
        float yaw = e.getYaw() == null ? fallback.getYaw() : e.getYaw();
        float pitch = e.getPitch() == null ? fallback.getPitch() : e.getPitch();
        return new Location(w, e.getX(), e.getY(), e.getZ(), yaw, pitch);
    }
}
