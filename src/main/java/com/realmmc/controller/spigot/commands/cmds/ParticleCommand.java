package com.realmmc.controller.spigot.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.auth.AuthenticationGuard;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileResolver;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.config.ParticleEntry;
import com.realmmc.controller.spigot.entities.particles.ParticleService;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Cmd(cmd = "particle", aliases = {"particles"})
public class ParticleCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final String requiredGroupName = "Gerente"; // Nome do grupo
    private final ParticleService particleService;
    private final Logger logger; // Logger padronizado

    public ParticleCommand() {
        this.particleService = ServiceRegistry.getInstance().getService(ParticleService.class)
                .orElseThrow(() -> new IllegalStateException("ParticleService não foi encontrado!"));
        // Obter logger da instância principal
        this.logger = Main.getInstance().getLogger();
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", requiredGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length == 0 || (args.length > 0 && args[0].equalsIgnoreCase("help"))) {
            showHelp(sender, label); // Passar label para showHelp
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "criar": handleCreate(sender, args, label); break;
            case "remover": handleRemove(sender, args, label); break;
            case "clone": handleClone(sender, args, label); break;
            case "list": handleList(sender); break;
            case "info": handleInfo(sender, args, label); break;
            case "tphere": handleTpHere(sender, args, label); break;
            case "set": handleSet(sender, args, label); break;
            case "animate": handleAnimate(sender, args, label); break;
            case "stopanimation": handleStopAnimation(sender, args, label); break;
            case "testplayer": handleTestPlayer(sender, args, label); break;
            case "reload":
                particleService.reloadParticles();
                Messages.send(sender, MessageKey.PARTICLE_RELOADED);
                playSound(sender, SoundKeys.SUCCESS);
                break;
            default:
                showHelp(sender, label); // Passar label
                playSound(sender, SoundKeys.USAGE_ERROR);
                break;
        }
    }

    // --- Métodos de Ajuda e Utilitários ---

    private void showHelp(CommandSender sender, String label) {
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Partículas"));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " criar <id> <tipo> [qtd] [intervalo]").with("description", "Cria um novo efeito de partícula."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " clone <id original> <novo id>").with("description", "Duplica um efeito de partícula."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " remover <id>").with("description", "Remove um efeito permanentemente."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " list").with("description", "Lista todos os efeitos existentes."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " info <id>").with("description", "Mostra informações de um efeito."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " tphere <id>").with("description", "Teleporta um efeito para a sua localização."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " set <id> <prop> <valor>").with("description", "Modifica uma propriedade de um efeito."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " animate <id> <tipo> [opções]").with("description", "Aplica uma animação a um efeito."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " stopanimation <id>").with("description", "Para a animação e volta ao efeito estático."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " testplayer <id> [jogador]").with("description", "Mostra um efeito apenas para um jogador."));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " reload").with("description", "Recarrega todos os efeitos do ficheiro."));
        Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_FULL); // Usa rodapé completo (pois tem [])
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

    private void handleCreate(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 3) { sendUsage(sender, "/" + label + " criar <id> <tipo> [quantidade] [intervalo_ticks]"); return; }
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

    private void handleRemove(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, "/" + label + " remover <id>"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id));
            playSound(sender, SoundKeys.ERROR); return;
        }
        particleService.removeParticle(id);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_REMOVED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleClone(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 3) { sendUsage(sender, "/" + label + " clone <id original> <novo id>"); return; }

        String originalId = args[1];
        // <<< CORREÇÃO: Mover a declaração para antes do uso >>>
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

    private void handleInfo(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, "/" + label + " info <id>"); return; }
        String id = args[1];
        ParticleEntry entry = particleService.getParticleEntry(id);
        if (entry == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
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

    private void handleTpHere(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 2) { sendUsage(sender, "/" + label + " tphere <id>"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
        particleService.teleportParticle(id, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.PARTICLE_TELEPORTED).with("id", id));
        playSound(sender, SoundKeys.TELEPORT_WHOOSH);
    }

    private void handleSet(CommandSender sender, String[] args, String label) {
        if (args.length < 4) { sendUsage(sender, "/" + label + " set <id> <propriedade> <valor>"); return; }
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
                        sendUsage(sender, "/" + label + " set " + id + " offset <x> <y> <z>");
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
            // Logar o erro detalhado no console
            logger.log(Level.WARNING, "Erro ao executar /" + label + " set " + id + " " + prop + " " + value, e);
            success = false;
        }

        if (success) {
            particleService.updateParticle(entry);
            Messages.send(sender, Message.of(MessageKey.PARTICLE_PROPERTY_SET).with("prop", prop).with("id", id));
            playSound(sender, SoundKeys.SETTING_UPDATE);
        }
    }

    private void handleAnimate(CommandSender sender, String[] args, String label) {
        if (args.length < 3) { sendUsage(sender, "/" + label + " animate <id> <tipo> [opções...]"); return; }
        String id = args[1];
        ParticleEntry entry = particleService.getParticleEntry(id);
        if (entry == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
        String type = args[2].toLowerCase();

        if (!Arrays.asList("circle", "helix", "sphere").contains(type)) {
            // Reutiliza a mensagem de uso
            sendUsage(sender, "/" + label + " animate <id> <circle|helix|sphere> [opções...]");
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
                    // Usar MessageKey (assumindo que PARTICLE_INVALID_ANIMATION_OPTION foi adicionada)
                    // Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_ANIMATION_OPTION).with("option", args[i]));
                    // Por enquanto, log e mensagem hardcoded (mas traduzível)
                    logger.log(Level.FINER, "Opção de animação mal formatada: {0}", args[i]);
                    Messages.send(sender, "<yellow>Ignorando opção de animação mal formatada: " + args[i] + "</yellow>");
                }
            }
        }
        entry.setAnimationProperties(props);

        particleService.updateParticle(entry);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_ANIMATION_SET).with("type", type).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleStopAnimation(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, "/" + label + " stopanimation <id>"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
        particleService.stopAnimation(id);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_ANIMATION_STOPPED).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleTestPlayer(CommandSender sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, "/" + label + " testplayer <id> [jogador]"); return; }
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
            sendUsage(sender, "/" + label + " testplayer <id> <jogador>");
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

                    // 1. Verificar se o jogador está online NESTE SERVIDOR primeiro
                    Player targetOnline = Bukkit.getPlayer(targetUuid);

                    if (targetOnline == null || !targetOnline.isOnline()) {
                        // O jogador não está neste servidor Spigot.
                        String formattedNick = com.realmmc.controller.shared.utils.NicknameFormatter.getFullFormattedNick(targetUuid);
                        Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NOT_ONLINE)
                                .with("player", formattedNick));
                        playSound(sender, SoundKeys.ERROR);
                        return;
                    }

                    // 2. O jogador está neste servidor, AGORA verificar se está autenticado
                    Optional<String> authError = AuthenticationGuard.checkCanInteractWith(targetUuid);
                    if (authError.isPresent()) {
                        // O jogador está aqui, mas ainda está no estado "CONNECTING"
                        Messages.send(sender, authError.get()); // Envia a mensagem (ex: "jogador conectando")
                        playSound(sender, SoundKeys.ERROR);
                        return;
                    }

                    // 3. O jogador está online E autenticado. Enviar partícula.
                    if (particleService.spawnForPlayerOnce(targetOnline, particleId)) {
                        String formattedNick = com.realmmc.controller.shared.utils.NicknameFormatter.getFullFormattedNick(targetUuid);
                        Messages.send(sender, Message.of(MessageKey.PARTICLE_TESTED)
                                .with("id", particleId)
                                .with("player", formattedNick));
                        playSound(sender, SoundKeys.SUCCESS);
                    } else {
                        Messages.send(sender, MessageKey.COMMAND_ERROR);
                        playSound(sender, SoundKeys.ERROR);
                    }

                }, runnable -> Bukkit.getScheduler().runTask(ServiceRegistry.getInstance().requireService(Plugin.class), runnable))
                .exceptionally(ex -> {
                    // Log padronizado
                    logger.log(Level.SEVERE, "Erro ao resolver perfil ou enviar partícula para " + finalTargetNameInput, ex);
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
                    "criar", "clone", "remover", "list", "info", "tphere", "set", "animate",
                    "stopanimation", "testplayer", "reload", "help"), completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList("remover", "clone", "info", "tphere", "set", "animate",
                    "stopanimation", "testplayer").contains(sub)) {
                StringUtil.copyPartialMatches(currentArg, particleService.getAllParticleIds(), completions);
            }
        }

        // <<< CORREÇÃO DE TAB COMPLETION AQUI (Lógica para args.length == 3) >>>
        else if (args.length == 3) {
            String sub = args[0].toLowerCase();

            if (sub.equals("criar")) {
                // Sugere tipos de partícula (para /particle criar <id> <tipo>)
                List<String> pTypes = Stream.of(Particle.values())
                        .map(m -> m.name().toLowerCase())
                        .collect(Collectors.toList());
                StringUtil.copyPartialMatches(currentArg, pTypes, completions);
            }
            else if (sub.equals("set")) {
                // Sugere propriedades (para /particle set <id> <propriedade>)
                StringUtil.copyPartialMatches(currentArg, Arrays.asList(
                        "tipo", "quantidade", "intervalo", "velocidade", "offset", "dados", "distancia"), completions);
            }
            else if (sub.equals("testplayer")) {
                // Sugere jogadores online (para /particle testplayer <id> <jogador>)
                List<String> suggestions = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                StringUtil.copyPartialMatches(currentArg, suggestions, completions);
            }
            else if (sub.equals("animate")) {
                // Sugere tipos de animação (para /particle animate <id> <tipo>)
                StringUtil.copyPartialMatches(currentArg, Arrays.asList("circle", "helix", "sphere"), completions);
            }
        }
        // <<< FIM DA CORREÇÃO DE TAB COMPLETION AQUI >>>

        else if (args.length == 4) {
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