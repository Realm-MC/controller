package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import com.realmmc.controller.spigot.sounds.SoundKeys;
import com.realmmc.controller.spigot.sounds.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Cmd(cmd = "npc", aliases = {})
public class NpcCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final NPCService npcService;
    private final SoundService soundService;

    public NpcCommand() {
        this.npcService = Main.getInstance().getNpcService();
        this.soundService = ServiceRegistry.getInstance().getService(SoundService.class)
                .orElseThrow(() -> new IllegalStateException("SoundService não foi encontrado!"));
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            Messages.send(sender, "<red>Apenas o grupo Gerente ou superior pode executar este comando.");
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "criar":
                handleCriar(sender, args);
                break;
            case "skin":
                handleSkin(sender, args);
                break;
            default:
                showHelp(sender);
                break;
        }
    }

    private void handleCriar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Este comando só pode ser executado por um jogador.");
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        if (args.length < 4) {
            Messages.send(sender, "<red>Utilize: /npc criar <id> <skin> <nome>.");
            playSound(player, SoundKeys.ERROR);
            return;
        }

        String id = args[1];
        String skinSource = args[2];
        String displayName = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        if (npcService.getNpcById(id) != null) {
            Messages.send(player, "<red>Já existe um NPC com o ID '" + id + "'.");
            playSound(player, SoundKeys.ERROR);
            return;
        }

        npcService.spawnGlobal(id, player.getLocation(), displayName, skinSource);
        Messages.send(player, "<green>NPC '" + id + "' criado com sucesso na sua localização!");
        playSound(player, SoundKeys.SUCCESS);
    }

    private void handleSkin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, "<red>Utilize: /npc skin <id> <nova_skin>.");
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        String id = args[1];
        String newSkinSource = args[2];

        if (npcService.getNpcById(id) == null) {
            Messages.send(sender, "<red>Nenhum NPC encontrado com o ID '" + id + "'.");
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        npcService.updateNpcSkin(id, newSkinSource);
        Messages.send(sender, "<green>A skin do NPC '" + id + "' foi atualizada com sucesso!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, " ");
        Messages.send(sender, "<gold>Comandos disponíveis para NPCs");
        Messages.send(sender, "<yellow>/npc criar <id> <skin> <nome> <dark_gray>- &7Cria um NPC na sua localização.");
        Messages.send(sender, "<yellow>/npc skin <id> <nova_skin> <dark_gray>- &7Altera a skin de um NPC existente.");
        Messages.send(sender, " ");
        Messages.send(sender, "<gold>OBS.: &7As informações com <> são obrigatórios");
        Messages.send(sender, " ");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) {
            return Collections.emptyList();
        }

        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("criar", "skin");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("skin")) {
                StringUtil.copyPartialMatches(args[1], npcService.getAllNpcIds(), completions);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("criar") || args[0].equalsIgnoreCase("skin")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("player");
                Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
                StringUtil.copyPartialMatches(args[2], suggestions, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }

    private void playSound(CommandSender sender, String key) {
        if (sender instanceof Player) {
            soundService.playSound((Player) sender, key);
        }
    }
}