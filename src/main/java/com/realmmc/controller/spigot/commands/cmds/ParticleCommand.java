package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.ParticleEntry;
import com.realmmc.controller.spigot.entities.particles.ParticleService;
import com.realmmc.controller.spigot.sounds.SoundKeys;
import com.realmmc.controller.spigot.sounds.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Cmd(cmd = "particle", aliases = {"particles"})
public class ParticleCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final ParticleService particleService;
    private final SoundService soundService;

    public ParticleCommand() {
        this.particleService = ServiceRegistry.getInstance().getService(ParticleService.class)
                .orElseThrow(() -> new IllegalStateException("ParticleService não foi encontrado!"));
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
            case "set":
                handleSet(sender, args);
                break;
            case "animate":
                handleAnimate(sender, args);
                break;
            case "stopanimation":
                handleStopAnimation(sender, args);
                break;
            case "testplayer":
                handleTestPlayer(sender, args);
                break;
            case "reload":
                particleService.reloadParticles();
                sender.sendMessage("§aTodos os efeitos de partículas foram recarregados.");
                playSound(sender, SoundKeys.SUCCESS);
                break;
            default:
                showHelp(sender);
                break;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(" ");
        sender.sendMessage("§6Comandos disponíveis para Partículas");
        sender.sendMessage("§e /particle criar <id> <tipo> [qtd] [intervalo] §8- §7Cria um novo efeito de partícula.");
        sender.sendMessage("§e /particle clone <id original> <novo id> §8- §7Duplica um efeito de partícula.");
        sender.sendMessage("§e /particle remover <id> §8- §7Remove um efeito permanentemente.");
        sender.sendMessage("§e /particle list §8- §7Lista todos os efeitos existentes.");
        sender.sendMessage("§e /particle info <id> §8- §7Mostra informações de um efeito.");
        sender.sendMessage("§e /particle tphere <id> §8- §7Teleporta um efeito para a sua localização.");
        sender.sendMessage("§e /particle set <id> <prop> <valor> §8- §7Modifica uma propriedade de um efeito.");
        sender.sendMessage("§e /particle animate <id> <tipo> [opções] §8- §7Aplica uma animação a um efeito.");
        sender.sendMessage("§e /particle stopanimation <id> §8- §7Para a animação e volta ao efeito estático.");
        sender.sendMessage("§e /particle testplayer <id> [jogador] §8- §7Mostra um efeito apenas para um jogador.");
        sender.sendMessage("§e /particle reload §8- §7Recarrega todos os efeitos do ficheiro.");
        sender.sendMessage(" ");
        sender.sendMessage("§6OBS.: §7As informações com <> são obrigatórios e [] são opcionais");
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
            sendUsage(sender, "/particle criar <id> <tipo> [quantidade] [intervalo_ticks]");
            return;
        }
        String id = args[1];
        if (particleService.getParticleEntry(id) != null) {
            sender.sendMessage("§cJá existe um efeito de partícula com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String particleType = args[2].toUpperCase();
        try {
            Particle.valueOf(particleType);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cO tipo de partícula '" + particleType + "' é inválido.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        int amount = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        int interval = args.length > 4 ? Integer.parseInt(args[4]) : 20;

        particleService.createParticle(id, player.getLocation(), particleType, amount, interval);
        sender.sendMessage("§aEfeito de partícula '" + id + "' criado com sucesso!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/particle remover <id>");
            return;
        }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum efeito com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        particleService.removeParticle(id);
        sender.sendMessage("§aEfeito de partícula '" + id + "' removido com sucesso!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleClone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por jogadores.");
            return;
        }
        if (args.length < 3) {
            sendUsage(sender, "/particle clone <id original> <novo id>");
            return;
        }
        String originalId = args[1];
        String newId = args[2];

        if (particleService.getParticleEntry(originalId) == null) {
            sender.sendMessage("§cO efeito original com ID '" + originalId + "' não foi encontrado.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (particleService.getParticleEntry(newId) != null) {
            sender.sendMessage("§cJá existe um efeito com o novo ID '" + newId + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        particleService.cloneParticle(originalId, newId, player.getLocation());
        sender.sendMessage("§aEfeito '" + originalId + "' clonado para '" + newId + "' na sua localização!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleList(CommandSender sender) {
        var ids = particleService.getAllParticleIds();
        if (ids.isEmpty()) {
            sender.sendMessage("§eNão há efeitos de partículas criados.");
            playSound(sender, SoundKeys.NOTIFICATION);
            return;
        }
        sender.sendMessage("§6Lista de efeitos de partículas: §7" + String.join(", ", ids));
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/particle info <id>");
            return;
        }
        String id = args[1];
        ParticleEntry entry = particleService.getParticleEntry(id);
        if (entry == null) {
            sender.sendMessage("§cNão foi encontrado nenhum efeito com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        sender.sendMessage(" ");
        sender.sendMessage("§6Informações sobre o Efeito §e" + id);
        sender.sendMessage("§f ID: §7" + entry.getId());
        sender.sendMessage("§f Tipo: §7" + entry.getParticleType());
        sender.sendMessage("§f Animação: §7" + (entry.getAnimationType() != null ? entry.getAnimationType() : "Nenhuma"));
        sender.sendMessage("§f Quantidade: §7" + entry.getAmount());
        sender.sendMessage("§f Intervalo: §7" + entry.getUpdateInterval() + " ticks");
        sender.sendMessage("§f Velocidade: §7" + entry.getSpeed());
        sender.sendMessage("§f Offset: §7" + String.format("X:%.2f, Y:%.2f, Z:%.2f", entry.getOffsetX(), entry.getOffsetY(), entry.getOffsetZ()));
        sender.sendMessage("§f Dados: §7" + (entry.getParticleData() != null ? entry.getParticleData() : "Nenhum"));
        sender.sendMessage("§f Longa Distância: " + (entry.isLongDistance() ? "§aAtivado" : "§cDesativado"));
        sender.sendMessage(" ");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando só pode ser executado por jogadores.");
            return;
        }
        if (args.length < 2) {
            sendUsage(sender, "/particle tphere <id>");
            return;
        }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum efeito com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        particleService.teleportParticle(id, player.getLocation());
        sender.sendMessage("§aEfeito '" + id + "' teleportado para a sua localização!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sendUsage(sender, "/particle set <id> <propriedade> <valor>");
            return;
        }
        String id = args[1];
        ParticleEntry entry = particleService.getParticleEntry(id);
        if (entry == null) {
            sender.sendMessage("§cNão foi encontrado nenhum efeito com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String prop = args[2].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        value = value.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }

        try {
            switch (prop) {
                case "tipo":
                    entry.setParticleType(Particle.valueOf(value.toUpperCase()).name());
                    break;
                case "quantidade":
                    entry.setAmount(Integer.parseInt(value));
                    break;
                case "intervalo":
                    entry.setUpdateInterval(Integer.parseInt(value));
                    break;
                case "velocidade":
                    entry.setSpeed(Double.parseDouble(value));
                    break;
                case "offset":
                    String[] parts = value.split(" ");
                    if (parts.length != 3) {
                        sendUsage(sender, "/particle set " + id + " offset <x> <y> <z>");
                        return;
                    }
                    entry.setOffsetX(Double.parseDouble(parts[0]));
                    entry.setOffsetY(Double.parseDouble(parts[1]));
                    entry.setOffsetZ(Double.parseDouble(parts[2]));
                    break;
                case "dados":
                    entry.setParticleData(value.equalsIgnoreCase("nenhum") ? null : value);
                    break;
                case "distancia":
                    entry.setLongDistance(Boolean.parseBoolean(value));
                    break;
                default:
                    sender.sendMessage("§cPropriedade inválida. Use: tipo, quantidade, intervalo, velocidade, offset, dados, distancia.");
                    playSound(sender, SoundKeys.USAGE_ERROR);
                    return;
            }
        } catch (Exception e) {
            sender.sendMessage("§cValor inválido para a propriedade '" + prop + "'. Verifique o formato.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        particleService.updateParticle(entry);
        sender.sendMessage("§aPropriedade '" + prop + "' do efeito '" + id + "' atualizada!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleAnimate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/particle animate <id> <tipo> [opções...]");
            return;
        }
        String id = args[1];
        ParticleEntry entry = particleService.getParticleEntry(id);
        if (entry == null) {
            sender.sendMessage("§cNão foi encontrado nenhum efeito com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String type = args[2].toLowerCase();
        entry.setAnimationType(type);

        Map<String, String> props = new HashMap<>();
        if (args.length > 3) {
            for (int i = 3; i < args.length; i++) {
                String[] parts = args[i].split(":", 2);
                if (parts.length == 2) {
                    props.put(parts[0].toLowerCase(), parts[1]);
                }
            }
        }
        entry.setAnimationProperties(props);

        particleService.updateParticle(entry);
        sender.sendMessage("§aAnimação '" + type + "' aplicada ao efeito '" + id + "'!");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleStopAnimation(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/particle stopanimation <id>");
            return;
        }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum efeito com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        particleService.stopAnimation(id);
        sender.sendMessage("§aAnimação do efeito '" + id + "' parada. O efeito voltou a ser estático.");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleTestPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/particle testplayer <id> [jogador]");
            return;
        }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) {
            sender.sendMessage("§cNão foi encontrado nenhum efeito com o ID '" + id + "'.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        Player target = null;
        if (args.length > 2) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("§cO jogador '" + args[2] + "' não está online.");
                playSound(sender, SoundKeys.USAGE_ERROR);
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        }

        if (target == null) {
            sender.sendMessage("§cPrecisas de especificar um jogador ou executar o comando em jogo.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (particleService.spawnForPlayerOnce(target, id)) {
            sender.sendMessage("§aEfeito '" + id + "' enviado para " + target.getName() + ".");
            playSound(sender, SoundKeys.SUCCESS);
        } else {
            sender.sendMessage("§cOcorreu um erro ao enviar o efeito.");
            playSound(sender, SoundKeys.USAGE_ERROR);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) {
            return Collections.emptyList();
        }

        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("criar", "remover", "clone", "list", "info", "tphere", "set", "animate", "stopanimation", "testplayer", "reload", "help"), completions);
        } else if (args.length == 2) {
            StringUtil.copyPartialMatches(args[1], particleService.getAllParticleIds(), completions);
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("criar")) {
                List<String> particleTypes = Stream.of(Particle.values()).map(Enum::name).collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[2], particleTypes, completions);
            } else if (sub.equals("set")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("tipo", "quantidade", "intervalo", "velocidade", "offset", "dados", "distancia"), completions);
            } else if (sub.equals("testplayer")) {
                List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[2], playerNames, completions);
            } else if (sub.equals("animate")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("circle", "helix", "sphere"), completions);
            }
        } else if (args.length >= 4) {
            String sub = args[0].toLowerCase();
            if (sub.equals("set")) {
                if (args[2].equalsIgnoreCase("tipo")) {
                    List<String> particleTypes = Stream.of(Particle.values()).map(Enum::name).collect(Collectors.toList());
                    StringUtil.copyPartialMatches(args[3], particleTypes, completions);
                } else if (args[2].equalsIgnoreCase("distancia")) {
                    StringUtil.copyPartialMatches(args[3], Arrays.asList("true", "false"), completions);
                }
            } else if (sub.equals("animate")) {
                String animType = args[2].toLowerCase();
                String currentArg = args[args.length - 1];
                List<String> options = new ArrayList<>();
                if (animType.equals("circle")) {
                    options.addAll(Arrays.asList("radius:", "speed:"));
                } else if (animType.equals("helix")) {
                    options.addAll(Arrays.asList("radius:", "height:", "speed:"));
                } else if (animType.equals("sphere")) {
                    options.addAll(Arrays.asList("radius:", "density:"));
                }
                StringUtil.copyPartialMatches(currentArg, options, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}