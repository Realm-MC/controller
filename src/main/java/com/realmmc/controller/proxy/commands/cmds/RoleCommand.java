package com.realmmc.controller.proxy.commands.cmds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.proxy.commands.CommandInterface;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.auth.AuthenticationGuard;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.messaging.RawMessage;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileResolver;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.PlayerRole;
import com.realmmc.controller.shared.role.Role;
import com.realmmc.controller.shared.role.RoleKickHandler;
import com.realmmc.controller.shared.role.RoleType;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.utils.NicknameFormatter;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.realmmc.controller.shared.utils.TimeUtils;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Cmd(cmd = "role", aliases = {"group", "rank"}, onlyPlayer = false)
public class RoleCommand implements CommandInterface {

    private final String requiredPermission = "controller.manager";
    private final String requiredGroupName = "Gerente";
    private final RoleService roleService;
    private final ProfileService profileService;
    private final PreferencesService preferencesService;
    private final Optional<SoundPlayer> soundPlayerOpt;
    private final Logger logger;
    private final ProxyServer proxyServer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<UUID, Object> userLocks = new ConcurrentHashMap<>();

    public RoleCommand() {
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.preferencesService = ServiceRegistry.getInstance().requireService(PreferencesService.class);
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        this.logger = Proxy.getInstance().getLogger();
    }

    private Object getLock(UUID uuid) {
        return userLocks.computeIfAbsent(uuid, k -> new Object());
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

        final CommandSource finalSender = sender;
        final String finalLabel = label;
        final String[] finalArgs = args;
        String subCommand = finalArgs[0].toLowerCase();

        switch (subCommand) {
            case "info":
                handleInfo(finalSender, finalArgs, finalLabel);
                break;
            case "add":
                modifyPlayerRole(finalSender, finalArgs, finalLabel, RoleModificationType.ADD);
                break;
            case "remove":
                modifyPlayerRole(finalSender, finalArgs, finalLabel, RoleModificationType.REMOVE);
                break;
            case "set":
                modifyPlayerRole(finalSender, finalArgs, finalLabel, RoleModificationType.SET);
                break;
            case "clear":
                handleClear(finalSender, finalArgs, finalLabel);
                break;
            case "list":
                handleList(finalSender, finalArgs, finalLabel);
                break;
            default:
                sendUsage(finalSender, finalLabel, "help");
                break;
        }
    }

