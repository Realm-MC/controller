package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.DisplayConfigLoader;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.config.HologramConfigLoader;
import com.realmmc.controller.spigot.entities.config.NPCConfigLoader;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.entities.holograms.HologramService;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Cmd(cmd = "display", aliases = {})
public class DisplayCommand implements CommandInterface {

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
                return;
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
                        return;
                    } else if (e instanceof TextDisplay td2) {
                        if (Main.getInstance().getNPCService().isNameHologram(td2.getUniqueId())) {
                            Main.getInstance().getNPCService().reloadAll();
                            Messages.send(player, "<green>NPCs recarregados (alvo próximo: holograma de nome)");
                            return;
                        }
                        Main.getInstance().getHologramService().reload();
                        Messages.send(player, "<green>Hologramas recarregados (alvo próximo: TextDisplay)");
                        return;
                    }
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
                return;
            }
        }
        DisplayEntry.Type type = DisplayEntry.Type.fromString(typeArg);
        if (type == null) {
            Messages.send(player, "<red>Tipo desconhecido: " + typeArg + ". Use NPC/HOLOGRAM/DISPLAY_ITEM.");
            return;
        }

        if (type == DisplayEntry.Type.NPC) {
            if (args.length < 4) {
                Messages.send(player, "<red>Uso: /display NPC <id> <skin_url|nick|player> <name>");
                return;
            }
            String id = args[1];
            String skin = args[2];
            String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            try {
                NPCService npcService = Main.getInstance().getNPCService();
                if (npcService.getNpcById(id) != null) {
                    Messages.send(player, "<red>Já existe um NPC com o ID '" + id + "'.");
                    return;
                }
                npcService.spawnGlobal(id, player.getLocation(), name, skin);
                Messages.send(player, "<green>NPC '" + id + "' criado com sucesso!");
            } catch (Exception e) {
                Messages.send(player, "<red>Erro ao criar NPC: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        if (type == DisplayEntry.Type.DISPLAY_ITEM) {
            if (args.length < 2) {
                Messages.send(player, "<red>Uso: /display DISPLAY_ITEM <material> [text...]");
                return;
            }
            String matArg = args[1];
            String text = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
            try {
                Material mat = Material.valueOf(matArg.toUpperCase());
                ItemStack item = new ItemStack(mat);
                List<String> lines = text == null || text.isBlank() ? Collections.emptyList() : List.of(text);
                DisplayItemService svc = Main.getInstance().getDisplayItemService();
                String genId = "disp_" + System.currentTimeMillis();
                svc.show(player, player.getLocation(), item, lines, false, Display.Billboard.CENTER, 3.0f, genId);
                Messages.send(player, "<green>Display Item criado com sucesso!");
            } catch (IllegalArgumentException ex) {
                Messages.send(player, "<red>Material inválido: " + matArg);
            } catch (Exception e) {
                Messages.send(player, "<red>Erro ao criar Display Item: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        if (type == DisplayEntry.Type.HOLOGRAM) {
            if (args.length < 2) {
                Messages.send(player, "<red>Uso: /display HOLOGRAM <text or line1|line2|...>");
                return;
            }
            String joined = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            List<String> lines;
            if (joined.contains("|")) {
                lines = new java.util.ArrayList<>();
                for (String part : joined.split("\\|")) {
                    String s = part.trim();
                    if (!s.isEmpty()) lines.add(s);
                }
            } else {
                lines = java.util.List.of(joined);
            }
            try {
                HologramService svc = Main.getInstance().getHologramService();
                svc.showGlobal(player.getLocation(), lines, false);
                Messages.send(player, "<green>Holograma criado com sucesso!");
            } catch (Exception e) {
                Messages.send(player, "<red>Erro ao criar Holograma: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        Messages.send(player, "<red>Tipo '" + type.name() + "' ainda não implementado aqui.");
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, "<#FFD700>--- Ajuda do Comando /display ---");
        Messages.send(sender, "<#FFFF00>/display NPC <id> <skin> <name> <#777777>- Cria um NPC no seu local.");
        Messages.send(sender, "<#FFFF00>/display DISPLAY_ITEM <material> [text...] <#777777>- Cria um item display.");
        Messages.send(sender, "<#FFFF00>/display HOLOGRAM <text or line1|line2|...> <#777777>- Cria um holograma.");
        Messages.send(sender, "<#FFFF00>/display reload [id] <#777777>- Recarrega a entidade do alvo ou por id (releitura do YML).");
        Messages.send(sender, "<gray>Types: DISPLAY_ITEM, HOLOGRAM, NPC (case-insensitive)");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("controller.manager")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("NPC", "HOLOGRAM", "DISPLAY_ITEM"), new ArrayList<>());
        }

        return Collections.emptyList();
    }
}
