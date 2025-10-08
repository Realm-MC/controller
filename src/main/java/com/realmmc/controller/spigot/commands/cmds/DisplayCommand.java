package com.realmmc.controller.spigot.commands.cmds;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.DisplayConfigLoader;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.config.HologramConfigLoader;
import com.realmmc.controller.spigot.entities.config.NPCConfigLoader;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Cmd(cmd = "display", aliases = {})
public class DisplayCommand implements CommandInterface {

    private static final long CONFIRM_TIMEOUT_MS = 30_000L;
    private static final Map<UUID, PendingRemoval> removeConfirm = new ConcurrentHashMap<>();
    private record PendingRemoval(String id, long expiresAt) {}
    private boolean confirmOrWarn(Player player, String id) {
        long now = System.currentTimeMillis();
        PendingRemoval pending = removeConfirm.get(player.getUniqueId());
        if (pending != null && (pending.expiresAt() < now)) {
            removeConfirm.remove(player.getUniqueId());
            pending = null;
        }
        if (pending == null || !pending.id().equals(id)) {
            removeConfirm.put(player.getUniqueId(), new PendingRemoval(id, now + CONFIRM_TIMEOUT_MS));
            Messages.send(player, "<yellow>Você está preste a apagar <white>" + id + "</white>, utilize novamente o comando em até 30s para confirmar.");
            return false;
        }
        removeConfirm.remove(player.getUniqueId());
        return true;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("controller.manager")) {
            Messages.send(sender, "<red>Apenas o grupo Gerente ou superior pode executar este comando.");
            return;
        }

        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Este comando só pode ser executado por um jogador.");
            return;
        }

        if (args.length < 1) {
            showHelp(sender);
            return;
        }

        String typeArg = args[0];

        if (typeArg.equalsIgnoreCase("remove")) {
            if (args.length >= 2) {
                String rid = args[1];
                if (!confirmOrWarn(player, rid)) return;
                boolean removed = false;
                try { DisplayConfigLoader d = new DisplayConfigLoader(); d.load(); if (d.removeEntry(rid)) { removed = true; Main.getInstance().getDisplayItemService().reload(); } } catch (Throwable ignored) {}
                try { HologramConfigLoader h = new HologramConfigLoader(); h.load(); if (h.removeEntry(rid)) { removed = true; Main.getInstance().getHologramService().reload(); } } catch (Throwable ignored) {}
                try { NPCConfigLoader n = new NPCConfigLoader(); n.load(); if (n.removeEntry(rid)) { removed = true; Main.getInstance().getNPCService().reloadAll(); } } catch (Throwable ignored) {}
                if (removed) { Messages.send(player, "<green>Removido ID '" + rid + "' e recarregado."); } else { Messages.send(player, "<red>ID '" + rid + "' não encontrado."); }
                return;
            }

            try {
                NPCConfigLoader n = new NPCConfigLoader(); n.load();
                var dir = player.getEyeLocation().getDirection().normalize();
                var origin = player.getEyeLocation().toVector();
                double maxDist = 8.0; double maxPerp = 0.75;
                DisplayEntry nearestNpc = null; double bestT = Double.MAX_VALUE;
                for (DisplayEntry e : n.getEntries()) {
                    if (e.getType() != DisplayEntry.Type.NPC) continue;
                    if (!player.getWorld().getName().equalsIgnoreCase(e.getWorld())) continue;
                    var head = new Location(player.getWorld(), e.getX(), e.getY(), e.getZ()).add(0, 1.6, 0).toVector();
                    var v = head.clone().subtract(origin); double t = dir.dot(v); if (t < 0 || t > maxDist) continue;
                    var perp = v.clone().subtract(dir.clone().multiply(t)); if (perp.length() <= maxPerp && t < bestT) { bestT = t; nearestNpc = e; }
                }
                if (nearestNpc != null) {
                    String nid = nearestNpc.getId();
                    if (!confirmOrWarn(player, nid)) return;
                    if (n.removeEntry(nid)) { Main.getInstance().getNPCService().reloadAll(); Messages.send(player, "<green>NPC removido (id=" + nid + ")"); return; }
                }
            } catch (Throwable ignored) {}

            RayTraceResult rt = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(), player.getEyeLocation().getDirection(), 8.0, 0.6,
                    e -> e instanceof ItemDisplay || e instanceof TextDisplay
            );
            Entity target = rt != null ? rt.getHitEntity() : player.getTargetEntity(6);
            if (target instanceof ItemDisplay || target instanceof TextDisplay) {
                DisplayConfigLoader d = new DisplayConfigLoader(); d.load();
                DisplayEntry nearest = null; double best = Double.MAX_VALUE;
                for (DisplayEntry e : d.getEntries()) {
                    if (e.getType() != DisplayEntry.Type.DISPLAY_ITEM) continue;
                    if (!player.getWorld().getName().equalsIgnoreCase(e.getWorld())) continue;
                    double dist = target.getLocation().distance(new Location(player.getWorld(), e.getX(), e.getY(), e.getZ()));
                    if (dist < best) { best = dist; nearest = e; }
                }
                if (nearest != null && best <= 3.0) {
                    String did = nearest.getId();
                    if (!confirmOrWarn(player, did)) return;
                    d.removeEntry(did);
                    Main.getInstance().getDisplayItemService().reload();
                    Messages.send(player, "<green>Display removido (id=" + did + ")");
                    return;
                }

                HologramConfigLoader h = new HologramConfigLoader(); h.load();
                nearest = null; best = Double.MAX_VALUE;
                for (DisplayEntry e : h.getEntries()) {
                    if (e.getType() != DisplayEntry.Type.HOLOGRAM) continue;
                    if (!player.getWorld().getName().equalsIgnoreCase(e.getWorld())) continue;
                    double dist = target.getLocation().distance(new Location(player.getWorld(), e.getX(), e.getY(), e.getZ()));
                    if (dist < best) { best = dist; nearest = e; }
                }
                if (nearest != null && best <= 3.0) {
                    String hid = nearest.getId();
                    if (!confirmOrWarn(player, hid)) return;
                    if (h.removeEntry(hid)) { Main.getInstance().getHologramService().reload(); Messages.send(player, "<green>Holograma removido (id=" + hid + ")"); return; }
                }
            }

            Messages.send(player, "<red>Nenhuma entidade alvo removível encontrada.");
            return;
        }
        if (typeArg.equalsIgnoreCase("info")) {
            if (args.length >= 2) {
                String qid = args[1];
                DisplayEntry found = null;
                String source = null;
                try {
                    DisplayConfigLoader d = new DisplayConfigLoader(); d.load();
                    found = d.getById(qid); if (found != null) source = "displays.yml";
                } catch (Throwable ignored) {}
                if (found == null) {
                    try { HologramConfigLoader h = new HologramConfigLoader(); h.load(); found = h.getById(qid); if (found != null) source = "holograms.yml"; } catch (Throwable ignored) {}
                }
                if (found == null) {
                    try { NPCConfigLoader n = new NPCConfigLoader(); n.load(); found = n.getById(qid); if (found != null) source = "npcs.yml"; } catch (Throwable ignored) {}
                }
                if (found == null) {
                    Messages.send(player, "<red>Nenhuma entry com id '" + qid + "' encontrada nos YMLs.");
                    return;
                }
                sendEntryInfo(player, found, source);
                return;
            }

            try {
                NPCConfigLoader n = new NPCConfigLoader(); n.load();
                var dir = player.getEyeLocation().getDirection().normalize();
                var origin = player.getEyeLocation().toVector();
                double maxDist = 8.0;
                double maxPerp = 0.75;
                DisplayEntry nearestNpc = null; double bestT = Double.MAX_VALUE;
                for (DisplayEntry e : n.getEntries()) {
                    if (e.getType() != DisplayEntry.Type.NPC) continue;
                    if (!player.getWorld().getName().equalsIgnoreCase(e.getWorld())) continue;
                    var head = new Location(player.getWorld(), e.getX(), e.getY(), e.getZ()).add(0, 1.6, 0).toVector();
                    var v = head.clone().subtract(origin);
                    double t = dir.dot(v);
                    if (t < 0 || t > maxDist) continue;
                    var perp = v.clone().subtract(dir.clone().multiply(t));
                    double dLine = perp.length();
                    if (dLine <= maxPerp && t < bestT) { bestT = t; nearestNpc = e; }
                }
                if (nearestNpc != null) { sendEntryInfo(player, nearestNpc, "npcs.yml"); return; }
            } catch (Throwable ignored) {}

            RayTraceResult rt = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(), player.getEyeLocation().getDirection(), 8.0, 0.3,
                    e -> e instanceof ItemDisplay || e instanceof TextDisplay
            );
            Entity target = rt != null ? rt.getHitEntity() : player.getTargetEntity(6);
            switch (target) {
                case null -> {
                    Messages.send(player, "<red>Nenhuma entidade em foco para inspecionar.");
                    return;
                }

                case TextDisplay td when Main.getInstance().getNPCService().isNameHologram(td.getUniqueId()) -> {
                    NPCConfigLoader n = new NPCConfigLoader();
                    n.load();
                    DisplayEntry nearest = null;
                    double best = Double.MAX_VALUE;
                    for (DisplayEntry e : n.getEntries()) {
                        if (e.getType() != DisplayEntry.Type.NPC) continue;
                        if (!player.getWorld().getName().equalsIgnoreCase(e.getWorld())) continue;
                        double d = td.getLocation().distance(new Location(player.getWorld(), e.getX(), e.getY() + 2.0, e.getZ()));
                        if (d < best) {
                            best = d;
                            nearest = e;
                        }
                    }
                    if (nearest != null && best <= 4.0) {
                        sendEntryInfo(player, nearest, "npcs.yml");
                        return;
                    }
                    Messages.send(player, "<red>NPC alvo não encontrado no YAML.");
                    return;
                }

                case ItemDisplay itemDisplay -> {
                    DisplayConfigLoader d = new DisplayConfigLoader();
                    d.load();
                    DisplayEntry nearest = null;
                    double best = Double.MAX_VALUE;
                    for (DisplayEntry e : d.getEntries()) {
                        if (e.getType() != DisplayEntry.Type.DISPLAY_ITEM) continue;
                        if (!player.getWorld().getName().equalsIgnoreCase(e.getWorld())) continue;
                        double dist = target.getLocation().distance(new Location(player.getWorld(), e.getX(), e.getY(), e.getZ()));
                        if (dist < best) {
                            best = dist;
                            nearest = e;
                        }
                    }
                    if (nearest != null && best <= 3.0) {
                        sendEntryInfo(player, nearest, "displays.yml");
                        return;
                    }
                    Messages.send(player, "<red>Display Item alvo não encontrado no YAML.");
                    return;
                }

                case TextDisplay textDisplay -> {
                    HologramConfigLoader h = new HologramConfigLoader();
                    h.load();
                    DisplayEntry nearest = null;
                    double best = Double.MAX_VALUE;
                    for (DisplayEntry e : h.getEntries()) {
                        if (e.getType() != DisplayEntry.Type.HOLOGRAM) continue;
                        if (!player.getWorld().getName().equalsIgnoreCase(e.getWorld())) continue;
                        double dist = target.getLocation().distance(new Location(player.getWorld(), e.getX(), e.getY(), e.getZ()));
                        if (dist < best) {
                            best = dist;
                            nearest = e;
                        }
                    }
                    if (nearest != null && best <= 3.0) {
                        sendEntryInfo(player, nearest, "holograms.yml");
                        return;
                    }
                    Messages.send(player, "<red>Holograma alvo não encontrado no YAML.");
                    return;
                }
                default -> {
                }
            }

            Messages.send(player, "<red>Tipo de alvo não suportado para info.");
            return;
        }

        if (typeArg.equalsIgnoreCase("reload")) {
            if (args.length >= 2) {
                String id = args[1];
                try {
                    DisplayConfigLoader dcl = new DisplayConfigLoader();
                    dcl.load();
                    if (dcl.getById(id) != null) {
                        Main.getInstance().getDisplayItemService().reload();
                        Messages.send(player, "<green>Displays recarregados (id=" + id + ")");
                        return;
                    }
                } catch (Exception ignored) {
                }
                try {
                    HologramConfigLoader hcl = new HologramConfigLoader();
                    hcl.load();
                    if (hcl.getById(id) != null) {
                        Main.getInstance().getHologramService().reload();
                        Messages.send(player, "<green>Hologramas recarregados (id=" + id + ")");
                        return;
                    }
                } catch (Exception ignored) {
                }
                try {
                    NPCConfigLoader ncl = new NPCConfigLoader();
                    ncl.load();
                    if (ncl.getById(id) != null) {
                        Main.getInstance().getNPCService().reloadAll();
                        Messages.send(player, "<green>NPCs recarregados (id=" + id + ")");
                        return;
                    }
                } catch (Exception ignored) {
                }

                Messages.send(player, "<red>Nenhuma entidade com id '" + id + "' encontrada nos YMLs.");
            } else {
                RayTraceResult rt = player.getWorld().rayTraceEntities(
                        player.getEyeLocation(),
                        player.getEyeLocation().getDirection(),
                        8.0,
                        0.3,
                        e -> e instanceof ItemDisplay || e instanceof TextDisplay
                );
                Entity target = rt != null ? rt.getHitEntity() : player.getTargetEntity(6);
                if (target instanceof ItemDisplay) {
                    Main.getInstance().getDisplayItemService().reload();
                    Messages.send(player, "<green>Displays recarregados (alvo: ItemDisplay)");
                    return;
                }
                if (target instanceof TextDisplay td) {
                    if (Main.getInstance().getNPCService().isNameHologram(td.getUniqueId())) {
                        Main.getInstance().getNPCService().reloadAll();
                        Messages.send(player, "<green>NPCs recarregados (alvo: holograma de nome)");
                        return;
                    }
                    Main.getInstance().getHologramService().reload();
                    Messages.send(player, "<green>Hologramas recarregados (alvo: TextDisplay)");
                    return;
                }

                var dir = player.getEyeLocation().getDirection().normalize();
                var origin = player.getEyeLocation().toVector();
                double maxDist = 6.0;
                double cosThreshold = 0.8;
                for (Entity e : player.getWorld().getNearbyEntities(player.getEyeLocation(), 4, 4, 4)) {
                    if (!(e instanceof ItemDisplay) && !(e instanceof TextDisplay)) continue;
                    var to = e.getLocation().toVector().subtract(origin);
                    double dist = to.length();
                    if (dist > maxDist || dist < 0.2) continue;
                    to.normalize();
                    double dot = dir.dot(to);
                    if (dot < cosThreshold) continue;

                    if (e instanceof ItemDisplay) {
                        Main.getInstance().getDisplayItemService().reload();
                        Messages.send(player, "<green>Displays recarregados (alvo próximo: ItemDisplay)");
                    } else {
                        TextDisplay td2 = (TextDisplay) e;
                        if (Main.getInstance().getNPCService().isNameHologram(td2.getUniqueId())) {
                            Main.getInstance().getNPCService().reloadAll();
                            Messages.send(player, "<green>NPCs recarregados (alvo próximo: holograma de nome)");
                            return;
                        }
                        Main.getInstance().getHologramService().reload();
                        Messages.send(player, "<green>Hologramas recarregados (alvo próximo: TextDisplay)");
                    }
                    return;
                }

                try {
                    var npcSvc = Main.getInstance().getNPCService();
                    boolean npcInSight = false;
                    for (String idNpc : npcSvc.getAllNpcIds()) {
                        var data = npcSvc.getNpcById(idNpc);
                        if (data == null) continue;
                        var npcHead = data.location().clone().add(0, 1.6, 0).toVector();
                        var v = npcHead.clone().subtract(origin);
                        double t = dir.dot(v);
                        if (t < 0 || t > 8.0) continue;
                        var perp = v.clone().subtract(dir.clone().multiply(t));
                        double dLine = perp.length();
                        if (dLine <= 0.75) {
                            npcInSight = true;
                            break;
                        }
                    }
                    if (npcInSight) {
                        npcSvc.reloadAll();
                        Messages.send(player, "<green>NPCs recarregados (alvo: NPC em visão)");
                        return;
                    }
                } catch (Throwable ignored) {
                }

                try {
                    Main.getInstance().getDisplayItemService().reload();
                } catch (Throwable ignored) {
                }
                try {
                    Main.getInstance().getHologramService().reload();
                } catch (Throwable ignored) {
                }
                try {
                    Main.getInstance().getNPCService().reloadAll();
                } catch (Throwable ignored) {
                }
                Messages.send(player, "<yellow>Nenhuma entidade detectada com precisão. Recarreguei Displays, Hologramas e NPCs.");
            }
            return;
        }

        DisplayEntry.Type type = DisplayEntry.Type.fromString(typeArg);
        if (type == null) {
            Messages.send(player, "<red>Tipo desconhecido: " + typeArg + ". Use NPC/HOLOGRAM/DISPLAY_ITEM.");
            return;
        }

        if (type == DisplayEntry.Type.DISPLAY_ITEM) {
            if (args.length < 3) {
                Messages.send(player, "<red>Uso: /display DISPLAY_ITEM <id> <material> [texto ou line1|line2]");
                return;
            }
            String id = args[1];
            String material = args[2].toUpperCase();
            List<String> lines = null;
            if (args.length > 3) {
                String joined = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                if (joined.contains("|")) lines = Arrays.asList(joined.split("\\|"));
                else lines = List.of(joined);
            }
            try {
                DisplayConfigLoader d = new DisplayConfigLoader();
                d.load();
                if (d.getById(id) != null) {
                    Messages.send(player, "<red>Já existe um display item com o ID '" + id + "'.");
                    return;
                }
                ItemStack stack = new ItemStack(Material.valueOf(material));
                Main.getInstance().getDisplayItemService().show(
                        player,
                        player.getLocation(),
                        stack,
                        lines,
                        false,
                        Display.Billboard.VERTICAL,
                        3.0f,
                        id
                );
                Messages.send(player, "<green>DisplayItem '" + id + "' criado.");
            } catch (Exception e) {
                Messages.send(player, "<red>Falha ao criar DisplayItem: " + e.getMessage());
            }
            return;
        }

        if (type == DisplayEntry.Type.HOLOGRAM) {
            if (args.length < 3) {
                Messages.send(player, "<red>Uso: /display HOLOGRAM <id> <texto ou line1|line2>");
                return;
            }
            String id = args[1];
            String joined = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            List<String> lines = joined.contains("|") ? Arrays.asList(joined.split("\\|")) : List.of(joined);
            try {
                HologramConfigLoader h = new HologramConfigLoader();
                h.load();
                if (h.getById(id) != null) {
                    Messages.send(player, "<red>Já existe um holograma com o ID '" + id + "'.");
                    return;
                }
                Main.getInstance().getHologramService().showGlobal(id, player.getLocation(), lines, false);
                Messages.send(player, "<green>Holograma '" + id + "' criado.");
            } catch (Exception e) {
                Messages.send(player, "<red>Falha ao criar Holograma: " + e.getMessage());
            }
            return;
        }

        if (type == DisplayEntry.Type.NPC) {
            if (args.length < 2) {
                Messages.send(player, "<red>Uso: /display NPC <id> [skin_url|nick|player|self|auto]");
                return;
            }
            String id = args[1];
            String skin = args.length >= 3 ? args[2] : null;
            try {
                NPCService npcService = Main.getInstance().getNPCService();
                if (npcService.getNpcById(id) != null) {
                    Messages.send(player, "<red>Já existe um NPC com o ID '" + id + "'.");
                    return;
                }

                boolean useSelf = (skin == null) || skin.equalsIgnoreCase("self") || skin.equalsIgnoreCase("auto");
                if (useSelf) {
                    String texVal = null, texSig = null;
                    for (ProfileProperty prop : player.getPlayerProfile().getProperties()) {
                        if ("textures".equals(prop.getName())) {
                            texVal = prop.getValue();
                            texSig = prop.getSignature();
                            break;
                        }
                    }
                    if (texVal != null && texSig != null) {
                        npcService.spawnGlobal(id, player.getLocation(), null, "default");
                        npcService.updateNpcTextures(id, texVal, texSig);
                        Messages.send(player, "<green>NPC '" + id + "' criado com skin do executor.");
                    } else {
                        npcService.spawnGlobal(id, player.getLocation(), null, player.getName());
                        Messages.send(player, "<yellow>Não encontrei textures assinadas no seu perfil. Usei seu nick como skin.");
                    }
                } else {
                    npcService.spawnGlobal(id, player.getLocation(), null, skin);
                    Messages.send(player, "<green>NPC '" + id + "' criado (skin='" + skin + "').");
                }
            } catch (Exception e) {
                Messages.send(player, "<red>Erro ao criar NPC: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, "<#FFD700>--- Ajuda do Comando /display ---");
        Messages.send(sender, "<#781DFF>/display NPC <id> [skin_url|nick|player|self|auto] <#777777>- Cria um NPC no seu local.");
        Messages.send(sender, "<#781DFF>/display DISPLAY_ITEM <material> [text...] <#777777>- Cria um item display.");
        Messages.send(sender, "<#781DFF>/display HOLOGRAM <text or line1|line2|...> <#777777>- Cria um holograma.");
        Messages.send(sender, "<#781DFF>/display REMOVE [id] <#777777>- Remove por id ou pelo alvo que você está mirando.");
        Messages.send(sender, "<#781DFF>/display INFO [id] <#777777>- Mostra informações sobre a entidade alvo ou por id.");
        Messages.send(sender, "<#781DFF>/display reload [id] <#777777>- Recarrega a entidade do alvo ou por id (releitura do YML).");
        Messages.send(sender, "<gray>Types: DISPLAY_ITEM, HOLOGRAM, NPC (case-insensitive)");
    }

    private void sendEntryInfo(Player player, DisplayEntry e, String source) {
        Messages.send(player, "<gray>--- <yellow>Entry Info</yellow> ---");
        Messages.send(player, "<gray>source: <white>" + source);
        Messages.send(player, "<gray>id: <white>" + e.getId());
        Messages.send(player, "<gray>type: <white>" + e.getType());
        Messages.send(player, "<gray>world: <white>" + e.getWorld());
        Messages.send(player, String.format("<gray>pos: <white>(%.3f, %.3f, %.3f)</white> yaw=<white>%.2f</white> pitch=<white>%.2f</white>", e.getX(), e.getY(), e.getZ(), e.getYaw(), e.getPitch()));
        if (e.getItem() != null) Messages.send(player, "<gray>item/skin: <white>" + e.getItem());
        if (e.getBillboard() != null) Messages.send(player, "<gray>billboard: <white>" + e.getBillboard());
        if (e.getScale() != null) Messages.send(player, "<gray>scale: <white>" + e.getScale());
        if (e.getGlow() != null) Messages.send(player, "<gray>glow: <white>" + e.getGlow());
        List<String> lines = e.getLines();
        if (lines != null && !lines.isEmpty()) {
            Messages.send(player, "<gray>lines:");
            for (String l : lines) Messages.send(player, "  <white>• " + l);
        }
        List<String> actions = e.getActions();
        if (actions != null && !actions.isEmpty()) {
            Messages.send(player, "<gray>actions:");
            for (String a : actions) Messages.send(player, "  <white>• " + a);
        }
    }
}