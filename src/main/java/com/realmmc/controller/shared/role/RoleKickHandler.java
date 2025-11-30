package com.realmmc.controller.shared.role;

import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.utils.TaskScheduler;

import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoleKickHandler {

    private static final Logger LOGGER = Logger.getLogger(RoleKickHandler.class.getName());
    private static final long CHECK_INTERVAL_SECONDS = 2;

    private static final long KICK_DELAY_SECONDS_ADD_SET = 10;
    private static final long KICK_DELAY_SECONDS_REMOVED = 10;
    private static final long KICK_DELAY_SECONDS_EXPIRED = 10;

    private static final Map<UUID, KickInfo> scheduledKicks = new ConcurrentHashMap<>();
    private static ScheduledFuture<?> checkTask = null;
    private static boolean running = false;

    @FunctionalInterface
    public interface PlatformKicker {
        void kickPlayer(UUID uuid, String formattedKickMessage);
    }
    private static PlatformKicker platformKicker = null;

    private static class KickInfo {
        final RoleType roleType;
        final KickReason reason;
        final String groupDisplayName;
        final long kickAtMillis;

        KickInfo(RoleType roleType, KickReason reason, String groupDisplayName, long kickAtMillis) {
            this.roleType = roleType;
            this.reason = reason;
            this.groupDisplayName = groupDisplayName;
            this.kickAtMillis = kickAtMillis;
        }
    }

    public enum KickReason {
        ADD_SET,
        REMOVED,
        EXPIRED
    }

    public static synchronized void initialize(PlatformKicker kicker) {
        if (running) return;
        Objects.requireNonNull(kicker, "PlatformKicker cannot be null.");
        platformKicker = kicker;

        try {
            checkTask = TaskScheduler.runAsyncTimer(RoleKickHandler::checkScheduledKicks,
                    CHECK_INTERVAL_SECONDS,
                    CHECK_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
            running = true;
            LOGGER.info("[RoleKickHandler] Initialized.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[RoleKickHandler] Error starting task.", e);
            running = false;
        }
    }

    public static synchronized void shutdown() {
        running = false;
        if (checkTask != null) {
            checkTask.cancel(false);
            checkTask = null;
        }
        scheduledKicks.clear();
        platformKicker = null;
    }

    public static void scheduleKick(UUID uuid, RoleType roleType, KickReason reason, String groupDisplayName) {
        if (!running || uuid == null) return;

        long delayMillis;
        switch (reason) {
            case ADD_SET: delayMillis = TimeUnit.SECONDS.toMillis(KICK_DELAY_SECONDS_ADD_SET); break;
            case REMOVED: delayMillis = TimeUnit.SECONDS.toMillis(KICK_DELAY_SECONDS_REMOVED); break;
            default: delayMillis = TimeUnit.SECONDS.toMillis(KICK_DELAY_SECONDS_EXPIRED);
        }
        long kickAt = System.currentTimeMillis() + delayMillis;

        KickInfo info = new KickInfo(roleType, reason, groupDisplayName, kickAt);
        scheduledKicks.put(uuid, info);
        LOGGER.info("[RoleKickHandler] Kick agendado para " + uuid + " em " + (delayMillis/1000) + "s.");
    }

    public static void cancelKick(UUID uuid) {
        if (scheduledKicks.remove(uuid) != null) {
            LOGGER.info("[RoleKickHandler] Kick cancelado para " + uuid + " (Jogador reconectou ou saiu).");
        }
    }

    private static void checkScheduledKicks() {
        if (!running || scheduledKicks.isEmpty() || platformKicker == null) return;

        long now = System.currentTimeMillis();
        new ConcurrentHashMap<>(scheduledKicks).forEach((uuid, info) -> {
            if (now >= info.kickAtMillis) {
                if (scheduledKicks.remove(uuid, info)) {
                    String kickMessage = formatKickMessage(info);
                    try {
                        platformKicker.kickPlayer(uuid, kickMessage);
                    } catch (Exception e) {
                        LOGGER.warning("Erro ao kickar " + uuid);
                    }
                }
            }
        });
    }

    private static String formatKickMessage(KickInfo info) {
        MessageKey key;
        if (info.roleType == RoleType.VIP) {
            switch (info.reason) {
                case ADD_SET: key = MessageKey.ROLE_KICK_ADD_SET_VIP; break;
                case REMOVED: key = MessageKey.ROLE_KICK_REMOVED_VIP; break;
                default: key = MessageKey.ROLE_KICK_EXPIRED_VIP;
            }
        } else {
            switch (info.reason) {
                case ADD_SET: key = MessageKey.ROLE_KICK_ADD_SET_STAFF; break;
                case REMOVED: key = MessageKey.ROLE_KICK_REMOVED_STAFF; break;
                default: key = MessageKey.ROLE_KICK_EXPIRED_STAFF;
            }
        }
        return Messages.translate(Message.of(key).with("group", info.groupDisplayName));
    }
}