package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileResolver;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.utils.NicknameFormatter;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.ParticleEntry;
import com.realmmc.controller.spigot.entities.particles.ParticleService;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin; // Import Plugin
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Cmd(cmd = "particle", aliases = {"particles"})
public class ParticleCommand implements CommandInterface {

    private final String permission = "controller.manager";
    // <<< CORREÇÃO: Nome do grupo associado à permissão >>>
    private final String requiredGroupName = "Gerente"; // Ou o nome de display correto do grupo Manager
    private final ParticleService particleService;

    public ParticleCommand() {
        this.particleService = ServiceRegistry.getInstance().getService(ParticleService.class)
                .orElseThrow(() -> new IllegalStateException("ParticleService não foi encontrado!"));
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            // <<< CORREÇÃO: Usar COMMON_NO_PERMISSION_GROUP >>>
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", requiredGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
            // <<< FIM CORREÇÃO >>>
        }

        if (args.length == 0 || (args.length > 0 && args[0].equalsIgnoreCase("help"))) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "criar": handleCreate(sender, args); break;
            case "remover": handleRemove(sender, args); break;
            case "clone": handleClone(sender, args); break;
            case "list": handleList(sender); break;
            case "info": handleInfo(sender, args); break;
            case "tphere": handleTpHere(sender, args); break;
            case "set": handleSet(sender, args); break;
            case "animate": handleAnimate(sender, args); break;
            case "stopanimation": handleStopAnimation(sender, args); break;
            case "testplayer": handleTestPlayer(sender, args); break;
            case "reload":
                particleService.reloadParticles();
                Messages.send(sender, MessageKey.PARTICLE_RELOADED);
                playSound(sender, SoundKeys.SUCCESS);
                break;
            default:
                showHelp(sender);
                playSound(sender, SoundKeys.USAGE_ERROR);
                break;
        }
    }

    // --- Métodos de Ajuda e Utilitários ---

    private void showHelp(CommandSender sender) {
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Partículas"));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle criar <id> <tipo> [qtd] [intervalo]").with("description", "Cria um novo efeito de partícula."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle clone <id original> <novo id>").with("description", "Duplica um efeito de partícula."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle remover <id>").with("description", "Remove um efeito permanentemente."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle list").with("description", "Lista todos os efeitos existentes."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle info <id>").with("description", "Mostra informações de um efeito."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle tphere <id>").with("description", "Teleporta um efeito para a sua localização."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle set <id> <prop> <valor>").with("description", "Modifica uma propriedade de um efeito."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle animate <id> <tipo> [opções]").with("description", "Aplica uma animação a um efeito."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle stopanimation <id>").with("description", "Para a animação e volta ao efeito estático."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle testplayer <id> [jogador]").with("description", "Mostra um efeito apenas para um jogador."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/particle reload").with("description", "Recarrega todos os efeitos do ficheiro."));
        Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_FULL);
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void sendUsage(CommandSender sender, String usage) {
        Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", usage));
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void playSound(CommandSender sender, String key) {
        if (sender instanceof Player player) {
            Optional<SoundPlayer> soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
            soundPlayerOpt.ifPresent(sp -> sp.playSound(player, key));
        }
    }

    // --- Handlers dos Subcomandos ---

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 3) { sendUsage(sender, "/particle criar <id> <tipo> [quantidade] [intervalo_ticks]"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) != null) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_ID).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        String particleType = args[2].toUpperCase();
        try { Particle.valueOf(particleType); }
        catch (IllegalArgumentException e) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_TYPE).with("type", particleType));
            playSound(sender, SoundKeys.ERROR); return;
        }
        int amount = 1; int interval = 20;
        try { if (args.length > 3) amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) { /* Usa default */ }
        try { if (args.length > 4) interval = Integer.parseInt(args[4]); } catch (NumberFormatException e) { /* Usa default */ }
        amount = Math.max(1, amount);
        interval = Math.max(1, interval);

        particleService.createParticle(id, player.getLocation(), particleType, amount, interval);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_CREATED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/particle remover <id>"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        particleService.removeParticle(id);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_REMOVED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleClone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 3) { sendUsage(sender, "/particle clone <id original> <novo id>"); return; }
        String originalId = args[1];
        String newId = args[2];
        if (particleService.getParticleEntry(originalId) == null) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", originalId));
            playSound(sender, SoundKeys.ERROR); return;
        }
        if (particleService.getParticleEntry(newId) != null) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_ID).with("id", newId));
            playSound(sender, SoundKeys.ERROR); return;
        }
        particleService.cloneParticle(originalId, newId, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.PARTICLE_CLONED).with("originalId", originalId).with("newId", newId));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleList(CommandSender sender) {
        var ids = particleService.getAllParticleIds();
        if (ids.isEmpty()) {
            Messages.send(sender, MessageKey.PARTICLE_LIST_EMPTY);
            playSound(sender, SoundKeys.NOTIFICATION); return;
        }
        Messages.send(sender, Message.of(MessageKey.PARTICLE_LIST_HEADER).with("ids", String.join("<gray>,<reset> ", ids)));
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/particle info <id>"); return; }
        String id = args[1];
        ParticleEntry entry = particleService.getParticleEntry(id);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        String longDist = Messages.translate(entry.isLongDistance() ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        String animation = entry.getAnimationType() != null ? entry.getAnimationType() : Messages.translate(MessageKey.PARTICLE_INFO_ANIM_NONE);
        String particleData = entry.getParticleData() != null ? entry.getParticleData() : Messages.translate(MessageKey.PARTICLE_INFO_DATA_NONE);

        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "Efeito de Partícula '" + id + "'"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.PARTICLE_INFO_TYPE)).with("value", entry.getParticleType()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.PARTICLE_INFO_ANIMATION)).with("value", animation));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.PARTICLE_INFO_AMOUNT)).with("value", entry.getAmount()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.PARTICLE_INFO_INTERVAL)).with("value", entry.getUpdateInterval() + " ticks"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.PARTICLE_INFO_SPEED)).with("value", String.format("%.3f", entry.getSpeed())));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.PARTICLE_INFO_OFFSET)).with("value", String.format("X:%.2f, Y:%.2f, Z:%.2f", entry.getOffsetX(), entry.getOffsetY(), entry.getOffsetZ())));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.PARTICLE_INFO_DATA)).with("value", particleData));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.PARTICLE_INFO_LONGDISTANCE)).with("value", longDist));
        Messages.send(sender, "<white>");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 2) { sendUsage(sender, "/particle tphere <id>"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        particleService.teleportParticle(id, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.PARTICLE_TELEPORTED).with("id", id));
        playSound(sender, SoundKeys.TELEPORT_WHOOSH);
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) { sendUsage(sender, "/particle set <id> <propriedade> <valor>"); return; }
        String id = args[1];
        ParticleEntry entry = particleService.getParticleEntry(id);
        if (entry == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
        String prop = args[2].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        value = value.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) { value = value.substring(1, value.length() - 1); }

        boolean success = true;
        try {
            switch (prop) {
                case "tipo":
                    Particle p = Particle.valueOf(value.toUpperCase());
                    entry.setParticleType(p.name());
                    break;
                case "quantidade":
                    entry.setAmount(Math.max(0, Integer.parseInt(value)));
                    break;
                case "intervalo":
                    entry.setUpdateInterval(Math.max(1, Integer.parseInt(value)));
                    break;
                case "velocidade":
                    entry.setSpeed(Double.parseDouble(value));
                    break;
                case "offset":
                    String[] parts = value.split("[\\s,]+");
                    if (parts.length != 3) {
                        sendUsage(sender, "/particle set " + id + " offset <x> <y> <z>");
                        success = false;
                        break;
                    }
                    entry.setOffsetX(Double.parseDouble(parts[0]));
                    entry.setOffsetY(Double.parseDouble(parts[1]));
                    entry.setOffsetZ(Double.parseDouble(parts[2]));
                    break;
                case "dados":
                    entry.setParticleData(value.equalsIgnoreCase("nenhum") || value.equalsIgnoreCase("null") || value.isEmpty() ? null : value);
                    break;
                case "distancia":
                    entry.setLongDistance(value.equalsIgnoreCase("true") || value.equals("1"));
                    break;
                default:
                    Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_PROPERTY).with("properties", "tipo, quantidade, intervalo, velocidade, offset, dados, distancia"));
                    playSound(sender, SoundKeys.ERROR);
                    success = false;
                    break;
            }
        } catch (IllegalArgumentException e) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_VALUE).with("prop", prop));
            playSound(sender, SoundKeys.ERROR);
            success = false;
        } catch (Exception e) {
            Messages.send(sender, MessageKey.COMMAND_ERROR);
            sender.sendMessage("<red>Detalhe: " + e.getMessage());
            playSound(sender, SoundKeys.ERROR);
            success = false;
        }

        if (success) {
            particleService.updateParticle(entry);
            Messages.send(sender, Message.of(MessageKey.PARTICLE_PROPERTY_SET).with("prop", prop).with("id", id));
            playSound(sender, SoundKeys.SETTING_UPDATE);
        }
    }

    private void handleAnimate(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/particle animate <id> <tipo> [opções...]"); return; }
        String id = args[1];
        ParticleEntry entry = particleService.getParticleEntry(id);
        if (entry == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
        String type = args[2].toLowerCase();

        if (!Arrays.asList("circle", "helix", "sphere").contains(type)) {
            Messages.error(sender, "Tipo de animação inválido. Use: circle, helix, sphere.");
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        entry.setAnimationType(type);
        Map<String, String> props = new HashMap<>();
        if (args.length > 3) {
            for (int i = 3; i < args.length; i++) {
                String[] parts = args[i].split(":", 2);
                if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                    props.put(parts[0].toLowerCase().trim(), parts[1].trim());
                } else {
                    Messages.warning(sender, "Ignorando opção de animação mal formatada: " + args[i]);
                }
            }
        }
        entry.setAnimationProperties(props);

        particleService.updateParticle(entry);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_ANIMATION_SET).with("type", type).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleStopAnimation(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/particle stopanimation <id>"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        particleService.stopAnimation(id);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_ANIMATION_STOPPED).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleTestPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/particle testplayer <id> [jogador]"); return; }
        String particleId = args[1];

        ParticleEntry entry = particleService.getParticleEntry(particleId);
        if (entry == null) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", particleId));
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        String targetNameInput;
        Player senderPlayer = (sender instanceof Player) ? (Player) sender : null;

        if (args.length > 2) {
            targetNameInput = args[2];
        } else if (senderPlayer != null) {
            targetNameInput = senderPlayer.getName();
        } else {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            sendUsage(sender, "/particle testplayer <id> <jogador>");
            return;
        }

        final String finalTargetNameInput = targetNameInput;
        CompletableFuture.supplyAsync(() -> ProfileResolver.resolve(finalTargetNameInput), TaskScheduler.getAsyncExecutor())
                .thenAcceptAsync(targetProfileOpt -> {
                    if (targetProfileOpt.isEmpty()) {
                        Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED)
                                .with("player", finalTargetNameInput));
                        playSound(sender, SoundKeys.ERROR);
                        return;
                    }

                    Profile targetProfile = targetProfileOpt.get();
                    UUID targetUuid = targetProfile.getUuid();
                    Player targetOnline = Bukkit.getPlayer(targetUuid);

                    if (targetOnline != null && targetOnline.isOnline()) {
                        if (particleService.spawnForPlayerOnce(targetOnline, particleId)) {
                            String formattedNick = NicknameFormatter.getFullFormattedNick(targetUuid);
                            Messages.send(sender, Message.of(MessageKey.PARTICLE_TESTED)
                                    .with("id", particleId)
                                    .with("player", formattedNick));
                            playSound(sender, SoundKeys.SUCCESS);
                        } else {
                            Messages.send(sender, MessageKey.COMMAND_ERROR);
                            playSound(sender, SoundKeys.ERROR);
                        }
                    } else {
                        String formattedNick = NicknameFormatter.getFullFormattedNick(targetUuid);
                        Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NOT_ONLINE)
                                .with("player", formattedNick));
                        playSound(sender, SoundKeys.ERROR);
                    }

                }, runnable -> Bukkit.getScheduler().runTask(ServiceRegistry.getInstance().requireService(Plugin.class), runnable))
                .exceptionally(ex -> {
                    sender.getServer().getLogger().log(Level.SEVERE, "Erro ao resolver perfil ou enviar partícula para " + finalTargetNameInput, ex);
                    Messages.send(sender, MessageKey.COMMAND_ERROR);
                    playSound(sender, SoundKeys.ERROR);
                    return null;
                });
    }

    // --- Tab Completion ---

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) { return Collections.emptyList(); }
        final List<String> completions = new ArrayList<>();
        final String currentArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            StringUtil.copyPartialMatches(currentArg, Arrays.asList(
                    "criar", "remover", "clone", "list", "info", "tphere", "set", "animate",
                    "stopanimation", "testplayer", "reload", "help"), completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList("remover", "clone", "info", "tphere", "set", "animate",
                    "stopanimation", "testplayer").contains(sub)) {
                StringUtil.copyPartialMatches(currentArg, particleService.getAllParticleIds(), completions);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("criar")) {
                List<String> particleTypes = Stream.of(Particle.values())
                        .map(p -> p.name().toLowerCase())
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(currentArg, particleTypes, completions);
            } else if (sub.equals("set")) {
                StringUtil.copyPartialMatches(currentArg, Arrays.asList(
                        "tipo", "quantidade", "intervalo", "velocidade", "offset", "dados", "distancia"), completions);
            } else if (sub.equals("testplayer")) {
                List<String> suggestions = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                if ("id:".startsWith(currentArg)) suggestions.add("id:");
                if ("uuid:".startsWith(currentArg)) suggestions.add("uuid:");
                if (currentArg.startsWith("id:") && currentArg.length() > 3) suggestions.add(currentArg);
                if (currentArg.startsWith("uuid:") && currentArg.length() > 5) suggestions.add(currentArg);
                StringUtil.copyPartialMatches(currentArg, suggestions, completions);
            } else if (sub.equals("animate")) {
                StringUtil.copyPartialMatches(currentArg, Arrays.asList("circle", "helix", "sphere"), completions);
            }
        } else if (args.length == 4) {
            String sub = args[0].toLowerCase();
            if (sub.equals("set")) {
                String prop = args[2].toLowerCase();
                if (prop.equals("tipo")) {
                    List<String> pTypes = Stream.of(Particle.values()).map(p -> p.name().toLowerCase()).collect(Collectors.toList());
                    StringUtil.copyPartialMatches(currentArg, pTypes, completions);
                } else if (prop.equals("distancia")) {
                    StringUtil.copyPartialMatches(currentArg, Arrays.asList("true", "false"), completions);
                } else if (prop.equals("dados")) {
                    completions.add("nenhum");
                }
            } else if (sub.equals("animate")) {
                String animType = args[2].toLowerCase();
                List<String> options = new ArrayList<>();
                if (animType.equals("circle")) options.addAll(Arrays.asList("radius:", "speed:"));
                else if (animType.equals("helix")) options.addAll(Arrays.asList("radius:", "height:", "speed:"));
                else if (animType.equals("sphere")) options.addAll(Arrays.asList("radius:", "density:"));
                StringUtil.copyPartialMatches(currentArg, options, completions);
            }
        } else if (args.length > 4 && args[0].equalsIgnoreCase("animate")) {
            String animType = args[2].toLowerCase();
            List<String> options = new ArrayList<>();
            if (animType.equals("circle")) options.addAll(Arrays.asList("radius:", "speed:"));
            else if (animType.equals("helix")) options.addAll(Arrays.asList("radius:", "height:", "speed:"));
            else if (animType.equals("sphere")) options.addAll(Arrays.asList("radius:", "density:"));
            Set<String> usedOptions = Arrays.stream(args, 3, args.length -1).map(s -> s.split(":")[0].toLowerCase() + ":").collect(Collectors.toSet());
            options.removeIf(usedOptions::contains);
            StringUtil.copyPartialMatches(currentArg, options, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}