package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.displayitems.DisplayItemService;
import com.realmmc.controller.spigot.sounds.SoundKeys;
import com.realmmc.controller.spigot.sounds.SoundService;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
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
    private final SoundService soundService;

    public DisplayCommand() {
        this.displayService = Main.getInstance().getDisplayItemService();
        this.soundService = ServiceRegistry.getInstance().getService(SoundService.class)
                .orElseThrow(() -> new IllegalStateException("SoundService não foi encontrado!"));
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("§cApenas o grupo Gerente ou superior pode executar este comando.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "help":
                showHelp(sender);
                break;
            case "criar":
                handleCreate(sender, args);
                break;
            case "remover":
                handleRemove(sender, args);
                break;
            case "clone":
                handleClone(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "info":
                handleInfo(sender, args);
                break;
            case "tphere":
                handleTpHere(sender, args);
                break;
            case "setitem":
                handleSetItem(sender, args);
                break;
            case "setscale":
                handleSetScale(sender, args);
                break;
            case "setbillboard":
                handleSetBillboard(sender, args);
                break;
            case "toggleglow":
                handleToggleGlow(sender, args);
                break;
            case "togglelines":
                handleToggleLines(sender, args);
                break;
            case "addline":
                handleAddLine(sender, args);
                break;
            case "setline":
                handleSetLine(sender, args);
                break;
            case "removeline":
                handleRemoveLine(sender, args);
                break;
            case "addaction":
                handleAddAction(sender, args);
                break;
            case "removeaction":
                handleRemoveAction(sender, args);
                break;
            case "listactions":
                handleListActions(sender, args);
                break;
            case "reload":
                displayService.reload();
                sender.sendMessage("§aTodos os Display Items foram recarregados a partir do ficheiro de configuração.");
                playSound(sender, SoundKeys.SUCCESS);
                break;
            default:
                showHelp(sender);
                break;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(" ");
        sender.sendMessage("§6Comandos disponíveis para Display Items");
        sender.sendMessage("§e /display criar <id> <material> §8- §7Cria um novo item flutuante.");
        sender.sendMessage("§e /display clone <id original> <novo id> §8- §7Duplica um item flutuante.");
        sender.sendMessage("§e /display remover <id> §8- §7Remove um item flutuante.");
        sender.sendMessage("§e /display list §8- §7Lista todos os itens flutuantes.");
        sender.sendMessage("§e /display info <id> §8- §7Mostra informações de um item.");
        sender.sendMessage("§e /display tphere <id> §8- §7Teleporta um item para a sua localização.");
        sender.sendMessage("§e /display setitem <id> <material> §8- §7Altera o item exibido.");
        sender.sendMessage("§e /display setscale <id> <escala> §8- §7Altera o tamanho do item (ex: 1.5).");
        sender.sendMessage("§e /display setbillboard <id> <tipo> §8- §7Altera como o item encara o jogador.");
        sender.sendMessage("§e /display toggleglow <id> §8- §7Ativa/desativa o brilho do item.");
        sender.sendMessage("§e /display togglelines <id> §8- §7Mostra/esconde as linhas de texto.");
        sender.sendMessage("§e /display addline <id> <texto> §8- §7Adiciona uma linha de texto.");
        sender.sendMessage("§e /display setline <id> <linha> <texto> §8- §7Modifica uma linha de texto.");
        sender.sendMessage("§e /display removeline <id> <linha> §8- §7Remove uma linha de texto.");
        sender.sendMessage("§e /display addaction <id> <ação> §8- §7Adiciona uma ação de clique.");
        sender.sendMessage("§e /display removeaction <id> <linha> §8- §7Remove uma ação de clique.");
        sender.sendMessage("§e /display listactions <id> §8- §7Lista as ações de um item.");
        sender.sendMessage("§e /display reload §8- §7Recarrega todos os Display Items do ficheiro.");
        sender.sendMessage(" ");
        sender.sendMessage("§6OBS.: §7As informações com <> são obrigatórios");
        sender.sendMessage(" ");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void sendUsage(CommandSender sender, String usage) {
        sender.sendMessage("§cUtilize: " + usage);
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void playSound(CommandSender sender, String key) {
        if (sender instanceof Player) {
            soundService.playSound((Player) sender, key);
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por jogadores.");
            return;
        }
        if (args.length < 3) {
            sendUsage(sender, "/display criar <id> <material>");
            return;
        }
        String id = args[1];
        if (displayService.getDisplayEntry(id) != null) {
            sender.sendMessage("§cJá existe um Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        Material material;
        try {
            material = Material.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cO material '" + args[2] + "' é inválido.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        displayService.createDisplay(id, player.getLocation(), material);
        sender.sendMessage("§aDisplay Item '" + id + "' criado com sucesso na sua localização!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleClone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por jogadores.");
            return;
        }
        if (args.length < 3) {
            sendUsage(sender, "/display clone <id original> <novo id>");
            return;
        }
        String originalId = args[1];
        String newId = args[2];

        if (displayService.getDisplayEntry(originalId) == null) {
            sender.sendMessage("§cO Display original com ID '" + originalId + "' não foi encontrado.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (displayService.getDisplayEntry(newId) != null) {
            sender.sendMessage("§cJá existe um Display com o novo ID '" + newId + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        displayService.cloneDisplay(originalId, newId, player.getLocation());
        sender.sendMessage("§aDisplay '" + originalId + "' clonado para '" + newId + "' na sua localização!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/display remover <id>");
            return;
        }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        displayService.removeDisplay(id);
        sender.sendMessage("§aDisplay Item '" + id + "' removido com sucesso!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleList(CommandSender sender) {
        var ids = displayService.getAllDisplayIds();
        if (ids.isEmpty()) {
            sender.sendMessage("§eNão há Display Items criados no momento.");
            playSound(sender, SoundKeys.NOTIFICATION);
            return;
        }
        sender.sendMessage("§6Lista de Display Items existentes: §7" + String.join(", ", ids));
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/display info <id>");
            return;
        }
        String id = args[1];
        DisplayEntry entry = displayService.getDisplayEntry(id);
        if (entry == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String locationStr = String.format("%.2f, %.2f, %.2f em %s", entry.getX(), entry.getY(), entry.getZ(), entry.getWorld());
        String glow = Boolean.TRUE.equals(entry.getGlow()) ? "§aAtivado" : "§cDesativado";
        String linesVisible = Boolean.TRUE.equals(entry.getHologramVisible()) ? "§aVisíveis" : "§cOcultas";

        sender.sendMessage(" ");
        sender.sendMessage("§6Informações sobre o Display Item §e" + id);
        sender.sendMessage("§f ID: §7" + entry.getId());
        sender.sendMessage("§f Item: §7" + entry.getItem());
        sender.sendMessage("§f Localização: §7" + locationStr);
        sender.sendMessage("§f Escala: §7" + entry.getScale());
        sender.sendMessage("§f Billboard: §7" + entry.getBillboard());
        sender.sendMessage("§f Brilho (Glow): " + glow);
        sender.sendMessage("§f Visibilidade das Linhas: " + linesVisible);
        sender.sendMessage("§f Linhas de Texto (" + entry.getLines().size() + "):");
        for (int i = 0; i < entry.getLines().size(); i++) {
            sender.sendMessage("  §e" + (i + 1) + ": §r" + entry.getLines().get(i));
        }
        sender.sendMessage(" ");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por jogadores.");
            return;
        }
        if (args.length < 2) {
            sendUsage(sender, "/display tphere <id>");
            return;
        }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        displayService.teleportDisplay(id, player.getLocation());
        sender.sendMessage("§aDisplay Item '" + id + "' teleportado para a sua localização!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/display setitem <id> <material>");
            return;
        }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        Material material;
        try {
            material = Material.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cO material '" + args[2] + "' é inválido.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        displayService.setItem(id, material);
        sender.sendMessage("§aItem do Display '" + id + "' atualizado para " + material.name() + "!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetScale(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/display setscale <id> <escala>");
            return;
        }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        float scale;
        try {
            scale = Float.parseFloat(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cA escala '" + args[2] + "' deve ser um número (ex: 1.0, 2.5).");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        displayService.setScale(id, scale);
        sender.sendMessage("§aEscala do Display '" + id + "' atualizada para " + scale + "!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetBillboard(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/display setbillboard <id> <tipo>");
            return;
        }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        Display.Billboard billboard;
        try {
            billboard = Display.Billboard.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cO tipo de billboard '" + args[2] + "' é inválido. Use CENTER, FIXED, VERTICAL, ou HORIZONTAL.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        displayService.setBillboard(id, billboard.name());
        sender.sendMessage("§aBillboard do Display '" + id + "' atualizado para " + billboard.name() + "!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleToggleGlow(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/display toggleglow <id>");
            return;
        }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        boolean newState = displayService.toggleGlow(id);
        sender.sendMessage("§aO brilho (glow) do Display '" + id + "' foi " + (newState ? "§aativado§a." : "§cdesativado§a."));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleToggleLines(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/display togglelines <id>");
            return;
        }
        String id = args[1];
        if (displayService.getDisplayEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        boolean newState = displayService.toggleLinesVisibility(id);
        sender.sendMessage("§aA visibilidade das linhas do Display '" + id + "' foi " + (newState ? "§aativada§a." : "§cdesativada§a."));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleAddLine(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/display addline <id> <texto>");
            return;
        }
        String id = args[1];
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (displayService.getDisplayEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        displayService.addLine(id, text);
        sender.sendMessage("§aLinha adicionada ao Display '" + id + "'!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSetLine(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendUsage(sender, "/display setline <id> <linha> <texto>");
            return;
        }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cO número da linha deve ser um número válido.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!displayService.setLine(id, line, text)) {
            sender.sendMessage("§cNão foi encontrado um Display com o ID '" + id + "' ou a linha " + line + " é inválida.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        sender.sendMessage("§aLinha " + line + " do Display '" + id + "' atualizada!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemoveLine(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/display removeline <id> <linha>");
            return;
        }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cO número da linha deve ser um número válido.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (!displayService.removeLine(id, line)) {
            sender.sendMessage("§cNão foi encontrado um Display com o ID '" + id + "' ou a linha " + line + " é inválida.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        sender.sendMessage("§aLinha " + line + " do Display '" + id + "' removida!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleAddAction(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/display addaction <id> <ação>");
            return;
        }
        String id = args[1];
        String action = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (displayService.getDisplayEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        displayService.addAction(id, action);
        sender.sendMessage("§aAção adicionada ao Display '" + id + "'!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemoveAction(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/display removeaction <id> <linha>");
            return;
        }
        String id = args[1];
        int line;
        try {
            line = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cO número da linha da ação deve ser um número válido.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (!displayService.removeAction(id, line)) {
            sender.sendMessage("§cNão foi encontrado um Display com o ID '" + id + "' ou a ação " + line + " é inválida.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        sender.sendMessage("§aAção " + line + " do Display '" + id + "' removida!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleListActions(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/display listactions <id>");
            return;
        }
        String id = args[1];
        DisplayEntry entry = displayService.getDisplayEntry(id);
        if (entry == null) {
            sender.sendMessage("§cNão foi encontrado nenhum Display com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        List<String> actions = entry.getActions();
        if (actions == null || actions.isEmpty()) {
            sender.sendMessage("§eO Display '" + id + "' não possui ações de clique.");
            playSound(sender, SoundKeys.NOTIFICATION);
            return;
        }

        sender.sendMessage("§6Ações de clique para o Display §e" + id);
        for (int i = 0; i < actions.size(); i++) {
            sender.sendMessage("  §e" + (i + 1) + ": §7" + actions.get(i));
        }
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) {
            return Collections.emptyList();
        }

        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("criar", "clone", "remover", "list", "info", "tphere", "setitem", "setscale", "setbillboard", "toggleglow", "togglelines", "addline", "setline", "removeline", "addaction", "removeaction", "listactions", "reload", "help");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            List<String> displayIds = new ArrayList<>(displayService.getAllDisplayIds());
            switch(sub) {
                case "remover":
                case "clone":
                case "info":
                case "tphere":
                case "setitem":
                case "setscale":
                case "setbillboard":
                case "toggleglow":
                case "togglelines":
                case "addline":
                case "setline":
                case "removeline":
                case "addaction":
                case "removeaction":
                case "listactions":
                    StringUtil.copyPartialMatches(args[1], displayIds, completions);
                    break;
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("criar") || sub.equals("setitem")) {
                List<String> materials = Stream.of(Material.values())
                        .filter(Material::isItem)
                        .map(Enum::name)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[2], materials, completions);
            } else if (sub.equals("setbillboard")) {
                List<String> billboards = Stream.of(Display.Billboard.values())
                        .map(Enum::name)
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[2], billboards, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}