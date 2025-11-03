package com.realmmc.controller.proxy.commands.cmds;

// Imports do Velocity
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

// Imports do Controller
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
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileResolver;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.role.*;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisPublisher;
import com.realmmc.controller.shared.utils.NicknameFormatter;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.realmmc.controller.shared.utils.TimeUtils;

// Imports Java
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// <<< CORREÇÃO (BUG 1): Adicionar imports do Jackson >>>
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Imports org.json (REMOVIDOS - Bug 1)
// import org.json.JSONException;
// import org.json.JSONObject;


@Cmd(cmd = "role", aliases = {"group", "rank"}, onlyPlayer = false)
public class RoleCommand implements CommandInterface {

    private final String requiredPermission = "controller.manager";
    private final String requiredGroupName = "Gerente";
    private final RoleService roleService;
    private final ProfileService profileService;
    private final Optional<SoundPlayer> soundPlayerOpt;
    private final Logger logger;
    private final ProxyServer proxyServer;

    // <<< CORREÇÃO (BUG 1): Adicionar ObjectMapper >>>
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

    // --- Implementação dos Subcomandos ---

    private void handleInfo(CommandSource sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, label, "info <jogador | grupo>"); return; }
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

        resolveProfileAsync(finalTargetInput).thenAcceptAsync(targetProfileOpt -> {
            if (targetProfileOpt.isEmpty()) {
                Messages.send(finalSender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", finalTargetInput));
                playSound(finalSender, SoundKeys.ERROR);
                return;
            }
            final Profile targetProfile = targetProfileOpt.get();
            final UUID targetUuid = targetProfile.getUuid();

            PlayerSessionData sessionData;
            try {
                sessionData = roleService.loadPlayerDataAsync(targetUuid).join();
            } catch (Exception e) {
                handleCommandError(finalSender, "obter dados sessão jogador (sync /info)", e);
                return;
            }

            if (sessionData == null) {
                sessionData = roleService.getSessionDataFromCache(targetUuid)
                        .orElseGet(() -> {
                            logger.log(Level.SEVERE, "[RoleCommand INFO] SessionData ainda nulo após load.join() para {0}. Usando dados default.", targetUuid);
                            return roleService.getDefaultSessionData(targetUuid);
                        });
            }


            String formattedNick = NicknameFormatter.getFullFormattedNick(targetUuid);
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

            if (rolesToShow.isEmpty()) { Messages.send(finalSender, MessageKey.COMMON_INFO_LIST_EMPTY); }
            else {
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
                            if (pr.isPaused()) { statusKey = MessageKey.ROLE_INFO_STATUS_PAUSED; String remaining = (pr.getPausedTimeRemaining() != null && pr.getPausedTimeRemaining() > 0) ? TimeUtils.formatDuration(pr.getPausedTimeRemaining()) : Messages.translate(MessageKey.ROLE_INFO_STATUS_PERMANENT, senderLocale); statusPlaceholders.put("remaining_time", remaining); }
                            else if (pr.hasExpiredTime()) { statusKey = MessageKey.ROLE_INFO_STATUS_EXPIRED; }
                            else if (!pr.isPermanent()) { statusKey = MessageKey.ROLE_INFO_STATUS_TEMPORARY; long rem = pr.getExpiresAt() - System.currentTimeMillis(); statusPlaceholders.put("remaining_time", TimeUtils.formatDuration(Math.max(0, rem))); }
                            else { statusKey = MessageKey.ROLE_INFO_STATUS_PERMANENT; } break;
                        case EXPIRED: statusKey = MessageKey.ROLE_INFO_STATUS_EXPIRED; break;
                        case REMOVED: if (pr.getRemovedAt() != null) { statusKey = MessageKey.ROLE_INFO_STATUS_REMOVED_AT; statusPlaceholders.put("removed_at", TimeUtils.formatDate(pr.getRemovedAt())); }
                        else { statusKey = MessageKey.ROLE_INFO_STATUS_REMOVED; } break;
                        default: statusKey = MessageKey.ROLE_INFO_STATUS_UNKNOWN; break;
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
        }).exceptionally(ex -> handleCommandError(sender, "obter info jogador", ex));
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
        if (permissions.isEmpty()) { Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else {
            permissions.stream().sorted().limit(20).forEach(perm -> Messages.send(sender, new RawMessage("<white>  - <gray>{permission}").placeholder("permission", perm)));
            if (permissions.size() > 20) { Messages.send(sender, new RawMessage("<white>  ... e <gray>{count}</gray> mais.").placeholder("count", permissions.size() - 20)); }
        }

        List<String> inheritance = role.getInheritance() != null ? role.getInheritance() : Collections.emptyList();
        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_INHERITANCE, senderLocale)).with("count", inheritance.size()));
        if (inheritance.isEmpty()) { Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else {
            inheritance.stream().sorted().forEach(inheritedId -> { String inheritedDisplay = roleService.getRole(inheritedId).map(Role::getDisplayName).orElse(inheritedId + " (não encontrado)"); Messages.send(sender, new RawMessage("<white>  - <gray>{group}").placeholder("group", inheritedDisplay)); });
        }

        Messages.send(sender, "<white>");
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private enum RoleModificationType { ADD, REMOVE, SET }

    private void modifyPlayerRole(CommandSource sender, String[] args, String label, RoleModificationType type) {
        int minArgs = (type == RoleModificationType.REMOVE) ? 3 : 3;
        String usageArgs = "<jogador> <grupo>";
        if (type != RoleModificationType.REMOVE) usageArgs += " [duração]";
        if (type == RoleModificationType.ADD || type == RoleModificationType.SET) usageArgs += " [-hidden]";

        boolean hidden = false;
        String[] actualArgs = args;
        if ((type == RoleModificationType.ADD || type == RoleModificationType.SET) && args.length > minArgs && args[args.length - 1].equalsIgnoreCase("-hidden")) { hidden = true; actualArgs = Arrays.copyOf(args, args.length - 1); }

        if (actualArgs.length < minArgs) { sendUsage(sender, label, args[0] + " " + usageArgs); return; }

        final String targetInput = actualArgs[1];
        final String roleNameInput = actualArgs[2].toLowerCase();
        final String durationStr = (actualArgs.length > 3 && type != RoleModificationType.REMOVE) ? actualArgs[3] : null;
        final RoleModificationType finalType = type;
        final CommandSource finalSender = sender;
        final boolean finalHidden = hidden;

        Optional<Role> roleOpt = roleService.getRole(roleNameInput);
        if (roleOpt.isEmpty()) { Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_GROUP_NOT_FOUND).with("group", roleNameInput)); playSound(finalSender, SoundKeys.USAGE_ERROR); return; }
        final Role targetRole = roleOpt.get();

        if (targetRole.getName().equalsIgnoreCase("default") && (finalType == RoleModificationType.REMOVE || finalType == RoleModificationType.SET)) { Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CANNOT_MODIFY_DEFAULT)); playSound(finalSender, SoundKeys.USAGE_ERROR); return; }

        final Long expiresAt;
        if (finalType != RoleModificationType.REMOVE && durationStr != null) { long d = TimeUtils.parseDuration(durationStr); if (d <= 0) { Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_INVALID_DURATION).with("duration", durationStr)); playSound(finalSender, SoundKeys.USAGE_ERROR); return; } expiresAt = System.currentTimeMillis() + d; }
        else { expiresAt = null; }

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
            final String targetOriginalName = targetProfile.getName();

            roleService.loadPlayerDataAsync(targetUuid).thenAcceptAsync(targetData -> {
                int targetWeight = targetData.getPrimaryRole().getWeight();
                if (!hasBypass && targetWeight >= senderWeight) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CLEAR_SUPERIOR).with("target_name", targetOriginalName));
                    playSound(finalSender, SoundKeys.USAGE_ERROR);
                    return;
                }

                try {
                    final AtomicBoolean changed = new AtomicBoolean(false);
                    final AtomicReference<MessageKey> successMessageKey = new AtomicReference<>(null);
                    final AtomicBoolean vipAffected = new AtomicBoolean(targetRole.getType() == RoleType.VIP);
                    final String finalPrimaryRoleName;

                    synchronized (targetUuid.toString().intern()) {
                        final Profile latestProfile = profileService.getByUuid(targetUuid).orElseThrow(() -> new IllegalStateException("Perfil desapareceu DENTRO do lock!"));
                        List<PlayerRole> currentRoles = new ArrayList<>(latestProfile.getRoles() != null ? latestProfile.getRoles() : Collections.emptyList());

                        switch (finalType) {
                            case SET:
                                currentRoles.forEach(pr -> { if (pr != null && !pr.getRoleName().equalsIgnoreCase("default") && !pr.getRoleName().equalsIgnoreCase(roleNameInput) && pr.getStatus() == PlayerRole.Status.ACTIVE) { pr.setStatus(PlayerRole.Status.REMOVED); pr.setRemovedAt(System.currentTimeMillis()); pr.setPaused(false); pr.setPausedTimeRemaining(null); changed.set(true); roleService.getRole(pr.getRoleName()).ifPresent(r -> { if(r.getType() == RoleType.VIP) vipAffected.set(true); }); } });
                                addOrUpdateRole(currentRoles, targetRole.getName(), expiresAt, changed, true);
                                ensureDefaultActiveIfNeeded(currentRoles, targetRole.getName());
                                successMessageKey.set(MessageKey.ROLE_SUCCESS_SET);
                                logger.log(Level.FINE, "[RoleCommand {0}] Tentando definir role {1} para {2}", new Object[]{finalType, roleNameInput, targetUuid});
                                break;
                            case ADD:
                                boolean alreadyHasActivePermanent = currentRoles.stream().anyMatch(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(roleNameInput) && pr.getStatus() == PlayerRole.Status.ACTIVE && pr.isPermanent());
                                if (alreadyHasActivePermanent && expiresAt == null) { Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_ALREADY_HAS_PERMANENT).with("player", targetOriginalName).with("group_display", targetRole.getDisplayName())); playSound(finalSender, SoundKeys.NOTIFICATION); return; }
                                addOrUpdateRole(currentRoles, targetRole.getName(), expiresAt, changed, false);
                                ensureDefaultActiveIfNeeded(currentRoles, targetRole.getName());
                                successMessageKey.set(MessageKey.ROLE_SUCCESS_ADD);
                                logger.log(Level.FINE, "[RoleCommand {0}] Tentando adicionar role {1} para {2}", new Object[]{finalType, roleNameInput, targetUuid});
                                break;
                            case REMOVE:
                                Optional<PlayerRole> activeRoleToRemove = currentRoles.stream().filter(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(roleNameInput) && pr.getStatus() == PlayerRole.Status.ACTIVE).findFirst();
                                if (activeRoleToRemove.isPresent()) { PlayerRole ex = activeRoleToRemove.get(); ex.setStatus(PlayerRole.Status.REMOVED); ex.setRemovedAt(System.currentTimeMillis()); ex.setPaused(false); ex.setPausedTimeRemaining(null); changed.set(true); successMessageKey.set(MessageKey.ROLE_SUCCESS_REMOVE); roleService.getRole(ex.getRoleName()).ifPresent(r -> { if(r.getType() == RoleType.VIP) vipAffected.set(true); }); ensureDefaultActiveIfNeeded(currentRoles, "dummy"); }
                                else { Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_NOT_ACTIVE).with("player", targetOriginalName).with("group_display", targetRole.getDisplayName()).with("group_name", targetRole.getName())); playSound(finalSender, SoundKeys.NOTIFICATION); return; }
                                logger.log(Level.FINE, "[RoleCommand {0}] Tentando remover role {1} de {2}", new Object[]{finalType, roleNameInput, targetUuid});
                                break;
                            default: throw new IllegalStateException("Tipo de modificação desconhecido");
                        }

                        if (changed.get()) {
                            try { roleService.updatePauseState(targetUuid, currentRoles); } catch (Exception e) { logger.log(Level.WARNING, "Erro updatePauseState", e); }
                            latestProfile.setRoles(currentRoles);

                            Role calculatedPrimaryRole = calculatePrimaryRole(currentRoles);
                            if (calculatedPrimaryRole != null) {
                                finalPrimaryRoleName = calculatedPrimaryRole.getName();
                                if (!Objects.equals(latestProfile.getPrimaryRoleName(), finalPrimaryRoleName)) {
                                    latestProfile.setPrimaryRoleName(finalPrimaryRoleName);
                                    logger.log(Level.FINE, "[RoleCommand {0}] PrimaryRoleName definido como ''{1}'' no perfil antes de salvar.", new Object[]{finalType, finalPrimaryRoleName});
                                } else {
                                    logger.log(Level.FINEST, "[RoleCommand {0}] PrimaryRoleName (''{1}'') não precisou ser atualizado no perfil.", new Object[]{finalType, finalPrimaryRoleName});
                                }
                            } else {
                                logger.log(Level.SEVERE, "[RoleCommand {0}] ERRO CRÍTICO: Não foi possível calcular o grupo primário para {1} antes de salvar!", new Object[]{finalType, targetUuid});
                                latestProfile.setPrimaryRoleName("default");
                                finalPrimaryRoleName = "default";
                            }

                            try {
                                profileService.save(latestProfile);
                                logger.log(Level.INFO, "[RoleCommand {0}] Perfil salvo com sucesso para {1}. PrimaryRoleName salvo: {2}", new Object[]{finalType, targetUuid, latestProfile.getPrimaryRoleName()});
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "[RoleCmd {0}] FALHA AO SALVAR PERFIL para {1}");
                                handleCommandError(finalSender, "salvar perfil", e);
                                return;
                            }

                            roleService.publishSync(targetUuid);
                            logger.log(Level.INFO, "[RoleCommand {0}] ROLE_SYNC publicado para {1}", new Object[]{finalType, targetUuid});

                            String durationMsg;
                            Locale senderLocale = Messages.determineLocale(finalSender);
                            if (finalType != RoleModificationType.REMOVE) { String durKey = (expiresAt == null) ? Messages.translate(MessageKey.ROLE_INFO_STATUS_PERMANENT, senderLocale) : Messages.translate(Message.of(MessageKey.ROLE_INFO_STATUS_TEMPORARY).with("remaining_time", TimeUtils.formatDuration(expiresAt - System.currentTimeMillis())), senderLocale); durationMsg = durKey.replaceAll("<[^>]*>", ""); } else { durationMsg = ""; }

                            if (successMessageKey.get() != null) { Messages.send(finalSender, Message.of(successMessageKey.get()).with("group_display", targetRole.getDisplayName()).with("group_name", targetRole.getName()).with("player", targetOriginalName).with("duration_msg", durationMsg)); }
                            playSound(finalSender, SoundKeys.SUCCESS);

                            boolean isTargetOnline = proxyServer.getPlayer(targetUuid).isPresent();
                            if (!finalHidden && (finalType == RoleModificationType.ADD || finalType == RoleModificationType.SET)) {
                                publishRoleBroadcast(targetUuid, targetOriginalName, targetRole);
                            }

                            if (isTargetOnline && AuthenticationGuard.isAuthenticated(targetUuid)) {
                                RoleKickHandler.KickReason reason = (finalType == RoleModificationType.ADD || finalType == RoleModificationType.SET) ? RoleKickHandler.KickReason.ADD_SET : RoleKickHandler.KickReason.REMOVED;
                                RoleKickHandler.scheduleKick(targetUuid, targetRole.getType(), reason, targetRole.getDisplayName());
                                logger.log(Level.FINE, "[RoleCommand {0}] Kick agendado para {1} (estado ONLINE).", new Object[]{finalType, targetUuid});
                            } else if (isTargetOnline) {
                                logger.log(Level.FINE, "[RoleCommand {0}] Kick para {1} pulado, jogador está no estado CONNECTING.", new Object[]{finalType, targetUuid});
                            }

                        } else {
                            logger.log(Level.FINE, "[RoleCommand {0}] Nenhuma mudança detectada para {1} e role {2}. Nenhuma ação tomada.", new Object[]{finalType, targetUuid, roleNameInput});
                        }
                    } // Fim sync block
                } catch (Exception e) {
                    handleCommandError(finalSender, "processar " + finalType, e);
                }
            }, roleService.getAsyncExecutor());
        }).exceptionally(ex -> handleCommandError(sender, "resolver perfil", ex));
    }

    private void addOrUpdateRole(List<PlayerRole> currentRoles, String roleName, Long expiresAt, AtomicBoolean changed, boolean isSet) {
        Optional<PlayerRole> existingActive = currentRoles.stream().filter(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(roleName) && pr.getStatus() == PlayerRole.Status.ACTIVE).findFirst();
        if (existingActive.isPresent()) { PlayerRole pr = existingActive.get(); boolean expiresChanged = !Objects.equals(pr.getExpiresAt(), expiresAt); boolean pauseCleared = pr.isPaused() || pr.getPausedTimeRemaining() != null; if (expiresChanged || pauseCleared) { pr.setExpiresAt(expiresAt); if (pauseCleared) { pr.setPaused(false); pr.setPausedTimeRemaining(null); } changed.set(true); logger.finest("[addOrUpdateRole] Role ATIVO existente '" + roleName + "' atualizado."); } else { logger.finest("[addOrUpdateRole] Role ATIVO '" + roleName + "' já estava no estado desejado."); } }
        else { currentRoles.add(PlayerRole.builder().roleName(roleName).expiresAt(expiresAt).status(PlayerRole.Status.ACTIVE).paused(false).addedAt(System.currentTimeMillis()).build()); changed.set(true); logger.finest("[addOrUpdateRole] Nenhum role ATIVO '" + roleName + "' encontrado. Adicionado NOVO PlayerRole."); }
    }

    private void ensureDefaultActiveIfNeeded(List<PlayerRole> currentRoles, String modifiedRoleName) {
        boolean otherRoleIsActive = currentRoles.stream().anyMatch(pr -> pr != null && pr.getStatus() == PlayerRole.Status.ACTIVE && !"default".equalsIgnoreCase(pr.getRoleName()));
        Optional<PlayerRole> defaultRoleOpt = currentRoles.stream().filter(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName())).findFirst();
        if (defaultRoleOpt.isPresent()) { PlayerRole defaultRole = defaultRoleOpt.get(); if (!otherRoleIsActive && defaultRole.getStatus() != PlayerRole.Status.ACTIVE) { defaultRole.setStatus(PlayerRole.Status.ACTIVE); defaultRole.setRemovedAt(null); defaultRole.setPaused(false); defaultRole.setPausedTimeRemaining(null); logger.finest("[ensureDefaultActiveIfNeeded] Role 'default' existente reativado."); } else if (otherRoleIsActive && defaultRole.getStatus() == PlayerRole.Status.REMOVED) { defaultRole.setStatus(PlayerRole.Status.ACTIVE); defaultRole.setRemovedAt(null); defaultRole.setPaused(false); defaultRole.setPausedTimeRemaining(null); logger.warning("[ensureDefaultActiveIfNeeded] Role 'default' estava REMOVED enquanto outro role estava ativo. Reativado 'default'."); } }
        else if (!otherRoleIsActive) { currentRoles.add(PlayerRole.builder().roleName("default").status(PlayerRole.Status.ACTIVE).build()); logger.finest("[ensureDefaultActiveIfNeeded] Role 'default' não encontrado, adicionado como ACTIVE."); }
    }

    private Role calculatePrimaryRole(List<PlayerRole> currentRoles) {
        return currentRoles.stream().filter(pr -> pr != null && pr.isActive()).map(pr -> roleService.getRole(pr.getRoleName())).filter(Optional::isPresent).map(Optional::get).max(Comparator.comparingInt(Role::getWeight)).orElse(roleService.getRole("default").orElse(null));
    }

    // <<< CORREÇÃO (BUG 1): Usar Jackson (ObjectMapper) >>>
    private void publishRoleBroadcast(UUID playerUuid, String playerName, Role newRole) {
        try {
            // Usar ObjectNode (Jackson) em vez de JSONObject (org.json)
            ObjectNode node = objectMapper.createObjectNode();
            node.put("playerUuid", playerUuid.toString());
            node.put("playerName", playerName);
            node.put("playerColor", newRole.getColor());
            node.put("groupDisplay", newRole.getDisplayName());

            String jsonMessage = node.toString(); // Converte o ObjectNode para String
            RedisPublisher.publish(RedisChannel.ROLE_BROADCAST, jsonMessage);

            logger.log(Level.FINE, "Mensagem ROLE_BROADCAST publicada para {0}", playerName);
        } catch (Exception e) { // Captura Exception genérica
            logger.log(Level.SEVERE, "Falha ao serializar/publicar mensagem ROLE_BROADCAST para " + playerName, e);
        }
    }
    // <<< FIM CORREÇÃO (BUG 1) >>>

    private void handleClear(CommandSource sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, label, "clear <jogador | grupo>"); return; }
        final String targetInput = args[1];
        final CommandSource finalSender = sender;

        final Optional<Role> senderRoleOpt = getSenderPrimaryRole(finalSender);
        final boolean hasBypass = checkBypass(finalSender, senderRoleOpt);
        final int senderWeight = senderRoleOpt.map(Role::getWeight).orElse(hasBypass ? Integer.MAX_VALUE : 0);

        // Despacha para async
        TaskScheduler.runAsync(() -> {
            Optional<Role> roleOpt = roleService.getRole(targetInput);

            // <<< CORREÇÃO (BUG 3): Lógica implementada para limpar grupo >>>
            if (roleOpt.isPresent()) {
                final Role targetRole = roleOpt.get();
                final String targetRoleName = targetRole.getName();
                final String targetRoleDisplay = targetRole.getDisplayName();

                // 1. Verificar se é o grupo default
                if (targetRoleName.equalsIgnoreCase("default")) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CANNOT_CLEAR_GROUP).with("group_name", targetRoleName));
                    playSound(finalSender, SoundKeys.USAGE_ERROR);
                    return;
                }

                // 2. Verificar se o sender pode gerir este grupo (mesmo para limpar)
                if (!hasBypass && targetRole.getWeight() >= senderWeight) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CANNOT_MANAGE_SUPERIOR).with("target_group", targetRoleDisplay));
                    playSound(finalSender, SoundKeys.USAGE_ERROR);
                    return;
                }

                // 3. Enviar mensagem de progresso
                Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_CLEAR_GROUP_IN_PROGRESS).with("group_display", targetRoleDisplay));

                // 4. Buscar perfis (assíncrono por natureza)
                List<Profile> profilesToClear = profileService.findByActiveRoleName(targetRoleName);

                if (profilesToClear.isEmpty()) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_CLEAR_GROUP_NO_ELIGIBLE).with("group_display", targetRoleDisplay));
                    playSound(finalSender, SoundKeys.NOTIFICATION);
                    return;
                }

                final AtomicInteger successCount = new AtomicInteger(0);
                final AtomicInteger failCount = new AtomicInteger(0);

                // 5. Iterar e limpar (ainda na thread async)
                for (Profile profile : profilesToClear) {
                    final UUID targetUuid = profile.getUuid();
                    try {
                        // 5a. Verificar permissão para CADA jogador
                        PlayerSessionData targetData = roleService.loadPlayerDataAsync(targetUuid).join(); // .join() é seguro aqui
                        int targetWeight = targetData.getPrimaryRole().getWeight();

                        if (!hasBypass && targetWeight >= senderWeight && !(sender instanceof ConsoleCommandSource)) {
                            logger.finer("[RoleClearG] Pulando " + profile.getName() + " (peso superior)");
                            failCount.incrementAndGet();
                            continue;
                        }

                        // 5b. Executar a limpeza (lógica de clearRolesByPlayer)
                        synchronized (targetUuid.toString().intern()) {
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
                                try { roleService.updatePauseState(targetUuid, currentRoles); } catch (Exception e) { logger.log(Level.WARNING, "Erro updatePauseState durante clearRolesByGroup", e); }
                                latestProfile.setRoles(currentRoles);

                                profileService.save(latestProfile);
                                roleService.publishSync(targetUuid);
                                successCount.incrementAndGet();

                                // Agendar kick
                                boolean isTargetOnline = proxyServer.getPlayer(targetUuid).isPresent();
                                if (isTargetOnline && AuthenticationGuard.isAuthenticated(targetUuid)) {
                                    RoleKickHandler.scheduleKick(targetUuid, targetRole.getType(), RoleKickHandler.KickReason.REMOVED, targetRole.getDisplayName());
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "[RoleClearG] Falha ao limpar role para " + targetUuid, e);
                        failCount.incrementAndGet();
                    }
                } // Fim do loop

                // 6. Enviar resultado
                Messages.send(finalSender, Message.of(MessageKey.ROLE_SUCCESS_CLEAR_GROUP).with("count", successCount.get()).with("group_name", targetRoleName));
                if (failCount.get() > 0) {
                    Messages.send(finalSender, "<yellow>" + failCount.get() + " jogadores não puderam ser modificados (permissão insuficiente).</yellow>");
                }
                playSound(finalSender, SoundKeys.SUCCESS);
                // <<< FIM DA CORREÇÃO (BUG 3) >>>
            } else {
                // Lógica existente para limpar por jogador (continua igual)
                clearRolesByPlayer(finalSender, targetInput, label, senderRoleOpt, hasBypass, senderWeight);
            }
        });
    }

    private void clearRolesByPlayer(CommandSource sender, String targetInput, String label, Optional<Role> senderRoleOpt, boolean hasBypass, int senderWeight) {
        // Já está rodando async
        final CommandSource finalSender = sender;
        final String finalTargetInput = targetInput;

        resolveProfileAsync(finalTargetInput).thenAcceptAsync(targetProfileOpt -> {
            if (targetProfileOpt.isEmpty()) { Messages.send(finalSender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", finalTargetInput)); playSound(finalSender, SoundKeys.USAGE_ERROR); return; }
            final Profile targetProfile = targetProfileOpt.get(); final UUID targetUuid = targetProfile.getUuid(); final String targetOriginalName = targetProfile.getName();

            if (sender instanceof Player p && p.getUniqueId().equals(targetUuid)) {
                Messages.send(sender, MessageKey.ROLE_ERROR_CANNOT_CLEAR_SELF);
                playSound(sender, SoundKeys.USAGE_ERROR);
                return;
            }

            roleService.loadPlayerDataAsync(targetUuid).thenAcceptAsync(currentTargetData -> {
                int targetMaxWeight = currentTargetData.getPrimaryRole().getWeight();
                if (!hasBypass && targetMaxWeight >= senderWeight) {
                    Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CLEAR_SUPERIOR).with("target_name", targetOriginalName));
                    playSound(finalSender, SoundKeys.USAGE_ERROR); return;
                }

                try { final AtomicBoolean changed = new AtomicBoolean(false); final AtomicBoolean vipRemoved = new AtomicBoolean(false); final AtomicReference<Role> lastRemovedRole = new AtomicReference<>(null);
                    synchronized (targetUuid.toString().intern()) {
                        final Profile latestProfile = profileService.getByUuid(targetUuid).orElseThrow(()->new IllegalStateException("Perfil sumiu durante clearRolesByPlayer"));
                        List<PlayerRole> currentRoles = latestProfile.getRoles(); if (currentRoles == null) currentRoles = new ArrayList<>();
                        for (PlayerRole pr : currentRoles) { if (pr != null && !"default".equalsIgnoreCase(pr.getRoleName()) && pr.getStatus() == PlayerRole.Status.ACTIVE) { pr.setStatus(PlayerRole.Status.REMOVED); pr.setRemovedAt(System.currentTimeMillis()); pr.setPaused(false); pr.setPausedTimeRemaining(null); changed.set(true); roleService.getRole(pr.getRoleName()).ifPresent(r -> { lastRemovedRole.set(r); if(r.getType() == RoleType.VIP) vipRemoved.set(true); }); } }
                        ensureDefaultActiveIfNeeded(currentRoles, "dummy");
                        if (!changed.get()) { boolean defaultReactivated = currentRoles.stream().anyMatch(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()) && pr.getStatus() == PlayerRole.Status.ACTIVE); if (!defaultReactivated) { Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_ALREADY_DEFAULT).with("player", targetOriginalName)); playSound(finalSender, SoundKeys.NOTIFICATION); return; } }

                        Role calculatedPrimaryRole = calculatePrimaryRole(currentRoles);
                        if (calculatedPrimaryRole != null) { String newPrimaryName = calculatedPrimaryRole.getName(); if (!Objects.equals(latestProfile.getPrimaryRoleName(), newPrimaryName)) { latestProfile.setPrimaryRoleName(newPrimaryName); } } else { latestProfile.setPrimaryRoleName("default"); }
                        try { roleService.updatePauseState(targetUuid, currentRoles); } catch (Exception e) { logger.log(Level.WARNING, "Erro updatePauseState durante clearRolesByPlayer", e); }
                        latestProfile.setRoles(currentRoles);
                        try { profileService.save(latestProfile); }
                        catch (Exception e) { logger.log(Level.SEVERE, "[RoleClearP] FALHA AO SALVAR perfil após clear", e); handleCommandError(finalSender, "salvar clear", e); return; }
                        roleService.publishSync(targetUuid);
                        logger.log(Level.INFO, "[RoleClearP] ROLE_SYNC publicado para {0}", targetUuid);
                        Messages.send(finalSender, Message.of(MessageKey.ROLE_SUCCESS_CLEAR_PLAYER).with("player", targetOriginalName)); playSound(finalSender, SoundKeys.SUCCESS);

                        boolean isTargetOnline = proxyServer.getPlayer(targetUuid).isPresent();
                        if (isTargetOnline && AuthenticationGuard.isAuthenticated(targetUuid)) {
                            RoleType typeForKick = vipRemoved.get() ? RoleType.VIP : (lastRemovedRole.get() != null ? lastRemovedRole.get().getType() : RoleType.DEFAULT);
                            String displayForKick = lastRemovedRole.get() != null ? lastRemovedRole.get().getDisplayName() : "grupo";
                            RoleKickHandler.scheduleKick(targetUuid, typeForKick, RoleKickHandler.KickReason.REMOVED, displayForKick);
                            logger.log(Level.FINE, "[RoleClearP] Kick agendado para {0} (estado ONLINE).", targetUuid);
                        } else if (isTargetOnline) {
                            logger.log(Level.FINE, "[RoleClearP] Kick para {0} pulado, jogador está no estado CONNECTING.", targetUuid);
                        }
                    }
                } catch (Exception e) { handleCommandError(finalSender, "processar clear player", e); }
            }, roleService.getAsyncExecutor());
        }).exceptionally(ex -> handleCommandError(sender, "resolver perfil clear player", ex));
    }


    private void handleList(CommandSource sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, label, "list <grupo>"); return; }
        final String groupNameInput = args[1].toLowerCase();
        final CommandSource finalSender = sender;
        final Locale senderLocale = Messages.determineLocale(sender);
        Optional<Role> roleOpt = roleService.getRole(groupNameInput);
        if (roleOpt.isEmpty()) { Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_GROUP_NOT_FOUND).with("group", groupNameInput)); playSound(finalSender, SoundKeys.USAGE_ERROR); return; }
        final Role targetRole = roleOpt.get(); final String targetRoleDisplayName = targetRole.getDisplayName();
        Messages.send(finalSender, Message.of(MessageKey.ROLE_LIST_IN_PROGRESS).with("group_display", targetRoleDisplayName)); playSound(finalSender, SoundKeys.NOTIFICATION);

        CompletableFuture.supplyAsync(() -> profileService.findByActiveRoleName(targetRole.getName()), roleService.getAsyncExecutor())
                .thenAcceptAsync(profilesWithRole -> {
                    Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", "Jogadores Ativos no Grupo " + targetRoleDisplayName).with("count", profilesWithRole.size()));
                    if (profilesWithRole.isEmpty()) { Messages.send(finalSender, MessageKey.COMMON_INFO_LIST_EMPTY); }
                    else { int index = 1; profilesWithRole.sort(Comparator.comparing(Profile::getName, String.CASE_INSENSITIVE_ORDER)); for (Profile profile : profilesWithRole) { PlayerRole roleDetails = profile.getRoles().stream().filter(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(targetRole.getName()) && pr.getStatus() == PlayerRole.Status.ACTIVE).findFirst().orElse(null); String statusString = ""; if (roleDetails != null) { MessageKey statusKey; Map<String,Object> ph = new HashMap<>(); if (roleDetails.isPaused()) { statusKey = MessageKey.ROLE_INFO_STATUS_PAUSED; String remaining = (roleDetails.getPausedTimeRemaining() != null && roleDetails.getPausedTimeRemaining() > 0) ? TimeUtils.formatDuration(roleDetails.getPausedTimeRemaining()) : Messages.translate(MessageKey.ROLE_INFO_STATUS_PERMANENT, senderLocale); ph.put("remaining_time", remaining); } else if (roleDetails.hasExpiredTime()) { statusKey = MessageKey.ROLE_INFO_STATUS_EXPIRED; } else if (!roleDetails.isPermanent()) { statusKey = MessageKey.ROLE_INFO_STATUS_TEMPORARY; long rem = roleDetails.getExpiresAt() - System.currentTimeMillis(); ph.put("remaining_time", TimeUtils.formatDuration(Math.max(0, rem))); } else { statusKey = MessageKey.ROLE_INFO_STATUS_PERMANENT; } statusString = Messages.translate(Message.of(statusKey).with(ph), senderLocale); } else { statusString = Messages.translate(MessageKey.ROLE_INFO_STATUS_UNKNOWN, senderLocale); }
                        Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM)
                                .with("index", "<dark_gray>" + (index++) + "</dark_gray>")
                                .with("value", profile.getName() + " " + statusString));
                    } }
                    Messages.send(finalSender, "<white>"); playSound(finalSender, SoundKeys.NOTIFICATION);
                }, roleService.getAsyncExecutor())
                .exceptionally(ex -> handleCommandError(sender, "listar grupo " + targetRoleDisplayName, ex));
    }

    // --- Métodos Auxiliares (Síncronos, chamados pela thread principal ou callbacks) ---

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
        if (hasOptional) { Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_FULL); }
        else { Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_REQUIRED); }
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void sendUsage(CommandSource sender, String label, String usage) {
        Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/" + label + " " + usage));
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player player) {
            soundPlayerOpt.ifPresent(sp -> {
                try {
                    sp.playSound(player, key);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Erro ao tocar som ''{0}'' para {1}", new Object[]{key, player.getUsername()});
                }
            });
        }
    }

    private CompletableFuture<Optional<Profile>> resolveProfileAsync(String input) {
        Executor exec = roleService.getAsyncExecutor();
        if (exec == null) exec = ForkJoinPool.commonPool();
        final Executor fExec = exec;
        return CompletableFuture.supplyAsync(() -> {
            try { return ProfileResolver.resolve(input); }
            catch (Exception e) { logger.log(Level.SEVERE, "Erro ProfileResolver", e); return Optional.empty(); }
        }, fExec);
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

                logger.log(Level.WARNING, "Cache miss de sessão para o sender {0}, carregando sync...", p.getUsername());
                return Optional.of(roleService.loadPlayerDataAsync(p.getUniqueId()).join().getPrimaryRole());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Falha ao obter role (sync fallback) para " + p.getUsername(), e);
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
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) { cause = cause.getCause(); }
        logger.log(Level.SEVERE, "Erro durante ação ''" + action + "'' no RoleCommand", cause); // Log padronizado
        Messages.send(sender, MessageKey.COMMAND_ERROR);
        playSound(sender, SoundKeys.ERROR);
        return null;
    }

    // --- Tab Completion (Roda na thread principal) ---
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
                            // <<< CORREÇÃO (BUG 2): hasBpass -> hasBypass >>>
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
            logger.log(Level.SEVERE, "Erro tab completion RoleCommand", e);
        }
        return completions.stream().distinct().sorted().collect(Collectors.toList());
    }
}