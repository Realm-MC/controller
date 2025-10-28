package com.realmmc.controller.proxy.commands.cmds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.proxy.commands.CommandInterface;
import com.realmmc.controller.shared.annotations.Cmd;
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
import com.realmmc.controller.shared.utils.TimeUtils;

// Velocity Imports
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

// Java Util Imports
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

@Cmd(cmd = "role", aliases = {"group", "rank"}, onlyPlayer = false)
public class RoleCommand implements CommandInterface {

    private final String requiredPermission = "controller.manager";
    private final String requiredGroupName = "Manager";
    private final RoleService roleService;
    private final ProfileService profileService;
    private final SoundPlayer soundPlayer;
    private final Logger logger;
    private final ProxyServer proxyServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RoleCommand() {
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.soundPlayer = ServiceRegistry.getInstance().getService(SoundPlayer.class).orElse(null);
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
        this.logger = Proxy.getInstance().getLogger();
    }

    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        if (!sender.hasPermission(requiredPermission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", requiredGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR); // Ponto 1
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
        // <<< CORREÇÃO PONTO 2: Obter o locale do sender >>>
        final Locale senderLocale = Messages.determineLocale(finalSender);

        resolveProfileAsync(finalTargetInput).thenAcceptAsync(targetProfileOpt -> {
            if (targetProfileOpt.isEmpty()) {
                Messages.send(finalSender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", finalTargetInput));
                playSound(finalSender, SoundKeys.ERROR);
                return;
            }
            final Profile targetProfile = targetProfileOpt.get();
            final UUID targetUuid = targetProfile.getUuid();

            try {
                roleService.loadPlayerDataAsync(targetUuid).join();
            } catch (Exception e) {
                handleCommandError(finalSender, "obter dados sessão jogador (sync /info)", e);
                return;
            }

            PlayerSessionData sessionData = roleService.getSessionDataFromCache(targetUuid)
                    .orElseGet(() -> {
                        logger.severe("[RoleCommand INFO] SessionData ainda nulo após load.join() para " + targetUuid + ". Usando dados default.");
                        return roleService.getDefaultSessionData(targetUuid);
                    });


            String formattedNick = NicknameFormatter.getFullFormattedNick(targetUuid);
            Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "Roles de " + formattedNick));

            Role primary = sessionData.getPrimaryRole();
            // <<< CORREÇÃO PONTO 2 & 4: Traduz a chave e usa o formato common.info.line >>>
            String primaryKey = Messages.translate(MessageKey.ROLE_INFO_PRIMARY_ACTIVE, senderLocale);
            String primaryValue = primary.getDisplayName() + " (" + primary.getName() + ")";
            Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LINE)
                    .with("key", primaryKey)
                    .with("value", primaryValue)
            );

            Profile latestProfileForInfo = profileService.getByUuid(targetUuid).orElse(targetProfile);
            List<PlayerRole> rolesToShow = latestProfileForInfo.getRoles() != null ? latestProfileForInfo.getRoles() : Collections.emptyList();

            // <<< CORREÇÃO PONTO 2 & 4: Traduz a chave e usa o formato common.info.list.header >>>
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
                    // <<< CORREÇÃO PONTO 2: Traduz o status usando o locale do sender >>>
                    statusString = Messages.translate(Message.of(statusKey).with(statusPlaceholders), senderLocale);
                    String value = displayName + " " + statusString;
                    // <<< CORREÇÃO PONTO 5: Formata o índice >>>
                    Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM)
                            .with("index", "<dark_gray>" + (index++) + "</dark_gray>")
                            .with("value", value));
                }
            }
            Messages.send(finalSender, "<white>");
            playSound(finalSender, SoundKeys.NOTIFICATION);
        }).exceptionally(ex -> handleCommandError(finalSender, "obter info jogador", ex));
    }

    private void displayGroupInfo(CommandSource sender, Role role) {
        final CommandSource finalSender = sender;
        final Role finalRole = role;
        // <<< CORREÇÃO PONTO 2: Obter o locale do sender >>>
        final Locale senderLocale = Messages.determineLocale(finalSender);

        Messages.send(finalSender, Message.of(MessageKey.ROLE_GROUP_INFO_HEADER).with("group_name", finalRole.getDisplayName()));

        // <<< CORREÇÃO PONTO 2 & 4: Usa common.info.line e traduz chaves >>>
        Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_WEIGHT, senderLocale)).with("value", finalRole.getWeight()));
        Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_PREFIX, senderLocale)).with("value", finalRole.getPrefix() != null && !finalRole.getPrefix().isEmpty() ? finalRole.getPrefix() : Messages.translate(MessageKey.ROLE_GROUP_INFO_VALUE_NONE, senderLocale)));
        Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_SUFFIX, senderLocale)).with("value", finalRole.getSuffix() != null && !finalRole.getSuffix().isEmpty() ? finalRole.getSuffix() : Messages.translate(MessageKey.ROLE_GROUP_INFO_VALUE_NONE, senderLocale)));
        Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_TYPE, senderLocale)).with("value", finalRole.getType() != null ? finalRole.getType().name() : "N/A"));

        List<String> permissions = finalRole.getPermissions() != null ? finalRole.getPermissions() : Collections.emptyList();
        Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_PERMISSIONS, senderLocale)).with("count", permissions.size()));
        if (permissions.isEmpty()) { Messages.send(finalSender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else {
            permissions.stream().sorted().limit(20).forEach(perm -> Messages.send(finalSender, new RawMessage("<white>  - <gray>{permission}").placeholder("permission", perm)));
            if (permissions.size() > 20) { Messages.send(finalSender, new RawMessage("<white>  ... e <gray>{count}</gray> mais.").placeholder("count", permissions.size() - 20)); }
        }

        List<String> inheritance = finalRole.getInheritance() != null ? finalRole.getInheritance() : Collections.emptyList();
        Messages.send(finalSender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", Messages.translate(MessageKey.ROLE_GROUP_INFO_KEY_INHERITANCE, senderLocale)).with("count", inheritance.size()));
        if (inheritance.isEmpty()) { Messages.send(finalSender, MessageKey.COMMON_INFO_LIST_EMPTY); }
        else {
            inheritance.stream().sorted().forEach(inheritedId -> { String inheritedDisplay = roleService.getRole(inheritedId).map(Role::getDisplayName).orElse(inheritedId + " (não encontrado)"); Messages.send(finalSender, new RawMessage("<white>  - <gray>{group}").placeholder("group", inheritedDisplay)); });
        }

        Messages.send(finalSender, "<white>");
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

        if (targetRole.getName().equalsIgnoreCase("default") && (finalType == RoleModificationType.REMOVE || finalType == RoleModificationType.SET)) { Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_CANNOT_MODIFY_DEFAULT).with("group_display", targetRole.getDisplayName())); playSound(finalSender, SoundKeys.USAGE_ERROR); return; }

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
                    String finalPrimaryRoleName = "default";

                    synchronized (targetUuid.toString().intern()) {
                        final Profile latestProfile = profileService.getByUuid(targetUuid).orElseThrow(() -> new IllegalStateException("Perfil desapareceu DENTRO do lock!"));
                        List<PlayerRole> currentRoles = new ArrayList<>(latestProfile.getRoles() != null ? latestProfile.getRoles() : Collections.emptyList());

                        switch (finalType) {
                            case SET:
                                currentRoles.forEach(pr -> { if (pr != null && !pr.getRoleName().equalsIgnoreCase("default") && !pr.getRoleName().equalsIgnoreCase(roleNameInput) && pr.getStatus() == PlayerRole.Status.ACTIVE) { pr.setStatus(PlayerRole.Status.REMOVED); pr.setRemovedAt(System.currentTimeMillis()); pr.setPaused(false); pr.setPausedTimeRemaining(null); changed.set(true); roleService.getRole(pr.getRoleName()).ifPresent(r -> { if(r.getType() == RoleType.VIP) vipAffected.set(true); }); } });
                                addOrUpdateRole(currentRoles, targetRole.getName(), expiresAt, changed, true);
                                ensureDefaultActiveIfNeeded(currentRoles, targetRole.getName());
                                successMessageKey.set(MessageKey.ROLE_SUCCESS_SET);
                                logger.fine("[RoleCommand SET] ...");
                                break;
                            case ADD:
                                boolean alreadyHasActivePermanent = currentRoles.stream().anyMatch(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(roleNameInput) && pr.getStatus() == PlayerRole.Status.ACTIVE && pr.isPermanent());
                                if (alreadyHasActivePermanent && expiresAt == null) { Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_ALREADY_HAS_PERMANENT).with("player", targetOriginalName).with("group_display", targetRole.getDisplayName())); playSound(finalSender, SoundKeys.NOTIFICATION); return; }
                                addOrUpdateRole(currentRoles, targetRole.getName(), expiresAt, changed, false);
                                ensureDefaultActiveIfNeeded(currentRoles, targetRole.getName());
                                successMessageKey.set(MessageKey.ROLE_SUCCESS_ADD);
                                logger.fine("[RoleCommand ADD] ...");
                                break;
                            case REMOVE:
                                Optional<PlayerRole> activeRoleToRemove = currentRoles.stream().filter(pr -> pr != null && pr.getRoleName().equalsIgnoreCase(roleNameInput) && pr.getStatus() == PlayerRole.Status.ACTIVE).findFirst();
                                if (activeRoleToRemove.isPresent()) { PlayerRole ex = activeRoleToRemove.get(); ex.setStatus(PlayerRole.Status.REMOVED); ex.setRemovedAt(System.currentTimeMillis()); ex.setPaused(false); ex.setPausedTimeRemaining(null); changed.set(true); successMessageKey.set(MessageKey.ROLE_SUCCESS_REMOVE); roleService.getRole(ex.getRoleName()).ifPresent(r -> { if(r.getType() == RoleType.VIP) vipAffected.set(true); }); ensureDefaultActiveIfNeeded(currentRoles, "dummy"); }
                                else { Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_NOT_ACTIVE).with("player", targetOriginalName).with("group_display", targetRole.getDisplayName()).with("group_name", targetRole.getName())); playSound(finalSender, SoundKeys.NOTIFICATION); return; }
                                break;
                            default: throw new IllegalStateException("...");
                        } // Fim switch

                        if (changed.get()) {
                            try { roleService.updatePauseState(targetUuid, currentRoles); } catch (Exception e) { logger.log(Level.WARNING, "Erro updatePauseState", e); }
                            latestProfile.setRoles(currentRoles);

                            Role calculatedPrimaryRole = calculatePrimaryRole(currentRoles);
                            if (calculatedPrimaryRole != null) {
                                finalPrimaryRoleName = calculatedPrimaryRole.getName();
                                if (!Objects.equals(latestProfile.getPrimaryRoleName(), finalPrimaryRoleName)) { latestProfile.setPrimaryRoleName(finalPrimaryRoleName); logger.fine("[RoleCommand " + finalType + "] PrimaryRoleName definido como '" + finalPrimaryRoleName + "' no perfil antes de salvar."); }
                                else { logger.finest("[RoleCommand " + finalType + "] PrimaryRoleName ('" + finalPrimaryRoleName + "') não precisou ser atualizado no perfil."); }
                            } else { logger.severe("[RoleCommand " + finalType + "] ERRO CRÍTICO: Não foi possível calcular o grupo primário para " + targetUuid + " antes de salvar!"); latestProfile.setPrimaryRoleName("default"); finalPrimaryRoleName = "default"; }

                            try {
                                profileService.save(latestProfile);
                                logger.info("[RoleCommand " + finalType + "] Perfil salvo com sucesso para " + targetUuid + ". PrimaryRoleName salvo: " + latestProfile.getPrimaryRoleName());
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "[RoleCmd " + finalType + "] FALHA AO SALVAR PERFIL para " + targetUuid, e);
                                handleCommandError(finalSender, "salvar perfil", e);
                                return;
                            }

                            roleService.publishSync(targetUuid);
                            logger.info("[RoleCommand " + finalType + "] ROLE_SYNC publicado para " + targetUuid);

                            String durationMsg;
                            Locale senderLocale = Messages.determineLocale(finalSender); // <<< CORREÇÃO PONTO 2
                            if (finalType != RoleModificationType.REMOVE) { String durKey = (expiresAt == null) ? Messages.translate(MessageKey.ROLE_INFO_STATUS_PERMANENT, senderLocale) : Messages.translate(Message.of(MessageKey.ROLE_INFO_STATUS_TEMPORARY).with("remaining_time", TimeUtils.formatDuration(expiresAt - System.currentTimeMillis())), senderLocale); durationMsg = durKey.replaceAll("<[^>]*>", ""); } else { durationMsg = ""; }

                            if (successMessageKey.get() != null) { Messages.send(finalSender, Message.of(successMessageKey.get()).with("group_display", targetRole.getDisplayName()).with("group_name", targetRole.getName()).with("player", targetOriginalName).with("duration_msg", durationMsg)); }
                            playSound(finalSender, SoundKeys.SUCCESS);

                            boolean isTargetOnline = proxyServer.getPlayer(targetUuid).isPresent();
                            if (!finalHidden && (finalType == RoleModificationType.ADD || finalType == RoleModificationType.SET)) { publishRoleBroadcast(targetUuid, targetOriginalName, targetRole); }
                            if (isTargetOnline) { RoleKickHandler.KickReason reason = (finalType == RoleModificationType.ADD || finalType == RoleModificationType.SET) ? RoleKickHandler.KickReason.ADD_SET : RoleKickHandler.KickReason.REMOVED; RoleKickHandler.scheduleKick(targetUuid, targetRole.getType(), reason, targetRole.getDisplayName()); }

                        } else {
                            logger.fine("[RoleCommand " + finalType + "] Nenhuma mudança detectada para " + targetUuid + " e role " + roleNameInput + ". Nenhuma ação tomada.");
                        }
                    } // Fim sync block
                } catch (Exception e) {
                    handleCommandError(finalSender, "processar " + finalType, e);
                }
            }, roleService.getAsyncExecutor());
        }).exceptionally(ex -> handleCommandError(finalSender, "resolver perfil", ex));
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

    private void publishRoleBroadcast(UUID playerUuid, String playerName, Role newRole) {
        try { ObjectNode node = objectMapper.createObjectNode(); node.put("playerUuid", playerUuid.toString()); node.put("playerName", playerName); node.put("playerColor", newRole.getColor()); node.put("groupDisplay", newRole.getDisplayName()); String jsonMessage = objectMapper.writeValueAsString(node); RedisPublisher.publish(RedisChannel.ROLE_BROADCAST, jsonMessage); logger.fine("Mensagem ROLE_BROADCAST publicada para " + playerName); }
        catch (JsonProcessingException e) { logger.log(Level.SEVERE, "Falha ao serializar mensagem ROLE_BROADCAST para " + playerName, e); } catch (Exception e) { logger.log(Level.WARNING, "Falha ao publicar mensagem ROLE_BROADCAST para " + playerName, e); }
    }

    private void handleClear(CommandSource sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, label, "clear <jogador | grupo>"); return; }
        final String targetInput = args[1];
        final CommandSource finalSender = sender;
        final RoleService finalRoleService = roleService;
        final ProfileService finalProfileService = profileService;
        final Logger finalLogger = logger;

        final Optional<Role> senderRoleOpt = getSenderPrimaryRole(finalSender);
        final boolean hasBypass = checkBypass(finalSender, senderRoleOpt);
        final int senderWeight = senderRoleOpt.map(Role::getWeight).orElse(hasBypass ? Integer.MAX_VALUE : 0);

        Optional<Role> roleOpt = finalRoleService.getRole(targetInput);
        if (roleOpt.isPresent()) {
            // --- Limpar por Grupo ---
            // <<< CORREÇÃO PONTO 3: Desativar /role clear group >>>
            Messages.error(finalSender, "O comando /role clear <grupo> está temporariamente desativado.");
            playSound(finalSender, SoundKeys.USAGE_ERROR);
            return;
            // <<< FIM CORREÇÃO >>>
        } else {
            // Passa os dados de bypass e peso do sender para o método
            clearRolesByPlayer(finalSender, targetInput, label, senderRoleOpt, hasBypass, senderWeight);
        }
    }

    private void clearRolesByPlayer(CommandSource sender, String targetInput, String label, Optional<Role> senderRoleOpt, boolean hasBypass, int senderWeight) {
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
                        final Profile latestProfile = profileService.getByUuid(targetUuid).orElseThrow(()->new IllegalStateException("Perfil sumiu")); List<PlayerRole> currentRoles = latestProfile.getRoles(); if (currentRoles == null) currentRoles = new ArrayList<>();
                        for (PlayerRole pr : currentRoles) { if (pr != null && !"default".equalsIgnoreCase(pr.getRoleName()) && pr.getStatus() == PlayerRole.Status.ACTIVE) { pr.setStatus(PlayerRole.Status.REMOVED); pr.setRemovedAt(System.currentTimeMillis()); pr.setPaused(false); pr.setPausedTimeRemaining(null); changed.set(true); roleService.getRole(pr.getRoleName()).ifPresent(r -> { lastRemovedRole.set(r); if(r.getType() == RoleType.VIP) vipRemoved.set(true); }); } }
                        ensureDefaultActiveIfNeeded(currentRoles, "dummy");
                        if (!changed.get()) { boolean defaultReactivated = currentRoles.stream().anyMatch(pr -> pr != null && "default".equalsIgnoreCase(pr.getRoleName()) && pr.getStatus() == PlayerRole.Status.ACTIVE); if (!defaultReactivated) { Messages.send(finalSender, Message.of(MessageKey.ROLE_WARN_ALREADY_DEFAULT).with("player", targetOriginalName)); playSound(finalSender, SoundKeys.NOTIFICATION); return; } }

                        Role calculatedPrimaryRole = calculatePrimaryRole(currentRoles);
                        if (calculatedPrimaryRole != null) { String newPrimaryName = calculatedPrimaryRole.getName(); if (!Objects.equals(latestProfile.getPrimaryRoleName(), newPrimaryName)) { latestProfile.setPrimaryRoleName(newPrimaryName); } } else { latestProfile.setPrimaryRoleName("default"); }
                        try { roleService.updatePauseState(targetUuid, currentRoles); } catch (Exception e) { /* log */ }
                        latestProfile.setRoles(currentRoles);
                        try { profileService.save(latestProfile); } catch (Exception e) { logger.log(Level.SEVERE, "[RoleClearP] FALHA SAVE", e); handleCommandError(finalSender, "salvar clear", e); return; }
                        roleService.publishSync(targetUuid);
                        Messages.send(finalSender, Message.of(MessageKey.ROLE_SUCCESS_CLEAR_PLAYER).with("player", targetOriginalName)); playSound(finalSender, SoundKeys.SUCCESS);
                        if (proxyServer.getPlayer(targetUuid).isPresent()) { RoleType typeForKick = vipRemoved.get() ? RoleType.VIP : (lastRemovedRole.get() != null ? lastRemovedRole.get().getType() : RoleType.DEFAULT); String displayForKick = lastRemovedRole.get() != null ? lastRemovedRole.get().getDisplayName() : "grupo"; RoleKickHandler.scheduleKick(targetUuid, typeForKick, RoleKickHandler.KickReason.REMOVED, displayForKick); } }
                } catch (Exception e) { handleCommandError(finalSender, "processar clear player", e); }
            }).exceptionally(ex -> handleCommandError(finalSender, "obter dados alvo clear player", ex));
        }).exceptionally(ex -> handleCommandError(finalSender, "resolver perfil clear player", ex));
    }


    private void handleList(CommandSource sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, label, "list <grupo>"); return; }
        final String groupNameInput = args[1].toLowerCase(); final CommandSource finalSender = sender;
        final Locale senderLocale = Messages.determineLocale(sender);
        Optional<Role> roleOpt = roleService.getRole(groupNameInput); if (roleOpt.isEmpty()) { Messages.send(finalSender, Message.of(MessageKey.ROLE_ERROR_GROUP_NOT_FOUND).with("group", groupNameInput)); playSound(finalSender, SoundKeys.USAGE_ERROR); return; }
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
                }).exceptionally(ex -> handleCommandError(finalSender, "listar grupo " + targetRoleDisplayName, ex));
    }

    // --- Métodos Auxiliares ---
    private void showHelp(CommandSource sender, String label) {
        final CommandSource finalSender = sender;
        final String finalLabel = label;
        final Locale senderLocale = Messages.determineLocale(finalSender);

        List<Map.Entry<String, MessageKey>> commands = List.of(
                Map.entry("/{label} info <jogador|grupo>", MessageKey.ROLE_HELP_INFO),
                Map.entry("/{label} list <grupo>", MessageKey.ROLE_HELP_LIST),
                Map.entry("/{label} add <jogador> <grupo> [duração] [-hidden]", MessageKey.ROLE_HELP_ADD),
                Map.entry("/{label} set <jogador> <grupo> [duração] [-hidden]", MessageKey.ROLE_HELP_SET),
                Map.entry("/{label} remove <jogador> <grupo>", MessageKey.ROLE_HELP_REMOVE),
                Map.entry("/{label} clear <jogador|grupo>", MessageKey.ROLE_HELP_CLEAR)
        );
        boolean hasOptional = commands.stream().anyMatch(e -> e.getKey().contains("["));

        Messages.send(finalSender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Roles"));

        commands.forEach(entry -> {
            String usage = entry.getKey().replace("{label}", finalLabel);
            String description = Messages.translate(entry.getValue(), senderLocale); // Traduz usando o locale
            Messages.send(finalSender, Message.of(MessageKey.COMMON_HELP_LINE)
                    .with("usage", usage)
                    .with("description", description)
            );
        });

        if (hasOptional) { Messages.send(finalSender, MessageKey.COMMON_HELP_FOOTER_FULL); }
        else { Messages.send(finalSender, MessageKey.COMMON_HELP_FOOTER_REQUIRED); }
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void sendUsage(CommandSource sender, String label, String usage) {
        Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/" + label + " " + usage));
        playSound(sender, SoundKeys.USAGE_ERROR);
    }

    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player player && soundPlayer != null) {
            try {
                soundPlayer.playSound(player, key);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao tocar som '" + key + "' para " + player.getUsername(), e);
            }
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
                return roleService.getSessionDataFromCache(p.getUniqueId())
                        .map(PlayerSessionData::getPrimaryRole)
                        .or(() -> {
                            try {
                                logger.warning("[RoleCommand] Cache miss para sender " + p.getUsername() + ", carregando sync...");
                                return Optional.of(roleService.loadPlayerDataAsync(p.getUniqueId()).join().getPrimaryRole());
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Falha ao obter role (sync fallback) para " + p.getUsername(), e);
                                return Optional.empty();
                            }
                        });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Erro ao obter role do sender " + p.getUsername(), e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private boolean checkBypass(CommandSource sender, Optional<Role> senderRole) {
        if (sender instanceof ConsoleCommandSource) {
            return true;
        }
        return senderRole.isPresent() && senderRole.get().getName().equalsIgnoreCase("master");
    }

    private int getSenderRoleWeight(CommandSource sender) {
        final Optional<Role> senderRoleOpt = getSenderPrimaryRole(sender);
        final boolean hasBypass = checkBypass(sender, senderRoleOpt);
        return senderRoleOpt.map(Role::getWeight).orElse(hasBypass ? Integer.MAX_VALUE : 0);
    }

    private Void handleCommandError(CommandSource sender, String action, Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) { cause = cause.getCause(); }
        logger.log(Level.SEVERE, "Erro durante ação '" + action + "' no RoleCommand", cause);
        Messages.send(sender, MessageKey.COMMAND_ERROR);
        playSound(sender, SoundKeys.ERROR);
        return null;
    }

    // --- Tab Completion ---
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
                    if (currentArg.startsWith("uuid:") && currentArg.length() > 5) completions.add(currentArg);
                    if (currentArg.startsWith("id:") && currentArg.length() > 3) completions.add(currentArg);
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
                List<String> durations = Arrays.asList("1s", "30s", "1m", "5m", "15m", "30m", "1h", "12h", "1d", "7d", "15d", "30d", "90d", "180d", "1y");
                durations.stream().filter(d -> d.startsWith(currentArg)).forEach(completions::add);
                if ("-hidden".startsWith(currentArg)) completions.add("-hidden");
            }
            else if (args.length == 5 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("set"))) {
                if (!args[3].equalsIgnoreCase("-hidden") && "-hidden".startsWith(currentArg)) {
                    completions.add("-hidden");
                }
            }
        } catch (Exception e) { logger.log(Level.SEVERE, "Erro tab completion RoleCommand", e); }
        return completions.stream().distinct().sorted().collect(Collectors.toList());
    }

} // Fim da classe RoleCommand