package com.palacesky.controller.proxy.commands.cmds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.PlayerSessionData;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.proxy.Proxy;
import com.palacesky.controller.proxy.commands.CommandInterface;
import com.palacesky.controller.shared.annotations.Cmd;
import com.palacesky.controller.shared.logs.RoleLog;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.profile.Profile;
import com.palacesky.controller.shared.profile.ProfileResolver;
import com.palacesky.controller.shared.profile.ProfileService;
import com.palacesky.controller.shared.role.PlayerRole;
import com.palacesky.controller.shared.role.Role;
import com.palacesky.controller.shared.role.RoleKickHandler;
import com.palacesky.controller.shared.role.RoleType;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisPublisher;
import com.palacesky.controller.shared.utils.NicknameFormatter;
import com.palacesky.controller.shared.utils.TaskScheduler;
import com.palacesky.controller.shared.utils.TimeUtils;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Cmd(cmd = "role", aliases = {"group", "rank"}, onlyPlayer = false)
public class RoleCommand implements CommandInterface {

    private final String requiredPermission = "controller.manager";
    private final String requiredGroupName = "Gerente";
    private final RoleService roleService;
    private final ProfileService profileService;
    private final Optional<SoundPlayer> soundPlayerOpt;
    private final Logger logger;
    private final ProxyServer proxyServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoleCommand() {
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        this.logger = Proxy.getInstance().getLogger();
    }

    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        if (!sender.hasPermission(requiredPermission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", requiredGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length == 0 || (args.length > 0 && args[0].equalsIgnoreCase("help"))) {
            showHelp(sender, label);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info":
                handleInfo(sender, args, label);
                break;
            case "history":
                handleHistory(sender, args, label);
                break;
            case "add":
                modifyPlayerRole(sender, args, label, RoleModificationType.ADD);
                break;
            case "remove":
                modifyPlayerRole(sender, args, label, RoleModificationType.REMOVE);
                break;
            case "set":
                modifyPlayerRole(sender, args, label, RoleModificationType.SET);
                break;
            case "clear":
                handleClear(sender, args, label);
                break;
            case "list":
                handleList(sender, args, label);
                break;
            default:
                if (subCommand.startsWith("id:") || subCommand.startsWith("role:")) {
                    handleLogLookup(sender, subCommand.split(":")[1]);
                } else {
                    sendUsage(sender, label, "help");
                }
                break;
        }
    }

