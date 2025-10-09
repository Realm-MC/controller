package com.realmmc.controller.spigot.commands.cmds;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.entities.holograms.HologramService;
import com.realmmc.controller.spigot.entities.npcs.NPCService;
import com.realmmc.controller.spigot.sounds.SoundKeys;
import com.realmmc.controller.spigot.sounds.SoundService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Cmd(cmd = "display", aliases = {"displays"})
public class DisplayCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final DisplayItemService displayService;
    private final HologramService hologramService;
    private final NPCService npcService;
    private final SoundService soundService;

    public DisplayCommand() {
        this.displayService = Main.getInstance().getDisplayItemService();
        this.hologramService = Main.getInstance().getHologramService();
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
        if (subCommand.equalsIgnoreCase("reload")) {
            displayService.reload();
            hologramService.reload();
            npcService.reloadAll();
            Messages.send(sender, "<green>Todos os displays, hologramas e NPCs foram recarregados a partir dos ficheiros de configuração.");
            playSound(sender, SoundKeys.SUCCESS);
            return;
        }

        if (!(sender instanceof Player player)) {
            Messages.send(sender, "<red>Este subcomando só pode ser executado por um jogador.");
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        switch (subCommand) {
            case "criar":
                handleCreate(player, args);
                break;
            default:
                showHelp(player);
                break;
        }
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            Messages.send(player, "<red>Utilize: /display criar <tipo> <id> [argumentos].");
            playSound(player, SoundKeys.ERROR);
            return;
        }

        String typeStr = args[1].toUpperCase();
        String id = args[2];

        try {
            DisplayEntry.Type type = DisplayEntry.Type.valueOf(typeStr);
            switch (type) {
                case NPC:
                    createNpc(player, id, args);
                    break;
                case HOLOGRAM:
                    createHologram(player, id, args);
                    break;
                case DISPLAY_ITEM:
                    createDisplayItem(player, id, args);
                    break;
            }
        } catch (IllegalArgumentException e) {
            Messages.send(player, "<red>Tipo de entidade inválido: " + typeStr + ". Use NPC, HOLOGRAM, ou DISPLAY_ITEM.");
            playSound(player, SoundKeys.ERROR);
        }
    }

    private void createNpc(Player player, String id, String[] args) {
        if (npcService.getNpcById(id) != null) {
            Messages.send(player, "<red>Já existe um NPC com o ID '" + id + "'.");
            playSound(player, SoundKeys.ERROR);
            return;
        }
        String skin = (args.length > 3) ? args[3] : "self";
        npcService.spawnGlobal(id, player.getLocation(), null, skin);
        Messages.send(player, "<green>NPC '" + id + "' criado com sucesso! Edite o ficheiro npcs.yml para configurar as linhas e ações.");
        playSound(player, SoundKeys.SUCCESS);
    }

    private void createHologram(Player player, String id, String[] args) {
        if (args.length < 4) {
            Messages.send(player, "<red>Utilize: /display criar HOLOGRAM <id> <texto>.");
            playSound(player, SoundKeys.ERROR);
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        List<String> lines = Arrays.asList(text.split("\\|"));

        hologramService.showGlobal(id, player.getLocation(), lines, false);
        Messages.send(player, "<green>Holograma '" + id + "' criado com sucesso! Edite o ficheiro holograms.yml para configurar as ações.");
        playSound(player, SoundKeys.SUCCESS);
    }

    private void createDisplayItem(Player player, String id, String[] args) {
        if (args.length < 4) {
            Messages.send(player, "<red>Utilize: /display criar DISPLAY_ITEM <id> <material>.");
            playSound(player, SoundKeys.ERROR);
            return;
        }
        String materialName = args[3].toUpperCase();
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            Messages.send(player, "<red>Material inválido: " + materialName);
            playSound(player, SoundKeys.ERROR);
            return;
        }
        displayService.show(player, player.getLocation(), new ItemStack(material), null, false, Display.Billboard.CENTER, 1.0f, id);
        Messages.send(player, "<green>Display Item '" + id + "' criado com sucesso! Edite o ficheiro displays.yml para configurar as linhas e ações.");
        playSound(player, SoundKeys.SUCCESS);
    }

    private void showHelp(CommandSender sender) {
        Messages.send(sender, " ");
        Messages.send(sender, "<gold>Comandos disponíveis para Displays");
        Messages.send(sender, "<yellow>/display criar <tipo> <id> [args] <dark_gray>- &7Cria uma nova entidade.");
        Messages.send(sender, "<yellow>/display remover [id] <dark_gray>- &7Remove uma entidade por ID ou ao olhar para ela.");
        Messages.send(sender, "<yellow>/display info [id] <dark_gray>- &7Vê informações de uma entidade por ID ou ao olhar.");
        Messages.send(sender, "<yellow>/display reload <dark_gray>- &7Recarrega todas as entidades a partir dos ficheiros.");
        Messages.send(sender, " ");
        Messages.send(sender, "<gold>OBS.: &7<tipo> pode ser NPC, HOLOGRAM, ou DISPLAY_ITEM.");
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
            List<String> subCommands = Arrays.asList("criar", "remover", "info", "reload");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("criar")) {
                List<String> types = Stream.of(DisplayEntry.Type.values()).map(Enum::name).collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[1], types, completions);
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("criar") && args[1].equalsIgnoreCase("DISPLAY_ITEM")) {
            List<String> materials = Stream.of(Material.values())
                    .filter(Material::isItem)
                    .map(Enum::name)
                    .collect(Collectors.toList());
            StringUtil.copyPartialMatches(args[3], materials, completions);
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