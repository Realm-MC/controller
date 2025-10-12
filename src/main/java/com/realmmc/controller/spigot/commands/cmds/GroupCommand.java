package com.realmmc.controller.spigot.commands.cmds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.display.PlayerDisplayService;
import com.realmmc.controller.shared.permission.PermissionService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileResolver;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.role.RoleService;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.utils.TimeUtils;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.commands.CommandInterface;
import com.realmmc.controller.spigot.sounds.SoundKeys;
import com.realmmc.controller.spigot.sounds.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Cmd(cmd = "group", aliases = {"cargo", "grupo"})
public class GroupCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final ProfileService profileService;
    private final RoleService roleService;
    private final PlayerDisplayService displayService;
    private final SoundService soundService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GroupCommand() {
        this.profileService = ServiceRegistry.getInstance().getService(ProfileService.class).orElseThrow();
        this.roleService = ServiceRegistry.getInstance().getService(RoleService.class).orElseThrow();
        this.displayService = ServiceRegistry.getInstance().getService(PlayerDisplayService.class).orElseThrow();
        this.soundService = ServiceRegistry.getInstance().getService(SoundService.class).orElseThrow();
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
            case "help" -> showHelp(sender);
            case "add" -> handleAdd(sender, args);
            case "set" -> handleSet(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender, args);
            case "clear" -> handleClear(sender, args);
            default -> showHelp(sender);
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(" ");
        sender.sendMessage("§6Comandos disponíveis para Cargos");
        sender.sendMessage("§e /group add <usuário> <grupo> [tempo] [-hidden] §8- §7Adiciona um grupo a um jogador.");
        sender.sendMessage("§e /group set <usuário> <grupo> [-hidden] §8- §7Define um grupo, limpando os outros.");
        sender.sendMessage("§e /group remove <usuário> <grupo> §8- §7Remove um grupo de um jogador.");
        sender.sendMessage("§e /group info <usuário|grupo> §8- §7Vê informações de um grupo ou usuário.");
        sender.sendMessage("§e /group list <grupo> §8- §7Vê a lista de usuários em um grupo.");
        sender.sendMessage("§e /group clear <usuário|grupo> §8- §7Limpa os grupos de um jogador ou jogadores de um grupo.");
        sender.sendMessage(" ");
        sender.sendMessage("§6OBS.: §7As informações com <> são obrigatórios e [] são opcionais");
        sender.sendMessage("§f");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/group add <usuário> <grupo> [tempo] [-hidden].");
            return;
        }
        String target = args[1];
        String roleName = args[2];

        Optional<Profile> profileOpt = ProfileResolver.resolve(target);
        if (profileOpt.isEmpty()) {
            sender.sendMessage("§cO " + target + " nunca entrou no servidor.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        Profile profile = profileOpt.get();

        Optional<Role> roleOpt = roleService.getByName(roleName);
        if (roleOpt.isEmpty()) {
            sender.sendMessage("§cO grupo '" + roleName + "' não foi encontrado.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        Role role = roleOpt.get();

        long duration = 0;
        boolean hidden = Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("-hidden"));
        String durationStr = "";

        for (int i = 3; i < args.length; i++) {
            if (!args[i].equalsIgnoreCase("-hidden")) {
                duration = TimeUtils.parseDuration(args[i]);
                durationStr = args[i];
                if (duration == -1) {
                    sender.sendMessage("§cFormato de duração inválido! Use 'd', 'h', 'm', 's'.");
                    playSound(sender, SoundKeys.USAGE_ERROR);
                    return;
                }
            }
        }

        profileService.addRole(profile.getUuid(), role.getId(), duration);
        sender.sendMessage("§aO grupo " + role.getDisplayName() + " §afoi adicionado a " + profile.getName() + "§a" + (duration > 0 ? " por " + durationStr : " permanentemente") + ".");
        playSound(sender, SoundKeys.SUCCESS);

        if (!hidden) {
            announceRoleChange(profile, role);
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/group set <usuário> <grupo> [-hidden].");
            return;
        }
        String target = args[1];
        String roleName = args[2];

        Optional<Profile> profileOpt = ProfileResolver.resolve(target);
        if (profileOpt.isEmpty()) {
            sender.sendMessage("§cO " + target + " nunca entrou no servidor.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        Profile profile = profileOpt.get();

        Optional<Role> roleOpt = roleService.getByName(roleName);
        if (roleOpt.isEmpty()) {
            sender.sendMessage("§cO grupo '" + roleName + "' não foi encontrado.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        Role role = roleOpt.get();

        boolean hidden = Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("-hidden"));

        profileService.setRoles(profile.getUuid(), List.of(role.getId()));
        sender.sendMessage("§aOs grupos de " + profile.getName() + " §aforam definidos para apenas " + role.getDisplayName() + "§a.");
        playSound(sender, SoundKeys.SUCCESS);

        if (!hidden) {
            announceRoleChange(profile, role);
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, "/group remove <usuário> <grupo>.");
            return;
        }
        String target = args[1];
        String roleName = args[2];

        Optional<Profile> profileOpt = ProfileResolver.resolve(target);
        if (profileOpt.isEmpty()) {
            sender.sendMessage("§cO " + target + " nunca entrou no servidor.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        Profile profile = profileOpt.get();

        Optional<Role> roleOpt = roleService.getByName(roleName);
        if (roleOpt.isEmpty()) {
            sender.sendMessage("§cO grupo '" + roleName + "' não foi encontrado.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        Role role = roleOpt.get();

        if (!profile.getRoleIds().contains(role.getId())) {
            sender.sendMessage("§cO jogador " + profile.getName() + " §cnão possui o grupo " + role.getDisplayName() + "§c.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        profileService.removeRole(profile.getUuid(), role.getId());
        sender.sendMessage("§aO grupo " + role.getDisplayName() + " §afoi removido de " + profile.getName() + "§a.");
        playSound(sender, SoundKeys.SUCCESS);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/group info <usuário|grupo>.");
            return;
        }
        String target = args[1];

        Optional<Profile> profileOpt = ProfileResolver.resolve(target);
        if (profileOpt.isPresent()) {
            Profile profile = profileOpt.get();
            PermissionService ps = ServiceRegistry.getInstance().getService(PermissionService.class).get();
            Role primaryRole = ps.getPrimaryRole(profile);

            sender.sendMessage(" ");
            sender.sendMessage("§6Informações de Cargos de " + displayService.getColorDisplayName(profile));
            sender.sendMessage("§f Cargo Principal: " + (primaryRole != null ? primaryRole.getDisplayName() + " §7(Peso: " + primaryRole.getWeight() + ")" : "§cNenhum"));

            sender.sendMessage("§f Todos os Cargos Ativos (" + profile.getRoleIds().size() + "):");
            profile.getRoleIds().stream()
                    .map(roleService::getById)
                    .filter(Optional::isPresent).map(Optional::get)
                    .sorted(Comparator.comparingInt(Role::getWeight).reversed())
                    .forEach(role -> {
                        String status = "§aPermanente";
                        if (profile.getPausedRoleDurations().containsKey(String.valueOf(role.getId()))) {
                            status = "§ePausado §7(" + TimeUtils.formatDuration(profile.getPausedRoleDurations().get(String.valueOf(role.getId()))) + " restantes)";
                        } else if (profile.getRoleExpirations().containsKey(String.valueOf(role.getId()))) {
                            long remaining = profile.getRoleExpirations().get(String.valueOf(role.getId())) - System.currentTimeMillis();
                            status = remaining > 0 ? "§cExpira em " + TimeUtils.formatDuration(remaining) : "§4Expirado";
                        }
                        sender.sendMessage("  §e- " + role.getDisplayName() + " §7(" + status + "§7)");
                    });
            sender.sendMessage(" ");
            playSound(sender, SoundKeys.NOTIFICATION);
            return;
        }

        Optional<Role> roleOpt = roleService.getByName(target);
        if (roleOpt.isPresent()) {
            Role role = roleOpt.get();
            sender.sendMessage(" ");
            sender.sendMessage("§6Informações do Grupo " + role.getDisplayName());
            sender.sendMessage("§f Nome Interno: §7" + role.getName());
            sender.sendMessage("§f ID: §7" + role.getId());
            sender.sendMessage("§f Prefixo: §r" + role.getPrefix());
            sender.sendMessage("§f Sufixo: §r" + role.getSuffix());
            sender.sendMessage("§f Cor: §r" + role.getColor() + "Exemplo");
            sender.sendMessage("§f Tipo: §7" + role.getType());
            sender.sendMessage("§f Peso: §7" + role.getWeight());
            sender.sendMessage("§f Permissões Diretas: §7" + role.getPermissions().size());
            sender.sendMessage(" ");
            playSound(sender, SoundKeys.NOTIFICATION);
            return;
        }

        sender.sendMessage("§c'" + target + "' não foi encontrado como jogador ou grupo.");
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/group list <grupo>.");
            return;
        }
        String roleName = args[1];
        Optional<Role> roleOpt = roleService.getByName(roleName);
        if (roleOpt.isEmpty()) {
            sender.sendMessage("§cO grupo '" + roleName + "' não foi encontrado.");
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        Role role = roleOpt.get();

        List<Profile> profiles = profileService.getProfilesInRole(role.getId());
        if (profiles.isEmpty()) {
            sender.sendMessage("§eNenhum jogador encontrado no grupo " + role.getDisplayName() + "§e.");
            playSound(sender, SoundKeys.NOTIFICATION);
            return;
        }

        String playerNames = profiles.stream().map(Profile::getName).collect(Collectors.joining(", "));
        sender.sendMessage("§6Jogadores no grupo " + role.getDisplayName() + " §7(" + profiles.size() + "):");
        sender.sendMessage("§7" + playerNames);
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/group clear <usuário|grupo>.");
            return;
        }
        String target = args[1];

        Optional<Profile> profileOpt = ProfileResolver.resolve(target);
        if (profileOpt.isPresent()) {
            profileService.clearRoles(profileOpt.get().getUuid());
            sender.sendMessage("§aTodos os grupos de " + target + " foram removidos.");
            playSound(sender, SoundKeys.SUCCESS);
            return;
        }

        Optional<Role> roleOpt = roleService.getByName(target);
        if (roleOpt.isPresent()) {
            Role role = roleOpt.get();
            List<Profile> profiles = profileService.getProfilesInRole(role.getId());
            if (profiles.isEmpty()) {
                sender.sendMessage("§eNenhum jogador para remover do grupo " + role.getDisplayName() + "§e.");
                return;
            }
            for (Profile p : profiles) {
                profileService.removeRole(p.getUuid(), role.getId());
            }
            sender.sendMessage("§a" + profiles.size() + " jogadores foram removidos do grupo " + role.getDisplayName() + "§a.");
            playSound(sender, SoundKeys.SUCCESS);
            return;
        }

        sender.sendMessage("§c'" + target + "' não foi encontrado como jogador ou grupo.");
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void announceRoleChange(Profile profile, Role newRole) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("type", "ROLE_ANNOUNCEMENT");
            node.put("playerName", displayService.getColorDisplayName(profile));
            node.put("roleName", newRole.getDisplayName());

            String json = mapper.writeValueAsString(node);
            RedisPublisher.publish(RedisChannel.GLOBAL_ANNOUNCEMENT, json);
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.WARNING, "Falha ao publicar anúncio de cargo via Redis", e);
        }
    }

    private void playSound(CommandSender sender, String key) {
        if (sender instanceof Player) {
            soundService.playSound((Player) sender, key);
        }
    }

    private void sendUsage(CommandSender sender, String usage) {
        sender.sendMessage("§cUtilize: " + usage);
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(permission)) {
            return Collections.emptyList();
        }
        final List<String> completions = new ArrayList<>();
        List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        List<String> roleNames = roleService.getAll().stream().map(Role::getName).toList();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], List.of("add", "set", "remove", "info", "list", "clear", "help"), completions);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (List.of("add", "set", "remove").contains(sub)) {
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
            } else if (List.of("info", "clear").contains(sub)) {
                List<String> targets = new ArrayList<>(playerNames);
                targets.addAll(roleNames);
                StringUtil.copyPartialMatches(args[1], targets, completions);
            } else if (sub.equals("list")) {
                StringUtil.copyPartialMatches(args[1], roleNames, completions);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (List.of("add", "set", "remove").contains(sub)) {
                StringUtil.copyPartialMatches(args[2], roleNames, completions);
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("add")) {
            StringUtil.copyPartialMatches(args[3], List.of("10s", "1m", "1h", "1d", "30d", "-hidden"), completions);
        }

        Collections.sort(completions);
        return completions;
    }
}