    private void handleInfo(CommandSource sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, label, "info <jogador | grupo>");
            return;
        }
        String targetInput = args[1];

        if (targetInput.toLowerCase().startsWith("role:") || targetInput.toLowerCase().startsWith("id:")) {
            handleLogLookup(sender, targetInput.split(":")[1]);
            return;
        }

        Optional<Role> roleOpt = roleService.getRole(targetInput);
        if (roleOpt.isPresent()) {
            displayGroupInfo(sender, roleOpt.get());
        } else {
            displayPlayerInfo(sender, targetInput);
        }
    }

    private void handleHistory(CommandSource sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, label, "history <jogador>");
            return;
        }
        String targetName = args[1];
        resolveProfileAsync(targetName).thenAccept(opt -> {
            if (opt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetName));
                return;
            }
            Profile profile = opt.get();
            List<RoleLog> logs = roleService.getLogRepository().findByTarget(profile.getUuid(), 10);

            String formattedName = NicknameFormatter.getFullFormattedNick(profile.getUuid());

            Messages.send(sender, Message.of(MessageKey.HISTORY_HEADER)
                    .with("type", "Cargos")
                    .with("player", formattedName));

            if (logs.isEmpty()) {
                Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY);
            } else {
                for (RoleLog log : logs) {
                    String roleDisplayName = log.getRoleName();
                    Optional<Role> rOpt = roleService.getRole(log.getRoleName());
                    if (rOpt.isPresent()) {
                        roleDisplayName = rOpt.get().getDisplayName();
                    }

                    Messages.send(sender, Message.of(MessageKey.HISTORY_ENTRY_ROLE)
                            .with("id", log.getId())
                            .with("action", log.getAction().name())
                            .with("role", roleDisplayName)
                            .with("source", log.getSource())
                            .with("date", TimeUtils.formatDate(log.getTimestamp())));
                }
            }
            playSound(sender, SoundKeys.NOTIFICATION);
        });
    }

    private void handleLogLookup(CommandSource sender, String id) {
        TaskScheduler.runAsync(() -> {
            Optional<RoleLog> logOpt = roleService.getLogRepository().findById(id);
            if (logOpt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.HISTORY_NOT_FOUND).with("id", id));
                return;
            }
            RoleLog log = logOpt.get();

            String roleDisplayName = log.getRoleName();
            Optional<Role> rOpt = roleService.getRole(log.getRoleName());
            if (rOpt.isPresent()) {
                roleDisplayName = rOpt.get().getDisplayName();
            }

            Messages.send(sender, Message.of(MessageKey.HISTORY_DETAILS_HEADER).with("id", log.getId()));
            sendDetail(sender, "Alvo", log.getTargetName());
            sendDetail(sender, "Ação", log.getAction().name());
            sendDetail(sender, "Cargo", roleDisplayName);
            sendDetail(sender, "Por", log.getSource());
            sendDetail(sender, "Data", TimeUtils.formatDate(log.getTimestamp()));
            sendDetail(sender, "Duração", log.getDuration());
            sendDetail(sender, "Contexto", log.getContext());
            playSound(sender, SoundKeys.NOTIFICATION);
        });
    }

    private void sendDetail(CommandSource sender, String key, String val) {
        Messages.send(sender, Message.of(MessageKey.HISTORY_DETAILS_LINE).with("key", key).with("value", val));
    }

    private void displayPlayerInfo(CommandSource sender, String targetInput) {
        Locale locale = Messages.determineLocale(sender);

        resolveProfileAsync(targetInput).thenCompose(targetProfileOpt -> {
            if (targetProfileOpt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetInput));
                playSound(sender, SoundKeys.ERROR);
                return CompletableFuture.completedFuture(null);
            }

            Profile profile = targetProfileOpt.get();
            return roleService.loadPlayerDataAsync(profile.getUuid())
                    .thenApply(sessionData -> new AbstractMap.SimpleImmutableEntry<>(profile, sessionData));

        }).thenAccept(entry -> {
            if (entry == null) return;

            Profile profile = entry.getKey();
            PlayerSessionData sessionData = entry.getValue();
            if (sessionData == null) sessionData = roleService.getDefaultSessionData(profile.getUuid());

            String formattedNick = NicknameFormatter.getFullFormattedNick(profile.getUuid());

            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "Grupos de " + formattedNick));

            Role primary = sessionData.getPrimaryRole();
            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE)
                    .with("key", Messages.translate(MessageKey.ROLE_INFO_PRIMARY_ACTIVE, locale))
                    .with("value", primary.getDisplayName()));

            List<PlayerRole> roles = profile.getRoles();
            if (roles == null) roles = Collections.emptyList();

            List<PlayerRole> sortedRoles = new ArrayList<>(roles);
            sortedRoles.sort((r1, r2) -> {
                int w1 = roleService.getRole(r1.getRoleName()).map(Role::getWeight).orElse(0);
                int w2 = roleService.getRole(r2.getRoleName()).map(Role::getWeight).orElse(0);
                return Integer.compare(w2, w1);
            });

            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER)
                    .with("key", "Grupos")
                    .with("count", sortedRoles.size()));

            if (sortedRoles.isEmpty()) {
                Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY);
            } else {
                int index = 1;
                for (PlayerRole pr : sortedRoles) {

                    String statusText;
                    String statusColor;

                    if (pr.isPaused()) {
                        statusText = "PAUSADO";
                        statusColor = "<yellow>";
                    } else if (pr.isActive()) {
                        statusText = "ATIVO";
                        statusColor = "<green>";
                    } else if (pr.isPendingNotification()) {
                        statusText = "PENDENTE";
                        statusColor = "<aqua>";
                    } else {
                        statusText = pr.getStatus().name();
                        statusColor = "<red>";
                    }

                    Optional<Role> rOpt = roleService.getRole(pr.getRoleName());
                    String display = rOpt.map(Role::getDisplayName).orElse(pr.getRoleName());

                    String infoExtra = String.format("<dark_gray>(ID: %s, Por: %s, Data: %s)</dark_gray>",
                            pr.getInstanceId(),
                            pr.getAddedBy() != null ? pr.getAddedBy() : "Sistema",
                            TimeUtils.formatDate(pr.getAddedAt()));

                    String duracao;
                    if (pr.isPaused() && pr.getPausedTimeRemaining() != null) {
                        duracao = "Restam: " + TimeUtils.formatDuration(pr.getPausedTimeRemaining());
                    } else if (pr.isPermanent()) {
                        duracao = "Perm.";
                    } else {
                        duracao = TimeUtils.formatDuration(Math.max(0, pr.getExpiresAt() - System.currentTimeMillis()));
                    }

                    Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM)
                            .with("index", index++)
                            .with("value", String.format("%s %s%s <gray>(%s)</gray> %s", display, statusColor, statusText, duracao, infoExtra)));
                }
            }
            Messages.send(sender, " ");
            playSound(sender, SoundKeys.NOTIFICATION);
        });
    }


    private void displayGroupInfo(CommandSource sender, Role role) {
        Locale locale = Messages.determineLocale(sender);
        Messages.send(sender, Message.of(MessageKey.ROLE_GROUP_INFO_HEADER).with("group_name", role.getDisplayName()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "ID").with("value", role.getName()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_WEIGHT, locale)).with("value", role.getWeight()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_PREFIX, locale)).with("value", role.getPrefix()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_TYPE, locale)).with("value", role.getType().name()));
        Messages.send(sender, " ");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private enum RoleModificationType {ADD, REMOVE, SET}

    private void modifyPlayerRole(CommandSource sender, String[] args, String label, RoleModificationType type) {
        int minArgs = 3;
        if (args.length < minArgs) {
            sendUsage(sender, label, args[0] + " <jogador> <grupo> [duração]");
            return;
        }

        String targetName = args[1];
        String roleName = args[2].toLowerCase();
        String durationStr = (args.length > 3) ? args[3] : null;

        boolean hiddenArg = (args.length > 0 && args[args.length - 1].equalsIgnoreCase("-hidden"));
        if (hiddenArg && durationStr != null && durationStr.equalsIgnoreCase("-hidden")) {
            durationStr = null;
        }

        Optional<Role> targetRoleOpt = roleService.getRole(roleName);
        if (targetRoleOpt.isEmpty()) {
            Messages.send(sender, Message.of(MessageKey.ROLE_ERROR_GROUP_NOT_FOUND).with("group", roleName));
            playSound(sender, SoundKeys.ERROR);
            return;
        }
        Role targetRole = targetRoleOpt.get();

        if (!checkHierarchy(sender, targetRole)) {
            Messages.send(sender, MessageKey.ROLE_ERROR_CANNOT_MANAGE_SUPERIOR);
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        Long durationMillis = null;
        if (durationStr != null && !durationStr.equalsIgnoreCase("-hidden")) {
            long parsed = TimeUtils.parseDuration(durationStr);
            if (parsed <= 0) {
                Messages.send(sender, MessageKey.ROLE_ERROR_INVALID_DURATION);
                playSound(sender, SoundKeys.ERROR);
                return;
            }
            durationMillis = parsed;
        }

        final Long finalDuration = durationMillis;
        final Locale locale = Messages.determineLocale(sender);
        final String sourceName = (sender instanceof Player) ? ((Player) sender).getUsername() : "Console";

        resolveProfileAsync(targetName).thenCompose(opt -> {
            if (opt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetName));
                playSound(sender, SoundKeys.ERROR);
                return CompletableFuture.completedFuture(null);
            }

            Profile profile = opt.get();
            return roleService.loadPlayerDataAsync(profile.getUuid())
                    .thenApply(targetData -> new AbstractMap.SimpleImmutableEntry<>(profile, targetData));

        }).thenAccept(entry -> {
            if (entry == null) return;

            Profile profile = entry.getKey();
            PlayerSessionData targetData = entry.getValue();
            UUID uuid = profile.getUuid();
            String targetFormatted = NicknameFormatter.getFullFormattedNick(uuid);

            if (targetData != null && !checkHierarchy(sender, targetData.getPrimaryRole())) {
                Messages.send(sender, Message.of(MessageKey.ROLE_ERROR_CLEAR_SUPERIOR).with("target_name", targetFormatted));
                playSound(sender, SoundKeys.ERROR);
                return;
            }

            String durationMsg = (finalDuration == null) ?
                    Messages.translate(MessageKey.ROLE_INFO_STATUS_PERMANENT, locale).replaceAll("<[^>]*>", "") :
                    " por " + TimeUtils.formatDuration(finalDuration);

            switch (type) {
                case ADD:
                    roleService.grantRole(uuid, roleName, finalDuration, sourceName);
                    Messages.send(sender, Message.of(MessageKey.ROLE_SUCCESS_ADD)
                            .with("player", targetFormatted)
                            .with("group_display", targetRole.getDisplayName())
                            .with("group_name", targetRole.getName())
                            .with("duration_msg", durationMsg));
                    break;
                case SET:
                    roleService.setRole(uuid, roleName, finalDuration, sourceName);
                    Messages.send(sender, Message.of(MessageKey.ROLE_SUCCESS_SET)
                            .with("player", targetFormatted)
                            .with("group_display", targetRole.getDisplayName())
                            .with("group_name", targetRole.getName())
                            .with("duration_msg", durationMsg));
                    break;
                case REMOVE:
                    roleService.removeRole(uuid, roleName, sourceName);
                    Messages.send(sender, Message.of(MessageKey.ROLE_SUCCESS_REMOVE)
                            .with("player", targetFormatted)
                            .with("group_display", targetRole.getDisplayName())
                            .with("group_name", targetRole.getName()));
                    break;
            }
            playSound(sender, SoundKeys.SUCCESS);

            if (!hiddenArg && (type == RoleModificationType.ADD || type == RoleModificationType.SET)) {
                publishRoleBroadcast(uuid, profile.getName(), targetRole);
            }

            if (proxyServer.getPlayer(uuid).isPresent()) {
                RoleKickHandler.KickReason reason = (type == RoleModificationType.REMOVE)
                        ? RoleKickHandler.KickReason.REMOVED
                        : RoleKickHandler.KickReason.ADD_SET;
                RoleType kickType = targetRole.getType();
                RoleKickHandler.scheduleKick(uuid, kickType, reason, targetRole.getDisplayName());
            }

        }).exceptionally(ex -> {
            logger.log(Level.SEVERE, "Erro ao modificar cargo", ex);
            Messages.send(sender, MessageKey.COMMAND_ERROR);
            return null;
        });
    }

    private void publishRoleBroadcast(UUID playerUuid, String playerName, Role newRole) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("playerUuid", playerUuid.toString());
            node.put("playerName", playerName);
            node.put("playerColor", newRole.getColor());
            node.put("groupDisplay", newRole.getDisplayName());
            String jsonMessage = node.toString();
            RedisPublisher.publish(RedisChannel.ROLE_BROADCAST, jsonMessage);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao enviar broadcast de role", e);
        }
    }

    private void handleClear(CommandSource sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, label, "clear <jogador>");
            return;
        }
        String targetName = args[1];
        final String sourceName = (sender instanceof Player) ? ((Player) sender).getUsername() : "Console";

        resolveProfileAsync(targetName).thenCompose(opt -> {
            if (opt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetName));
                return CompletableFuture.completedFuture(null);
            }
            Profile profile = opt.get();
            return roleService.loadPlayerDataAsync(profile.getUuid())
                    .thenApply(data -> new AbstractMap.SimpleImmutableEntry<>(profile, data));

        }).thenAccept(entry -> {
            if (entry == null) return;
            Profile profile = entry.getKey();
            PlayerSessionData data = entry.getValue();
            UUID uuid = profile.getUuid();

            if (!checkHierarchy(sender, data.getPrimaryRole())) {
                Messages.send(sender, MessageKey.ROLE_ERROR_CLEAR_SUPERIOR);
                return;
            }

            roleService.clearRoles(uuid, sourceName);
            String formatted = NicknameFormatter.getFullFormattedNick(uuid);
            Messages.send(sender, Message.of(MessageKey.ROLE_SUCCESS_CLEAR_PLAYER).with("player", formatted));
            playSound(sender, SoundKeys.SUCCESS);

            if (proxyServer.getPlayer(uuid).isPresent()) {
                RoleKickHandler.scheduleKick(uuid, RoleType.DEFAULT, RoleKickHandler.KickReason.REMOVED, "grupos");
            }
        });
    }

    private void handleList(CommandSource sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, label, "list <grupo>");
            return;
        }
        String groupName = args[1].toLowerCase();
        Optional<Role> roleOpt = roleService.getRole(groupName);

        if (roleOpt.isEmpty()) {
            Messages.send(sender, Message.of(MessageKey.ROLE_ERROR_GROUP_NOT_FOUND).with("group", groupName));
            return;
        }

        Role role = roleOpt.get();
        Messages.send(sender, Message.of(MessageKey.ROLE_LIST_IN_PROGRESS).with("group_display", role.getDisplayName()));

        CompletableFuture.supplyAsync(() -> profileService.findByActiveRoleName(groupName), TaskScheduler.getAsyncExecutor())
                .thenAccept(profiles -> {
                    Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER)
                            .with("key", "Membros de " + role.getDisplayName())
                            .with("count", profiles.size()));

                    int i = 1;
                    for (Profile p : profiles) {
                        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM)
                                .with("index", i++)
                                .with("value", p.getName()));
                    }
                    playSound(sender, SoundKeys.NOTIFICATION);
                });
    }

    private boolean checkHierarchy(CommandSource sender, Role targetRole) {
        if (sender instanceof ConsoleCommandSource) return true;
        if (sender.hasPermission("controller.admin.bypass")) return true;
        if (!(sender instanceof Player)) return true;

        UUID senderUuid = ((Player) sender).getUniqueId();
        Optional<PlayerSessionData> senderData = roleService.getSessionDataFromCache(senderUuid);

        if (senderData.isEmpty()) return false;
        return senderData.get().getPrimaryRole().getWeight() > targetRole.getWeight();
    }

    private void showHelp(CommandSource sender, String label) {
        Locale locale = Messages.determineLocale(sender);
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Roles"));
        sendHelpLine(sender, label, "info <jogador|grupo|id:log>", MessageKey.ROLE_HELP_INFO, locale);
        sendHelpLine(sender, label, "history <jogador>", MessageKey.ROLE_HELP_INFO, locale);
        sendHelpLine(sender, label, "list <grupo>", MessageKey.ROLE_HELP_LIST, locale);
        sendHelpLine(sender, label, "add <jogador> <grupo> [tempo] [-hidden]", MessageKey.ROLE_HELP_ADD, locale);
        sendHelpLine(sender, label, "set <jogador> <grupo> [tempo] [-hidden]", MessageKey.ROLE_HELP_SET, locale);
        sendHelpLine(sender, label, "remove <jogador> <grupo>", MessageKey.ROLE_HELP_REMOVE, locale);
        sendHelpLine(sender, label, "clear <jogador>", MessageKey.ROLE_HELP_CLEAR, locale);
        Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_FULL);
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void sendHelpLine(CommandSource sender, String label, String args, MessageKey descriptionKey, Locale locale) {
        String description = Messages.translate(descriptionKey, locale);
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " " + args).with("description", description));
    }

    private void sendUsage(CommandSource sender, String label, String usage) {
        Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/" + label + " " + usage));
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player) {
            soundPlayerOpt.ifPresent(sp -> sp.playSound(sender, key));
        }
    }

    private CompletableFuture<Optional<Profile>> resolveProfileAsync(String input) {
        return CompletableFuture.supplyAsync(() -> ProfileResolver.resolve(input), TaskScheduler.getAsyncExecutor());
    }

    @Override
    public List<String> tabComplete(CommandSource sender, String[] args) {
        if (!sender.hasPermission(requiredPermission)) return Collections.emptyList();
        List<String> completions = new ArrayList<>();
        String current = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            completions.addAll(Arrays.asList("info", "history", "list", "add", "set", "remove", "clear", "help"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList("info", "history", "add", "set", "remove", "clear").contains(sub)) {
                proxyServer.getAllPlayers().stream().map(Player::getUsername).forEach(completions::add);
            }
            if (Arrays.asList("list", "info").contains(sub)) {
                roleService.getAllCachedRoles().stream().map(Role::getName).forEach(completions::add);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (Arrays.asList("add", "set", "remove").contains(sub)) {
                roleService.getAllCachedRoles().stream().map(Role::getName).forEach(completions::add);
            }
        } else if (args.length == 4) {
            if (Arrays.asList("add", "set").contains(args[0].toLowerCase())) {
                completions.addAll(Arrays.asList("30d", "7d", "1d", "12h", "1h"));
            }
        }
        return completions.stream().filter(s -> s.startsWith(current)).sorted().collect(Collectors.toList());
    }
}