    private void handleInfo(CommandSource sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, label, "info <jogador | grupo>");
            return;
        }
        final String targetInput = args[1];
        final CommandSource finalSender = sender;

        Optional<Role> roleOpt = roleService.getRole(targetInput);
        if (roleOpt.isPresent()) {
            displayGroupInfo(finalSender, roleOpt.get());
        } else {
            displayPlayerInfo(finalSender, targetInput);
        }
    }

    private void displayPlayerInfo(CommandSource sender, String targetInput) {
        final CommandSource finalSender = sender;
        final String finalTargetInput = targetInput;
        final Locale senderLocale = Messages.determineLocale(finalSender);

        resolveProfileAsync(finalTargetInput).thenComposeAsync(targetProfileOpt -> {
            if (targetProfileOpt.isEmpty()) {
                Messages.send(finalSender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", finalTargetInput));
                playSound(finalSender, SoundKeys.ERROR);
                return CompletableFuture.failedFuture(new NoSuchElementException("Player not found: " + finalTargetInput));
            }

            final Profile targetProfile = targetProfileOpt.get();
            final UUID targetUuid = targetProfile.getUuid();

            return roleService.loadPlayerDataAsync(targetUuid).thenApply(sessionData -> new AbstractMap.SimpleImmutableEntry<>(targetProfile, sessionData));

        }, roleService.getAsyncExecutor()).thenAcceptAsync(entry -> {

            final Profile targetProfile = entry.getKey();
            PlayerSessionData sessionData = entry.getValue();
            final UUID targetUuid = targetProfile.getUuid();

            if (sessionData == null) {
                sessionData = roleService.getSessionDataFromCache(targetUuid)
                        .orElseGet(() -> {
                            logger.log(Level.SEVERE, "[RoleCommand] SessionData null for {0}. Using default.", targetUuid);
                            return roleService.getDefaultSessionData(targetUuid);
                        });
            }

            String formattedNick = NicknameFormatter.getNickname(targetUuid, true, targetProfile.getName());

            Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "Roles de " + formattedNick));

            Role primary = sessionData.getPrimaryRole();
            String primaryKey = Messages.translate(MessageKey.ROLE_INFO_PRIMARY_ACTIVE, senderLocale);
            String primaryValue = primary.getDisplayName() + " (" + primary.getName() + ")";
            Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LINE)
                    .with("key", primaryKey)
                    .with("value", primaryValue)
            );

            Profile latestProfileForInfo = profileService.getByUuid(targetUuid).orElse(targetProfile);
            List<PlayerRole> rolesToShow = latestProfileForInfo.getRoles() != null ? latestProfileForInfo.getRoles() : Collections.emptyList();

            String historyHeader = Messages.translate(MessageKey.ROLE_INFO_HISTORY_HEADER, senderLocale);
            Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER)
                    .with("key", historyHeader)
                    .with("count", rolesToShow.size())
            );

            if (rolesToShow.isEmpty()) {
                Messages.send(finalSender, MessageKey.COMMON_INFO_LIST_EMPTY);
            } else {
                int index = 1;
                List<PlayerRole> sortedRoles = new ArrayList<>(rolesToShow);
                sortedRoles.sort(Comparator.<PlayerRole, Integer>comparing(pr -> roleService.getRole(pr.getRoleName()).map(Role::getWeight).orElse(-1)).reversed()
                        .thenComparing(pr -> pr.getStatus() == PlayerRole.Status.ACTIVE ? 0 : (pr.getStatus() == PlayerRole.Status.EXPIRED ? 1 : 2))
                        .thenComparing(PlayerRole::getRoleName));

                for (PlayerRole pr : sortedRoles) {
                    if (pr == null) continue;
                    String roleName = pr.getRoleName();
                    String displayName = roleService.getRole(roleName).map(Role::getDisplayName).orElse(roleName);
                    String statusString;
                    MessageKey statusKey;
                    Map<String, Object> statusPlaceholders = new HashMap<>();
                    switch (pr.getStatus()) {
                        case ACTIVE:
                            if (pr.isPaused()) {
                                statusKey = MessageKey.ROLE_INFO_STATUS_PAUSED;
                                String remaining = (pr.getPausedTimeRemaining() != null && pr.getPausedTimeRemaining() > 0) ? TimeUtils.formatDuration(pr.getPausedTimeRemaining()) : Messages.translate(MessageKey.ROLE_INFO_STATUS_PERMANENT, senderLocale);
                                statusPlaceholders.put("remaining_time", remaining);
                            } else if (pr.hasExpiredTime()) {
                                statusKey = MessageKey.ROLE_INFO_STATUS_EXPIRED;
                            } else if (!pr.isPermanent()) {
                                statusKey = MessageKey.ROLE_INFO_STATUS_TEMPORARY;
                                long rem = pr.getExpiresAt() - System.currentTimeMillis();
                                statusPlaceholders.put("remaining_time", TimeUtils.formatDuration(Math.max(0, rem)));
                            } else {
                                statusKey = MessageKey.ROLE_INFO_STATUS_PERMANENT;
                            }
                            break;
                        case EXPIRED:
                            statusKey = MessageKey.ROLE_INFO_STATUS_EXPIRED;
                            break;
                        case REMOVED:
                            if (pr.getRemovedAt() != null) {
                                statusKey = MessageKey.ROLE_INFO_STATUS_REMOVED_AT;
                                statusPlaceholders.put("removed_at", TimeUtils.formatDate(pr.getRemovedAt()));
                            } else {
                                statusKey = MessageKey.ROLE_INFO_STATUS_REMOVED;
                            }
                            break;
                        default:
                            statusKey = MessageKey.ROLE_INFO_STATUS_UNKNOWN;
                            break;
                    }
                    statusString = Messages.translate(Message.of(statusKey).with(statusPlaceholders), senderLocale);
                    String value = displayName + " " + statusString;
                    Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM)
                            .with("index", "<dark_gray>" + (index++) + "</dark_gray>")
                            .with("value", value));
                }
            }
            Messages.send(finalSender, "<white>");
            playSound(finalSender, SoundKeys.NOTIFICATION);

        }, roleService.getAsyncExecutor()).exceptionally(ex -> {
            if (ex.getCause() == null || !(ex.getCause() instanceof NoSuchElementException)) {
                handleCommandError(sender, "obter info jogador", ex);
            }
            return null;
        });
    }

    private void displayGroupInfo(CommandSource sender, Role role) {
        final Locale senderLocale = Messages.determineLocale(sender);

        Messages.send(sender, Message.of(MessageKey.ROLE_GROUP_INFO_HEADER).with("group_name", role.getDisplayName()));

        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_WEIGHT, senderLocale)).with("value", role.getWeight()));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_PREFIX, senderLocale)).with("value", role.getPrefix() != null && !role.getPrefix().isEmpty() ? role.getPrefix() : Messages.translate(MessageKey.ROLE_GROUP_INFO_VALUE_NONE, senderLocale)));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_SUFFIX, senderLocale)).with("value", role.getSuffix() != null && !role.getSuffix().isEmpty() ? role.getSuffix() : Messages.translate(MessageKey.ROLE_GROUP_INFO_VALUE_NONE, senderLocale)));
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_TYPE, senderLocale)).with("value", role.getType() != null ? role.getType().name() : "N/A"));

        List<String> permissions = role.getPermissions() != null ? role.getPermissions() : Collections.emptyList();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_PERMISSIONS, senderLocale)).with("count", permissions.size()));
        if (permissions.isEmpty()) {
            Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY);
        } else {
            permissions.stream().sorted().limit(20).forEach(perm -> Messages.send(sender, new RawMessage("<white>  - <gray>{permission}").placeholder("permission", perm)));
            if (permissions.size() > 20) {
                Messages.send(sender, new RawMessage("<white>  ... e <gray>{count}</gray> mais.").placeholder("count", permissions.size() - 20));
            }
        }

        List<String> inheritance = role.getInheritance() != null ? role.getInheritance() : Collections.emptyList();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_INHERITANCE, senderLocale)).with("count", inheritance.size()));
        if (inheritance.isEmpty()) {
            Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY);
        } else {
            inheritance.stream().sorted().forEach(inheritedId -> {
                String inheritedDisplay = roleService.getRole(inheritedId).map(Role::getDisplayName).orElse(inheritedId + " (não encontrado)");
                Messages.send(sender, new RawMessage("<white>  - <gray>{group}").placeholder("group", inheritedDisplay));
            });
        }

        Messages.send(sender, "<white>");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private enum RoleModificationType {ADD, REMOVE, SET}

    private void modifyPlayerRole(CommandSource sender, String[] args, String label, RoleModificationType type) {
        int minArgs = (type == RoleModificationType.REMOVE) ? 3 : 3;
        String usageArgs = "<jogador> <grupo>";
        if (type != RoleModificationType.REMOVE) usageArgs += " [duração]";
        if (type == RoleModificationType.ADD || type == RoleModificationType.SET) usageArgs += " [-hidden]";

        boolean hidden = false;
        String[] actualArgs = args;
        if ((type == RoleModificationType.ADD || type == RoleModificationType.SET) && args.length > minArgs && args[args.length - 1].equalsIgnoreCase("-hidden")) {
            hidden = true;
            actualArgs = Arrays.copyOf(args, args.length - 1);
        }

        if (actualArgs.length < minArgs) {
            sendUsage(sender, label, args[0] + " " + usageArgs);
            return;
        }

        final String targetInput = actualArgs[1];
        final String roleNameInput = actualArgs[2].toLowerCase();
        final String durationStr = (actualArgs.length > 3 && type != RoleModificationType.REMOVE) ? actualArgs[3] : null;
        final RoleModificationType finalType = type;
        final CommandSource finalSender = sender;
        final boolean finalHidden = hidden;

        Optional<Role> roleOpt = roleService.getRole(roleNameInput);
        if (roleOpt.isEmpty()) {
            Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_GROUP_NOT_FOUND).with("group", roleNameInput));
            playSound(finalSender, SoundKeys.USAGE_ERROR);
            return;
        }
        final Role targetRole = roleOpt.get();

        if (targetRole.getName().equalsIgnoreCase("default") && (finalType == RoleModificationType.REMOVE || finalType == RoleModificationType.SET)) {
            Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CANNOT_MODIFY_DEFAULT));
            playSound(finalSender, SoundKeys.USAGE_ERROR);
            return;
        }

        final Long expiresAt;
        if (finalType != RoleModificationType.REMOVE && durationStr != null) {
            long d = TimeUtils.parseDuration(durationStr);
            if (d <= 0) {
                Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_INVALID_DURATION).with("duration", durationStr));
                playSound(finalSender, SoundKeys.USAGE_ERROR);
                return;
            }
            expiresAt = System.currentTimeMillis() + d;
        } else {
            expiresAt = null;
        }

        final Optional<Role> senderRoleOpt = getSenderPrimaryRole(finalSender);
        final boolean hasBypass = checkBypass(finalSender, senderRoleOpt);
        final int senderWeight = senderRoleOpt.map(Role::getWeight).orElse(hasBypass ? Integer.MAX_VALUE : 0);

        if (!hasBypass && (finalType == RoleModificationType.ADD || finalType == RoleModificationType.SET)) {
            if (targetRole.getWeight() >= senderWeight) {
                Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CANNOT_MANAGE_SUPERIOR).with("target_group", targetRole.getDisplayName()));
                playSound(finalSender, SoundKeys.USAGE_ERROR);
                return;
            }
        }

        resolveProfileAsync(targetInput).thenAcceptAsync(targetProfileOpt -> {
            if (targetProfileOpt.isEmpty()) {
                Messages.send(finalSender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetInput));
                playSound(finalSender, SoundKeys.ERROR);
                return;
            }
            final Profile targetProfile = targetProfileOpt.get();
            final UUID targetUuid = targetProfile.getUuid();

            roleService.loadPlayerDataAsync(targetUuid).thenAcceptAsync(targetData -> {

                String targetFormattedName = NicknameFormatter.getNickname(targetUuid, true, targetProfile.getName());

                int targetWeight = targetData.getPrimaryRole().getWeight();
                if (!hasBypass && targetWeight >= senderWeight) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CLEAR_SUPERIOR).with("target_name", targetFormattedName));
                    playSound(finalSender, SoundKeys.USAGE_ERROR);
                    return;
                }

                try {
                    final AtomicBoolean changed = new AtomicBoolean(false);
                    final AtomicReference<MessageKey> successMessageKey = new AtomicReference<>(null);
                    final AtomicBoolean vipAffected = new AtomicBoolean(targetRole.getType() == RoleType.VIP);
                    final String finalPrimaryRoleName;

                    final boolean isSelf = (finalSender instanceof Player p && p.getUniqueId().equals(targetUuid));

                    synchronized (getLock(targetUuid)) {
                        final Profile latestProfile = profileService.getByUuid(targetUuid).orElseThrow(() -> new IllegalStateException("Perfil desapareceu DENTRO do lock!"));
                        List<PlayerRole> currentRoles = new ArrayList<>(latestProfile.getRoles() != null ? latestProfile.getRoles() : Collections.emptyList());

                        switch (finalType) {
                            case SET:
                                currentRoles.forEach(pr -> {
                                    if (pr != null && !pr.getRoleName().equalsIgnoreCase("default") && !pr.getRoleName().equalsIgnoreCase(roleNameInput) && pr.getStatus() == PlayerRole.Status.ACTIVE) {
                                        pr.setStatus(PlayerRole.Status.REMOVED);
                                        pr.setRemovedAt(System.currentTimeMillis());
                                        pr.setPaused(false);
                                        pr.setPausedTimeRemaining(null);
                                        changed.set(true);
                                        roleService.getRole(pr.getRoleName()).ifPresent(r -> {
                                            if (r.getType() == RoleType.VIP) vipAffected.set(true);
                                        });
                                    }
                                });
                                addOrUpdateRole(currentRoles, targetRole.getName(), expiresAt, changed, true);
                                ensureDefaultActiveIfNeeded(currentRoles, targetRole.getName());
                                successMessageKey.set(isSelf ? MessageKey.ROLE_SUCCESS_SET_SELF : MessageKey.ROLE_SUCCESS_SET);
                                logger.log(Level.INFO, "[RoleCommand:Set] Attempting to set role {0} for {1}", new Object[]{roleNameInput, targetUuid});
                                break;
                            case ADD:
                                boolean alreadyHasActivePermanent = currentRoles.stream().anyMatch(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(roleNameInput) && pr.getStatus() == PlayerRole.Status.ACTIVE && pr.isPermanent());
                                if (alreadyHasActivePermanent && expiresAt == null) {
                                    Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_ALREADY_HAS_PERMANENT).with("player", targetFormattedName).with("group_display", targetRole.getDisplayName()));
                                    playSound(finalSender, SoundKeys.NOTIFICATION);
                                    return;
                                }
                                addOrUpdateRole(currentRoles, targetRole.getName(), expiresAt, changed, false);
                                ensureDefaultActiveIfNeeded(currentRoles, targetRole.getName());
                                successMessageKey.set(isSelf ? MessageKey.ROLE_SUCCESS_ADD_SELF : MessageKey.ROLE_SUCCESS_ADD);
                                logger.log(Level.INFO, "[RoleCommand:Add] Attempting to add role {0} for {1}", new Object[]{roleNameInput, targetUuid});
                                break;
                            case REMOVE:
                                Optional<PlayerRole> activeRoleToRemove = currentRoles.stream().filter(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(roleNameInput) && pr.getStatus() == PlayerRole.Status.ACTIVE).findFirst();
                                if (activeRoleToRemove.isPresent()) {
                                    PlayerRole ex = activeRoleToRemove.get();
                                    ex.setStatus(PlayerRole.Status.REMOVED);
                                    ex.setRemovedAt(System.currentTimeMillis());
                                    ex.setPaused(false);
                                    ex.setPausedTimeRemaining(null);
                                    changed.set(true);
                                    successMessageKey.set(isSelf ? MessageKey.ROLE_SUCCESS_REMOVE_SELF : MessageKey.ROLE_SUCCESS_REMOVE);
                                    roleService.getRole(ex.getRoleName()).ifPresent(r -> {
                                        if (r.getType() == RoleType.VIP) vipAffected.set(true);
                                    });
                                    ensureDefaultActiveIfNeeded(currentRoles, "dummy");
                                } else {
                                    Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_NOT_ACTIVE).with("player", targetFormattedName).with("group_display", targetRole.getDisplayName()).with("group_name", targetRole.getName()));
                                    playSound(finalSender, SoundKeys.NOTIFICATION);
                                    return;
                                }
                                break;
                            default:
                                throw new IllegalStateException("Tipo de modificação desconhecido");
                        }

                        if (changed.get()) {
                            try {
                                roleService.updatePauseState(targetUuid, currentRoles);
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "[RoleCommand] Error in updatePauseState", e);
                            }
                            latestProfile.setRoles(currentRoles);

                            Role calculatedPrimaryRole = calculatePrimaryRole(currentRoles);
                            if (calculatedPrimaryRole != null) {
                                finalPrimaryRoleName = calculatedPrimaryRole.getName();
                                if (!Objects.equals(latestProfile.getPrimaryRoleName(), finalPrimaryRoleName)) {
                                    latestProfile.setPrimaryRoleName(finalPrimaryRoleName);
                                    logger.log(Level.INFO, "[RoleCommand:{0}] PrimaryRoleName updated to '{1}' on profile before save.", new Object[]{finalType, finalPrimaryRoleName});
                                } else {
                                    logger.log(Level.FINEST, "[RoleCommand:{0}] PrimaryRoleName ('{1}') did not need to be updated on profile.", new Object[]{finalType, finalPrimaryRoleName});
                                }
                            } else {
                                logger.log(Level.SEVERE, "[RoleCommand:{0}] CRITICAL ERROR: Could not calculate primary group for {1} before save!", new Object[]{finalType, targetUuid});
                                latestProfile.setPrimaryRoleName("default");
                                finalPrimaryRoleName = "default";
                            }

                            try {
                                profileService.save(latestProfile);
                                logger.log(Level.INFO, "[RoleCommand:{0}] Profile saved successfully for {1}. PrimaryRoleName saved: {2}", new Object[]{finalType, targetUuid, latestProfile.getPrimaryRoleName()});
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "[RoleCommand:{0}] FAILED TO SAVE PROFILE for {1}");
                                handleCommandError(finalSender, "salvar perfil", e);
                                return;
                            }

                            roleService.publishSync(targetUuid);
                            logger.log(Level.INFO, "[RoleCommand:{0}] ROLE_SYNC published for {1}", new Object[]{finalType, targetUuid});

                            String durationMsg;
                            Locale senderLocale = Messages.determineLocale(finalSender);
                            if (finalType != RoleModificationType.REMOVE) {
                                String durKey = (expiresAt == null) ? Messages.translate(MessageKey.ROLE_INFO_STATUS_PERMANENT, senderLocale) : Messages.translate(Message.of(MessageKey.ROLE_INFO_STATUS_TEMPORARY).with("remaining_time", TimeUtils.formatDuration(expiresAt - System.currentTimeMillis())), senderLocale);
                                durationMsg = durKey.replaceAll("<[^>]*>", "");
                            } else {
                                durationMsg = "";
                            }

                            if (successMessageKey.get() != null) {
                                Messages.send(finalSender, Message.of(successMessageKey.get()).with("group_display", targetRole.getDisplayName()).with("group_name", targetRole.getName()).with("player", targetFormattedName).with("duration_msg", durationMsg));
                            }
                            playSound(finalSender, SoundKeys.SUCCESS);

                            if ((finalType == RoleModificationType.ADD || finalType == RoleModificationType.SET) && targetRole.getType() == RoleType.STAFF) {
                                preferencesService.getPreferences(targetUuid).ifPresent(prefs -> {
                                    if (!prefs.isStaffChatEnabled()) {
                                        prefs.setStaffChatEnabled(true);
                                        preferencesService.save(prefs);
                                        logger.info("[RoleCommand] Preferência StaffChat ativada automaticamente para " + targetUuid);
                                    }
                                });
                            }

                            boolean isTargetOnline = proxyServer.getPlayer(targetUuid).isPresent();
                            if (!finalHidden && (finalType == RoleModificationType.ADD || finalType == RoleModificationType.SET)) {
                                publishRoleBroadcast(targetUuid, targetProfile.getName(), targetRole);
                            }

                            if (isTargetOnline && AuthenticationGuard.isAuthenticated(targetUuid)) {
                                RoleKickHandler.KickReason reason = (finalType == RoleModificationType.ADD || finalType == RoleModificationType.SET) ? RoleKickHandler.KickReason.ADD_SET : RoleKickHandler.KickReason.REMOVED;
                                RoleKickHandler.scheduleKick(targetUuid, targetRole.getType(), reason, targetRole.getDisplayName());
                                logger.log(Level.INFO, "[RoleCommand:{0}] Kick scheduled for {1} (state ONLINE).", new Object[]{finalType, targetUuid});
                            } else if (isTargetOnline) {
                                logger.log(Level.FINE, "[RoleCommand:{0}] Kick for {1} skipped, player is in CONNECTING state.", new Object[]{finalType, targetUuid});
                            }

                        } else {
                            logger.log(Level.FINE, "[RoleCommand:{0}] No changes detected for {1} and role {2}. No action taken.", new Object[]{finalType, targetUuid, roleNameInput});
                        }
                    }
                } catch (Exception e) {
                    handleCommandError(finalSender, "processar " + finalType, e);
                }
            }, roleService.getAsyncExecutor());
        }).exceptionally(ex -> handleCommandError(sender, "resolver perfil", ex));
    }

    private void addOrUpdateRole(List<PlayerRole> currentRoles, String roleName, Long expiresAt, AtomicBoolean changed, boolean isSet) {
        Optional<PlayerRole> existingActive = currentRoles.stream().filter(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(roleName) && pr.getStatus() == PlayerRole.Status.ACTIVE).findFirst();
        if (existingActive.isPresent()) {
            PlayerRole pr = existingActive.get();
            boolean expiresChanged = !Objects.equals(pr.getExpiresAt(), expiresAt);
            boolean pauseCleared = pr.isPaused() || pr.getPausedTimeRemaining() != null;
            if (expiresChanged || pauseCleared) {
                pr.setExpiresAt(expiresAt);
                if (pauseCleared) {
                    pr.setPaused(false);
                    pr.setPausedTimeRemaining(null);
                }
                changed.set(true);
                logger.finest("[RoleCommand:AddOrUpdate] Existing ACTIVE role '" + roleName + "' updated.");
            } else {
                logger.finest("[RoleCommand:AddOrUpdate] ACTIVE role '" + roleName + "' was already in the desired state.");
            }
        } else {
            currentRoles.add(PlayerRole.builder().roleName(roleName).expiresAt(expiresAt).status(PlayerRole.Status.ACTIVE).paused(false).addedAt(System.currentTimeMillis()).build());
            changed.set(true);
            logger.finest("[RoleCommand:AddOrUpdate] No ACTIVE role '" + roleName + "' found. Adding NEW PlayerRole.");
        }
    }

    private void ensureDefaultActiveIfNeeded(List<PlayerRole> currentRoles, String modifiedRoleName) {
        boolean otherRoleIsActive = currentRoles.stream().anyMatch(pr -> pr != null && pr.getStatus() == PlayerRole.Status.ACTIVE && !"default".equalsIgnoreCase(pr.getRoleName()));
        Optional<PlayerRole> defaultRoleOpt = currentRoles.stream().filter(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName())).findFirst();
        if (defaultRoleOpt.isPresent()) {
            PlayerRole defaultRole = defaultRoleOpt.get();
            if (!otherRoleIsActive && defaultRole.getStatus() != PlayerRole.Status.ACTIVE) {
                defaultRole.setStatus(PlayerRole.Status.ACTIVE);
                defaultRole.setRemovedAt(null);
                defaultRole.setPaused(false);
                defaultRole.setPausedTimeRemaining(null);
                logger.finest("[RoleCommand:Default] Existing 'default' role reactivated.");
            } else if (otherRoleIsActive && defaultRole.getStatus() == PlayerRole.Status.REMOVED) {
                defaultRole.setStatus(PlayerRole.Status.ACTIVE);
                defaultRole.setRemovedAt(null);
                defaultRole.setPaused(false);
                defaultRole.setPausedTimeRemaining(null);
                logger.warning("[RoleCommand:Default] 'default' role was REMOVED while another role was active. Reactivating 'default'.");
            }
        } else if (!otherRoleIsActive) {
            currentRoles.add(PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build());
            logger.finest("[RoleCommand:Default] 'default' role not found, added as ACTIVE.");
        }
    }

    private Role calculatePrimaryRole(List<PlayerRole> currentRoles) {
        return currentRoles.stream().filter(pr -> pr != null && pr.isActive()).map(pr -> roleService.getRole(pr.getRoleName())).filter(Optional::isPresent).map(Optional::get).max(Comparator.comparingInt(Role::getWeight)).orElse(roleService.getRole("default").orElse(null));
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

            logger.log(Level.FINE, "[RoleCommand:Broadcast] ROLE_BROADCAST message published for {0}", playerName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[RoleCommand:Broadcast] Failed to serialize/publish ROLE_BROADCAST message for " + playerName, e);
        }
    }

    private void handleClear(CommandSource sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, label, "clear <jogador | grupo>");
            return;
        }
        final String targetInput = args[1];
        final CommandSource finalSender = sender;

        final Optional<Role> senderRoleOpt = getSenderPrimaryRole(finalSender);
        final boolean hasBypass = checkBypass(finalSender, senderRoleOpt);
        final int senderWeight = senderRoleOpt.map(Role::getWeight).orElse(hasBypass ? Integer.MAX_VALUE : 0);

        TaskScheduler.runAsync(() -> {
            Optional<Role> roleOpt = roleService.getRole(targetInput);

            if (roleOpt.isPresent()) {
                final Role targetRole = roleOpt.get();
                final String targetRoleName = targetRole.getName();
                final String targetRoleDisplay = targetRole.getDisplayName();

                if (targetRoleName.equalsIgnoreCase("default")) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CANNOT_CLEAR_GROUP).with("group_name", targetRoleName));
                    playSound(finalSender, SoundKeys.USAGE_ERROR);
                    return;
                }

                if (!hasBypass && targetRole.getWeight() >= senderWeight) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CANNOT_MANAGE_SUPERIOR).with("target_group", targetRoleDisplay));
                    playSound(finalSender, SoundKeys.USAGE_ERROR);
                    return;
                }

                Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_CLEAR_GROUP_IN_PROGRESS).with("group_display", targetRoleDisplay));

                List<Profile> profilesToClear = profileService.findByActiveRoleName(targetRoleName);

                if (profilesToClear.isEmpty()) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_CLEAR_GROUP_NO_ELIGIBLE).with("group_display", targetRoleDisplay));
                    playSound(finalSender, SoundKeys.NOTIFICATION);
                    return;
                }

                final AtomicInteger successCount = new AtomicInteger(0);
                final AtomicInteger failCount = new AtomicInteger(0);

                for (Profile profile : profilesToClear) {
                    final UUID targetUuid = profile.getUuid();
                    try {
                        PlayerSessionData targetData = roleService.loadPlayerDataAsync(targetUuid).join();
                        int targetWeight = targetData.getPrimaryRole().getWeight();

                        if (!hasBypass && targetWeight >= senderWeight && !(finalSender instanceof ConsoleCommandSource)) {
                            logger.finer("[RoleCommand:ClearGroup] Skipping " + profile.getName() + " (superior weight)");
                            failCount.incrementAndGet();
                            continue;
                        }

                        synchronized (getLock(targetUuid)) {
                            final Profile latestProfile = profileService.getByUuid(targetUuid).orElse(null);
                            if (latestProfile == null) continue;

                            List<PlayerRole> currentRoles = latestProfile.getRoles();
                            if (currentRoles == null) continue;

                            Optional<PlayerRole> roleToRemove = currentRoles.stream()
                                    .filter(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(targetRoleName) && pr.getStatus() == PlayerRole.Status.ACTIVE)
                                    .findFirst();

                            if (roleToRemove.isPresent()) {
                                PlayerRole pr = roleToRemove.get();
                                pr.setStatus(PlayerRole.Status.REMOVED);
                                pr.setRemovedAt(System.currentTimeMillis());
                                pr.setPaused(false);
                                pr.setPausedTimeRemaining(null);

                                ensureDefaultActiveIfNeeded(currentRoles, "dummy");
                                Role newPrimary = calculatePrimaryRole(currentRoles);
                                latestProfile.setPrimaryRoleName(newPrimary.getName());
                                try {
                                    roleService.updatePauseState(targetUuid, currentRoles);
                                } catch (Exception e) {
                                    logger.log(Level.WARNING, "[RoleCommand:ClearGroup] Error updatePauseState", e);
                                }
                                latestProfile.setRoles(currentRoles);

                                profileService.save(latestProfile);
                                roleService.publishSync(targetUuid);
                                successCount.incrementAndGet();

                                boolean isTargetOnline = proxyServer.getPlayer(targetUuid).isPresent();
                                if (isTargetOnline && AuthenticationGuard.isAuthenticated(targetUuid)) {
                                    RoleKickHandler.scheduleKick(targetUuid, targetRole.getType(), RoleKickHandler.KickReason.REMOVED, targetRole.getDisplayName());
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "[RoleCommand:ClearGroup] Failed to clear role for " + targetUuid, e);
                        failCount.incrementAndGet();
                    }
                }

                Messages.send(finalSender, Message.of(MessageKey.ROLE_SUCCESS_CLEAR_GROUP).with("count", successCount.get()).with("group_name", targetRoleName));
                if (failCount.get() > 0) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_CLEAR_GROUP_FAIL_COUNT).with("count", failCount.get()));
                }
                playSound(finalSender, SoundKeys.SUCCESS);
            } else {
                clearRolesByPlayer(finalSender, targetInput, label, senderRoleOpt, hasBypass, senderWeight);
            }
        });
    }

    private void clearRolesByPlayer(CommandSource sender, String targetInput, String label, Optional<Role> senderRoleOpt, boolean hasBypass, int senderWeight) {
        final CommandSource finalSender = sender;
        final String finalTargetInput = targetInput;

        resolveProfileAsync(finalTargetInput).thenAcceptAsync(targetProfileOpt -> {
            if (targetProfileOpt.isEmpty()) {
                Messages.send(finalSender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", finalTargetInput));
                playSound(finalSender, SoundKeys.USAGE_ERROR);
                return;
            }
            final Profile targetProfile = targetProfileOpt.get();
            final UUID targetUuid = targetProfile.getUuid();

            String targetFormattedName = NicknameFormatter.getNickname(targetUuid, true, targetProfile.getName());

            if (sender instanceof Player p && p.getUniqueId().equals(targetUuid)) {
                Messages.send(sender, MessageKey.ROLE_ERROR_CANNOT_CLEAR_SELF);
                playSound(sender, SoundKeys.USAGE_ERROR);
                return;
            }

            roleService.loadPlayerDataAsync(targetUuid).thenAcceptAsync(currentTargetData -> {
                int targetMaxWeight = currentTargetData.getPrimaryRole().getWeight();
                if (!hasBypass && targetMaxWeight >= senderWeight) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CLEAR_SUPERIOR).with("target_name", targetFormattedName));
                    playSound(finalSender, SoundKeys.USAGE_ERROR);
                    return;
                }

                try {
                    final AtomicBoolean changed = new AtomicBoolean(false);
                    final AtomicBoolean vipRemoved = new AtomicBoolean(false);
                    final AtomicReference<Role> lastRemovedRole = new AtomicReference<>(null);
                    final boolean isSelf = (finalSender instanceof Player p && p.getUniqueId().equals(targetUuid));

                    synchronized (getLock(targetUuid)) {
                        final Profile latestProfile = profileService.getByUuid(targetUuid).orElseThrow(() -> new IllegalStateException("Profile disappeared during clearRolesByPlayer"));
                        List<PlayerRole> currentRoles = latestProfile.getRoles();
                        if (currentRoles == null) currentRoles = new ArrayList<>();
                        for (PlayerRole pr : currentRoles) {
                            if (pr != null && !"default".equalsIgnoreCase(pr.getRoleName()) && pr.getStatus() == PlayerRole.Status.ACTIVE) {
                                pr.setStatus(PlayerRole.Status.REMOVED);
                                pr.setRemovedAt(System.currentTimeMillis());
                                pr.setPaused(false);
                                pr.setPausedTimeRemaining(null);
                                changed.set(true);
                                roleService.getRole(pr.getRoleName()).ifPresent(r -> {
                                    lastRemovedRole.set(r);
                                    if (r.getType() == RoleType.VIP) vipRemoved.set(true);
                                });
                            }
                        }
                        ensureDefaultActiveIfNeeded(currentRoles, "dummy");
                        if (!changed.get()) {
                            boolean defaultReactivated = currentRoles.stream().anyMatch(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()) && pr.getStatus() == PlayerRole.Status.ACTIVE);
                            if (!defaultReactivated) {
                                Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_ALREADY_DEFAULT).with("player", targetFormattedName));
                                playSound(finalSender, SoundKeys.NOTIFICATION);
                                return;
                            }
                        }

                        Role calculatedPrimaryRole = calculatePrimaryRole(currentRoles);
                        if (calculatedPrimaryRole != null) {
                            String newPrimaryName = calculatedPrimaryRole.getName();
                            if (!Objects.equals(latestProfile.getPrimaryRoleName(), newPrimaryName)) {
                                latestProfile.setPrimaryRoleName(newPrimaryName);
                            }
                        } else {
                            latestProfile.setPrimaryRoleName("default");
                        }
                        try {
                            roleService.updatePauseState(targetUuid, currentRoles);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "[RoleCommand:ClearPlayer] Error updatePauseState", e);
                        }
                        latestProfile.setRoles(currentRoles);
                        try {
                            profileService.save(latestProfile);
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "[RoleCommand:ClearPlayer] FAILED TO SAVE PROFILE after clear", e);
                            handleCommandError(finalSender, "salvar clear", e);
                            return;
                        }
                        roleService.publishSync(targetUuid);
                        logger.log(Level.INFO, "[RoleCommand:ClearPlayer] ROLE_SYNC published for {0}", targetUuid);

                        Messages.send(finalSender, Message.of(isSelf ? MessageKey.ROLE_SUCCESS_CLEAR_PLAYER_SELF : MessageKey.ROLE_SUCCESS_CLEAR_PLAYER).with("player", targetFormattedName));
                        playSound(finalSender, SoundKeys.SUCCESS);

                        boolean isTargetOnline = proxyServer.getPlayer(targetUuid).isPresent();
                        if (isTargetOnline && AuthenticationGuard.isAuthenticated(targetUuid)) {
                            RoleType typeForKick = vipRemoved.get() ? RoleType.VIP : (lastRemovedRole.get() != null ? lastRemovedRole.get().getType() : RoleType.DEFAULT);
                            String displayForKick = lastRemovedRole.get() != null ? lastRemovedRole.get().getDisplayName() : "grupo";
                            RoleKickHandler.scheduleKick(targetUuid, typeForKick, RoleKickHandler.KickReason.REMOVED, displayForKick);
                            logger.log(Level.INFO, "[RoleCommand:ClearPlayer] Kick scheduled for {0} (state ONLINE).", targetUuid);
                        } else if (isTargetOnline) {
                            logger.log(Level.FINE, "[RoleCommand:ClearPlayer] Kick for {0} skipped, player is in CONNECTING state.", targetUuid);
                        }
                    }
                } catch (Exception e) {
                    handleCommandError(finalSender, "processar clear player", e);
                }
            }, roleService.getAsyncExecutor());
        }).exceptionally(ex -> handleCommandError(sender, "resolver perfil clear player", ex));
    }


    private void handleList(CommandSource sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, label, "list <grupo>");
            return;
        }
        final String groupNameInput = args[1].toLowerCase();
        final CommandSource finalSender = sender;
        final Locale senderLocale = Messages.determineLocale(sender);

        Optional<Role> roleOpt = roleService.getRole(groupNameInput);
        if (roleOpt.isEmpty()) {
            Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_GROUP_NOT_FOUND).with("group", groupNameInput));
            playSound(finalSender, SoundKeys.USAGE_ERROR);
            return;
        }

        final Role targetRole = roleOpt.get();
        final String targetRoleDisplayName = targetRole.getDisplayName();

        Messages.send(finalSender, Message.of(MessageKey.ROLE_LIST_IN_PROGRESS).with("group_display", targetRoleDisplayName));
        playSound(finalSender, SoundKeys.NOTIFICATION);

        CompletableFuture.supplyAsync(() -> profileService.findByActiveRoleName(targetRole.getName()), roleService.getAsyncExecutor())
                .thenAcceptAsync(profilesWithRole -> {
                    Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER)
                            .with("key", "Jogadores Ativos no Grupo " + targetRoleDisplayName)
                            .with("count", profilesWithRole.size()));

                    if (profilesWithRole.isEmpty()) {
                        Messages.send(finalSender, MessageKey.COMMON_INFO_LIST_EMPTY);
                    } else {
                        int index = 1;
                        profilesWithRole.sort(Comparator.comparing(Profile::getName, String.CASE_INSENSITIVE_ORDER));

                        for (Profile profile : profilesWithRole) {
                            String listName = targetRole.getColor() + profile.getName() + "<reset>";
                            PlayerRole roleDetails = profile.getRoles().stream()
                                    .filter(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(targetRole.getName()) && pr.getStatus() == PlayerRole.Status.ACTIVE)
                                    .findFirst()
                                    .orElse(null);

                            String statusString = "";
                            if (roleDetails != null) {
                                MessageKey statusKey;
                                Map<String, Object> ph = new HashMap<>();
                                if (roleDetails.isPaused()) {
                                    statusKey = MessageKey.ROLE_INFO_STATUS_PAUSED;
                                    String remaining = (roleDetails.getPausedTimeRemaining() != null && roleDetails.getPausedTimeRemaining() > 0) ? TimeUtils.formatDuration(roleDetails.getPausedTimeRemaining()) : Messages.translate(MessageKey.ROLE_INFO_STATUS_PERMANENT, senderLocale);
                                    ph.put("remaining_time", remaining);
                                } else if (roleDetails.hasExpiredTime()) {
                                    statusKey = MessageKey.ROLE_INFO_STATUS_EXPIRED;
                                } else if (!roleDetails.isPermanent()) {
                                    statusKey = MessageKey.ROLE_INFO_STATUS_TEMPORARY;
                                    long rem = roleDetails.getExpiresAt() - System.currentTimeMillis();
                                    ph.put("remaining_time", TimeUtils.formatDuration(Math.max(0, rem)));
                                } else {
                                    statusKey = MessageKey.ROLE_INFO_STATUS_PERMANENT;
                                }
                                statusString = Messages.translate(Message.of(statusKey).with(ph), senderLocale);
                            } else {
                                statusString = Messages.translate(MessageKey.ROLE_INFO_STATUS_UNKNOWN, senderLocale);
                            }

                            Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM)
                                    .with("index", "<dark_gray>" + (index++) + "</dark_gray>")
                                    .with("value", listName + " " + statusString));
                        }
                    }
                    Messages.send(finalSender, "<white>");
                    playSound(finalSender, SoundKeys.NOTIFICATION);
                }, roleService.getAsyncExecutor())
                .exceptionally(ex -> {
                    logger.log(Level.SEVERE, "Erro ao listar grupo " + targetRoleDisplayName, ex);
                    handleCommandError(sender, "listar grupo " + targetRoleDisplayName, ex);
                    return null;
                });
    }

    private void showHelp(CommandSource sender, String label) {
        final Locale senderLocale = Messages.determineLocale(sender);
        List<Map.Entry<String, MessageKey>> commands = List.of(
                Map.entry("/{label} info <jogador|grupo>", MessageKey.ROLE_HELP_INFO),
                Map.entry("/{label} list <grupo>", MessageKey.ROLE_HELP_LIST),
                Map.entry("/{label} add <jogador> <grupo> [duração] [-hidden]", MessageKey.ROLE_HELP_ADD),
                Map.entry("/{label} set <jogador> <grupo> [duração] [-hidden]", MessageKey.ROLE_HELP_SET),
                Map.entry("/{label} remove <jogador> <grupo>", MessageKey.ROLE_HELP_REMOVE),
                Map.entry("/{label} clear <jogador|grupo>", MessageKey.ROLE_HELP_CLEAR)
        );
        boolean hasOptional = commands.stream().anyMatch(e -> e.getKey().contains("["));
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Roles"));
        commands.forEach(entry -> {
            String usage = entry.getKey().replace("{label}", label);
            String description = Messages.translate(entry.getValue(), senderLocale);
            Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE)
                    .with("usage", usage)
                    .with("description", description)
            );
        });
        if (hasOptional) {
            Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_FULL);
        } else {
            Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_REQUIRED);
        }
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void sendUsage(CommandSource sender, String label, String usage) {
        Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/" + label + " " + usage));
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player player) {
            soundPlayerOpt.ifPresent(sp -> sp.playSound(player, key));
        }
    }

    private CompletableFuture<Optional<Profile>> resolveProfileAsync(String input) {
        Executor exec = roleService.getAsyncExecutor();
        if (exec == null) exec = ForkJoinPool.commonPool();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ProfileResolver.resolve(input);
            } catch (Exception e) {
                return Optional.empty();
            }
        }, exec);
    }

    private Optional<Role> getSenderPrimaryRole(CommandSource sender) {
        if (sender instanceof ConsoleCommandSource) {
            return Optional.empty();
        }
        if (sender instanceof Player p) {
            try {
                Optional<PlayerSessionData> cachedData = roleService.getSessionDataFromCache(p.getUniqueId());
                if (cachedData.isPresent()) {
                    return cachedData.map(PlayerSessionData::getPrimaryRole);
                }
                return Optional.of(roleService.loadPlayerDataAsync(p.getUniqueId()).join().getPrimaryRole());
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private boolean checkBypass(CommandSource sender, Optional<Role> senderRole) {
        if (sender instanceof ConsoleCommandSource) {
            return true;
        }
        if (sender.hasPermission("controller.admin.bypass")) {
            return true;
        }
        return senderRole.isPresent() && senderRole.get().getName().equalsIgnoreCase("master");
    }

    private Void handleCommandError(CommandSource sender, String action, Throwable t) {
        Messages.send(sender, MessageKey.COMMAND_ERROR);
        playSound(sender, SoundKeys.ERROR);
        return null;
    }

    @Override
    public List<String> tabComplete(CommandSource sender, String[] args) {
        if (!sender.hasPermission(requiredPermission)) return Collections.emptyList();
        List<String> completions = new ArrayList<>();
        String currentArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        try {
            final Optional<Role> senderRoleOpt = getSenderPrimaryRole(sender);
            final boolean hasBypass = checkBypass(sender, senderRoleOpt);
            final int senderWeight = senderRoleOpt.map(Role::getWeight).orElse(hasBypass ? Integer.MAX_VALUE : 0);

            if (args.length == 1) {
                List<String> subCommands = Arrays.asList("info", "list", "add", "remove", "set", "clear", "help");
                subCommands.stream().filter(sub -> sub.toLowerCase().startsWith(currentArg)).forEach(completions::add);
            }
            else if (args.length == 2) {
                String subCmd = args[0].toLowerCase();
                boolean suggestPlayer = Arrays.asList("add", "remove", "set", "clear", "info").contains(subCmd);
                boolean suggestGroup = Arrays.asList("list", "info", "clear").contains(subCmd);
                if (suggestPlayer) {
                    proxyServer.getAllPlayers().stream().map(Player::getUsername).filter(name -> name.toLowerCase().startsWith(currentArg)).forEach(completions::add);
                    if ("uuid:".startsWith(currentArg)) completions.add("uuid:");
                    if ("id:".startsWith(currentArg)) completions.add("id:");
                }
                if (suggestGroup) {
                    roleService.getAllCachedRoles().stream()
                            .filter(role -> hasBypass || role.getWeight() < senderWeight)
                            .map(Role::getName)
                            .filter(name -> name.toLowerCase().startsWith(currentArg))
                            .forEach(completions::add);
                }
            }
            else if (args.length == 3) {
                String subCmd = args[0].toLowerCase();
                if (Arrays.asList("add", "set", "remove").contains(subCmd)) {
                    roleService.getAllCachedRoles().stream()
                            .filter(role -> hasBypass || role.getWeight() < senderWeight)
                            .map(Role::getName)
                            .filter(name -> name.toLowerCase().startsWith(currentArg))
                            .filter(name -> !((subCmd.equals("remove") || subCmd.equals("set")) && name.equalsIgnoreCase("default")))
                            .forEach(completions::add);
                }
            }
            else if (args.length == 4 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("set"))) {
                List<String> durations = Arrays.asList("30s", "1m", "5m", "15m", "30m", "1h", "12h", "1d", "7d", "15d", "30d", "90d", "180d", "1y");
                durations.stream().filter(d -> d.startsWith(currentArg)).forEach(completions::add);
                if ("-hidden".startsWith(currentArg)) completions.add("-hidden");
            }
            else if (args.length == 5 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("set"))) {
                if (!args[3].equalsIgnoreCase("-hidden") && "-hidden".startsWith(currentArg)) {
                    completions.add("-hidden");
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[RoleCommand] Error in tab completion", e);
        }
        return completions.stream().distinct().sorted().collect(Collectors.toList());
    }
}