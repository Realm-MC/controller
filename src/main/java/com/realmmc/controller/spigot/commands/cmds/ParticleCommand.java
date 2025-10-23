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
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.entities.config.ParticleEntry;
import com.realmmc.controller.spigot.entities.particles.ParticleService;
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

    public ParticleCommand() {
        this.particleService = ServiceRegistry.getInstance().getService(ParticleService.class)
                .orElseThrow(() -> new IllegalStateException("ParticleService não foi encontrado!"));
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            Messages.send(sender, MessageKey.COMMON_NO_PERMISSION_GENERIC);
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "help": showHelp(sender); break;
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

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 3) { sendUsage(sender, "/particle criar <id> <tipo> [quantidade] [intervalo_ticks]"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) != null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_ID).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
        String particleType = args[2].toUpperCase();
        try { Particle.valueOf(particleType); }
        catch (IllegalArgumentException e) { Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_TYPE).with("type", particleType)); playSound(sender, SoundKeys.ERROR); return; }
        int amount = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        int interval = args.length > 4 ? Integer.parseInt(args[4]) : 20;
        particleService.createParticle(id, player.getLocation(), particleType, amount, interval);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_CREATED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/particle remover <id>"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
        particleService.removeParticle(id);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_REMOVED).with("id", id));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleClone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 3) { sendUsage(sender, "/particle clone <id original> <novo id>"); return; }
        String originalId = args[1];
        String newId = args[2];
        if (particleService.getParticleEntry(originalId) == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", originalId)); playSound(sender, SoundKeys.ERROR); return; }
        if (particleService.getParticleEntry(newId) != null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_ID).with("id", newId)); playSound(sender, SoundKeys.ERROR); return; }
        particleService.cloneParticle(originalId, newId, player.getLocation());
        Messages.send(sender, Message.of(MessageKey.PARTICLE_CLONED).with("originalId", originalId).with("newId", newId));
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleList(CommandSender sender) {
        var ids = particleService.getAllParticleIds();
        if (ids.isEmpty()) { Messages.send(sender, MessageKey.PARTICLE_LIST_EMPTY); playSound(sender, SoundKeys.NOTIFICATION); return; }
        Messages.send(sender, Message.of(MessageKey.PARTICLE_LIST_HEADER).with("ids", String.join(", ", ids)));
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/particle info <id>"); return; }
        String id = args[1];
        ParticleEntry entry = particleService.getParticleEntry(id);
        if (entry == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
        String longDist = Messages.translate(entry.isLongDistance() ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE);
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "Efeito " + id));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "ID").with("value", entry.getId()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Tipo").with("value", entry.getParticleType()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Animação").with("value", (entry.getAnimationType() != null ? entry.getAnimationType() : "Nenhuma")));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Quantidade").with("value", entry.getAmount()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Intervalo").with("value", entry.getUpdateInterval() + " ticks"));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Velocidade").with("value", entry.getSpeed()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Offset").with("value", String.format("X:%.2f, Y:%.2f, Z:%.2f", entry.getOffsetX(), entry.getOffsetY(), entry.getOffsetZ())));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Dados").with("value", (entry.getParticleData() != null ? entry.getParticleData() : "Nenhum")));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Longa Distância").with("value", longDist));
        Messages.send(sender, "<white>");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.send(sender, MessageKey.ONLY_PLAYERS); playSound(sender, SoundKeys.ERROR); return; }
        if (args.length < 2) { sendUsage(sender, "/particle tphere <id>"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
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
        try {
            switch (prop) {
                case "tipo": entry.setParticleType(Particle.valueOf(value.toUpperCase()).name()); break;
                case "quantidade": entry.setAmount(Integer.parseInt(value)); break;
                case "intervalo": entry.setUpdateInterval(Integer.parseInt(value)); break;
                case "velocidade": entry.setSpeed(Double.parseDouble(value)); break;
                case "offset":
                    String[] parts = value.split(" ");
                    if (parts.length != 3) { sendUsage(sender, "/particle set " + id + " offset <x> <y> <z>"); return; }
                    entry.setOffsetX(Double.parseDouble(parts[0])); entry.setOffsetY(Double.parseDouble(parts[1])); entry.setOffsetZ(Double.parseDouble(parts[2])); break;
                case "dados": entry.setParticleData(value.equalsIgnoreCase("nenhum") ? null : value); break;
                case "distancia": entry.setLongDistance(Boolean.parseBoolean(value)); break;
                default:
                    Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_PROPERTY).with("properties", "tipo, quantidade, intervalo, velocidade, offset, dados, distancia."));
                    playSound(sender, SoundKeys.ERROR); return;
            }
        } catch (Exception e) {
            Messages.send(sender, Message.of(MessageKey.PARTICLE_INVALID_VALUE).with("prop", prop));
            playSound(sender, SoundKeys.ERROR); return;
        }
        particleService.updateParticle(entry);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_PROPERTY_SET).with("prop", prop).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleAnimate(CommandSender sender, String[] args) {
        if (args.length < 3) { sendUsage(sender, "/particle animate <id> <tipo> [opções...]"); return; }
        String id = args[1];
        ParticleEntry entry = particleService.getParticleEntry(id);
        if (entry == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
        String type = args[2].toLowerCase();
        entry.setAnimationType(type);
        Map<String, String> props = new HashMap<>();
        if (args.length > 3) { for (int i = 3; i < args.length; i++) { String[] parts = args[i].split(":", 2); if (parts.length == 2) props.put(parts[0].toLowerCase(), parts[1]); } }
        entry.setAnimationProperties(props);
        particleService.updateParticle(entry);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_ANIMATION_SET).with("type", type).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleStopAnimation(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/particle stopanimation <id>"); return; }
        String id = args[1];
        if (particleService.getParticleEntry(id) == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", id)); playSound(sender, SoundKeys.ERROR); return; }
        particleService.stopAnimation(id);
        Messages.send(sender, Message.of(MessageKey.PARTICLE_ANIMATION_STOPPED).with("id", id));
        playSound(sender, SoundKeys.SETTING_UPDATE);
    }

    private void handleTestPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) { sendUsage(sender, "/particle testplayer <id> [jogador]"); return; }
        String particleId = args[1];
        if (particleService.getParticleEntry(particleId) == null) { Messages.send(sender, Message.of(MessageKey.PARTICLE_NOT_FOUND).with("id", particleId)); playSound(sender, SoundKeys.ERROR); return; }

        Player targetOnline = null;
        String targetNameInput = null;
        Optional<Profile> targetProfileOpt = Optional.empty();

        if (args.length > 2) {
            targetNameInput = args[2];
            targetProfileOpt = ProfileResolver.resolve(targetNameInput);
        } else if (sender instanceof Player) {
            targetOnline = (Player) sender;
            targetProfileOpt = ProfileResolver.resolve(targetOnline.getUniqueId().toString());
            targetNameInput = sender.getName();
        } else {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            playSound(sender, SoundKeys.ERROR); return;
        }

        if (targetOnline == null && targetProfileOpt.isPresent()) {
            targetOnline = Bukkit.getPlayer(targetProfileOpt.get().getUuid());
        }

        if (targetOnline != null && targetProfileOpt.isPresent()) {
            Profile targetProfile = targetProfileOpt.get();
            if (particleService.spawnForPlayerOnce(targetOnline, particleId)) {
                String nameToShow = targetProfile.getName();
                Messages.send(sender, Message.of(MessageKey.PARTICLE_TESTED).with("id", particleId).with("player", nameToShow));
                playSound(sender, SoundKeys.SUCCESS);
            } else {
                Messages.send(sender, MessageKey.COMMAND_ERROR);
                playSound(sender, SoundKeys.ERROR);
            }
        } else if (targetProfileOpt.isPresent()) {
            Profile targetProfile = targetProfileOpt.get();
            String nameToShow = targetProfile.getName();
            Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NOT_ONLINE).with("player", nameToShow));
            playSound(sender, SoundKeys.ERROR);
        } else {
            String nameUsedForSearch = targetNameInput != null ? targetNameInput : "???";
            Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", nameUsedForSearch));
            playSound(sender, SoundKeys.ERROR);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) { return Collections.emptyList(); }
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("criar", "remover", "clone", "list", "info", "tphere", "set", "animate", "stopanimation", "testplayer", "reload", "help"), completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (!sub.equals("criar") && !sub.equals("list") && !sub.equals("reload") && !sub.equals("help")) {
                StringUtil.copyPartialMatches(args[1], particleService.getAllParticleIds(), completions);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("criar")) {
                List<String> particleTypes = Stream.of(Particle.values()).map(Enum::name).collect(Collectors.toList());
                StringUtil.copyPartialMatches(args[2], particleTypes, completions);
            } else if (sub.equals("set")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("tipo", "quantidade", "intervalo", "velocidade", "offset", "dados", "distancia"), completions);
            } else if (sub.equals("testplayer")) {
                List<String> suggestions = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                suggestions.add("id:");
                suggestions.add("uuid:");
                StringUtil.copyPartialMatches(args[2], suggestions, completions);
            } else if (sub.equals("animate")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("circle", "helix", "sphere"), completions);
            }
        } else if (args.length >= 4) {
            String sub = args[0].toLowerCase();
            if (sub.equals("set")) {
                String prop = args[2].toLowerCase();
                if (prop.equals("tipo")) { List<String> pTypes = Stream.of(Particle.values()).map(Enum::name).collect(Collectors.toList()); StringUtil.copyPartialMatches(args[3], pTypes, completions); }
                else if (prop.equals("distancia")) { StringUtil.copyPartialMatches(args[3], Arrays.asList("true", "false"), completions); }
            } else if (sub.equals("animate")) {
                String animType = args[2].toLowerCase();
                String currentArg = args[args.length - 1];
                List<String> options = new ArrayList<>();
                if (animType.equals("circle")) options.addAll(Arrays.asList("radius:", "speed:"));
                else if (animType.equals("helix")) options.addAll(Arrays.asList("radius:", "height:", "speed:"));
                else if (animType.equals("sphere")) options.addAll(Arrays.asList("radius:", "density:"));
                Set<String> usedOptions = Arrays.stream(args, 3, args.length -1).map(s -> s.split(":")[0].toLowerCase() + ":").collect(Collectors.toSet());
                options.removeIf(usedOptions::contains);
                StringUtil.copyPartialMatches(currentArg, options, completions);
            }
        }
        Collections.sort(completions);
        return completions;
    }
}