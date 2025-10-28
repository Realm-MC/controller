package com.realmmc.controller.shared.role;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.utils.TaskScheduler; // Para agendar a verificação

import java.util.Map;
import java.util.UUID;
import java.util.Objects; // Import Objects
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerencia o agendamento e execução de kicks relacionados a mudanças de Role (VIP/Staff).
 */
public class RoleKickHandler {

    private static final Logger LOGGER = Logger.getLogger(RoleKickHandler.class.getName());
    // Intervalo da tarefa que verifica kicks pendentes (em segundos)
    private static final long CHECK_INTERVAL_SECONDS = 2;

    // <<< CORREÇÃO PONTO 2: Alterar todos os delays para 15 segundos >>>
    private static final long KICK_DELAY_SECONDS_ADD_SET = 15;
    private static final long KICK_DELAY_SECONDS_REMOVED = 15;
    private static final long KICK_DELAY_SECONDS_EXPIRED = 15;
    // <<< FIM CORREÇÃO >>>

    // Armazena os kicks agendados: UUID -> KickInfo
    private static final Map<UUID, KickInfo> scheduledKicks = new ConcurrentHashMap<>();
    // Tarefa que verifica periodicamente os kicks
    private static ScheduledFuture<?> checkTask = null;
    // Para cancelar a task no shutdown
    private static boolean running = false;

    // Interface para a execução do kick (implementada por Spigot/Velocity)
    @FunctionalInterface
    public interface PlatformKicker {
        void kickPlayer(UUID uuid, String formattedKickMessage);
    }
    private static PlatformKicker platformKicker = null; // Será injetado pela plataforma

    // Enum para razões de kick (usado para escolher a mensagem)
    public enum KickReason {
        ADD_SET, // Jogador recebeu/teve setado um grupo
        REMOVED, // Grupo foi removido
        EXPIRED  // Grupo expirou
    }

    // Classe interna para guardar informações do kick agendado
    private static class KickInfo {
        final RoleType roleType; // VIP ou STAFF/DEFAULT
        final KickReason reason;
        final String groupDisplayName; // Nome formatado do grupo para a msg
        final long kickAtMillis; // Timestamp de quando o kick deve ocorrer

        KickInfo(RoleType roleType, KickReason reason, String groupDisplayName, long kickAtMillis) {
            this.roleType = roleType;
            this.reason = reason;
            this.groupDisplayName = groupDisplayName;
            this.kickAtMillis = kickAtMillis;
        }
    }

    /**
     * Inicia o serviço de verificação de kicks. Deve ser chamado uma vez (ex: no onEnable do módulo).
     * @param kicker A implementação específica da plataforma para executar o kick.
     */
    public static synchronized void initialize(PlatformKicker kicker) {
        if (running) {
            LOGGER.warning("RoleKickHandler já inicializado.");
            return;
        }
        Objects.requireNonNull(kicker, "PlatformKicker não pode ser nulo.");
        platformKicker = kicker;

        // Agenda a tarefa periódica para verificar kicks
        try {
            checkTask = TaskScheduler.runAsyncTimer(RoleKickHandler::checkScheduledKicks,
                    CHECK_INTERVAL_SECONDS, // Delay inicial
                    CHECK_INTERVAL_SECONDS, // Intervalo
                    TimeUnit.SECONDS);
            running = true;
            LOGGER.info("RoleKickHandler inicializado. Verificando kicks a cada " + CHECK_INTERVAL_SECONDS + " segundos.");
        } catch (IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "Falha ao inicializar RoleKickHandler: TaskScheduler não disponível?", e);
            running = false; // Garante que não está marcado como rodando
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro inesperado ao agendar tarefa de verificação de kicks.", e);
            running = false;
        }
    }

    /**
     * Para o serviço de verificação de kicks. Deve ser chamado no onDisable do módulo.
     */
    public static synchronized void shutdown() {
        running = false; // Sinaliza para parar a verificação
        if (checkTask != null) {
            try {
                checkTask.cancel(false); // Tenta cancelar a tarefa
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao cancelar a tarefa de verificação de kicks.", e);
            }
            checkTask = null;
        }
        scheduledKicks.clear(); // Limpa kicks pendentes
        platformKicker = null; // Limpa referência
        LOGGER.info("RoleKickHandler finalizado.");
    }

    /**
     * Agenda um kick para um jogador após uma mudança de role (se aplicável).
     * Cancela qualquer kick anterior agendado para o mesmo jogador.
     * @param uuid O UUID do jogador.
     * @param roleType O tipo do grupo afetado (VIP ou STAFF/DEFAULT).
     * @param reason A razão da mudança (ADD_SET, REMOVED, EXPIRED).
     * @param groupDisplayName O nome formatado do grupo para usar na mensagem de kick.
     */
    public static void scheduleKick(UUID uuid, RoleType roleType, KickReason reason, String groupDisplayName) {
        if (!running || uuid == null || roleType == null || reason == null || groupDisplayName == null) {
            if (!running) LOGGER.warning("Tentativa de agendar kick enquanto RoleKickHandler não está rodando.");
            return;
        }

        // Determina o delay com base na razão (agora fixo)
        long delayMillis;
        switch (reason) {
            case ADD_SET: delayMillis = TimeUnit.SECONDS.toMillis(KICK_DELAY_SECONDS_ADD_SET); break;
            case REMOVED: delayMillis = TimeUnit.SECONDS.toMillis(KICK_DELAY_SECONDS_REMOVED); break;
            case EXPIRED: delayMillis = TimeUnit.SECONDS.toMillis(KICK_DELAY_SECONDS_EXPIRED); break;
            default: delayMillis = TimeUnit.SECONDS.toMillis(15); // Fallback
        }
        long kickAt = System.currentTimeMillis() + delayMillis;

        KickInfo info = new KickInfo(roleType, reason, groupDisplayName, kickAt);
        scheduledKicks.put(uuid, info);
        LOGGER.fine("Kick agendado para UUID " + uuid + " (Razão: " + reason + ", Grupo: " + groupDisplayName + ") em " + delayMillis + "ms.");
    }

    /**
     * Método executado periodicamente para verificar e executar kicks agendados.
     */
    private static void checkScheduledKicks() {
        if (!running || scheduledKicks.isEmpty() || platformKicker == null) {
            return;
        }

        long now = System.currentTimeMillis();
        new ConcurrentHashMap<>(scheduledKicks).forEach((uuid, info) -> {
            if (now >= info.kickAtMillis) {
                if (scheduledKicks.remove(uuid, info)) {
                    LOGGER.fine("Processando kick agendado para UUID: " + uuid + " (Razão: " + info.reason + ")");
                    String kickMessage = formatKickMessage(info);
                    try {
                        platformKicker.kickPlayer(uuid, kickMessage);
                        LOGGER.info("Kick executado para UUID: " + uuid);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Erro ao executar kick via PlatformKicker para UUID: " + uuid, e);
                    }
                }
            }
        });
    }

    /**
     * Formata a mensagem de kick com base no tipo de grupo e razão.
     * @param info Os detalhes do kick agendado.
     * @return A mensagem de kick formatada (com MiniMessage).
     */
    private static String formatKickMessage(KickInfo info) {
        MessageKey key;
        if (info.roleType == RoleType.VIP) {
            switch (info.reason) {
                case ADD_SET: key = MessageKey.ROLE_KICK_ADD_SET_VIP; break;
                case REMOVED: key = MessageKey.ROLE_KICK_REMOVED_VIP; break;
                case EXPIRED: key = MessageKey.ROLE_KICK_EXPIRED_VIP; break;
                default: key = MessageKey.ROLE_KICK_GENERIC;
            }
        } else { // STAFF ou DEFAULT
            switch (info.reason) {
                case ADD_SET: key = MessageKey.ROLE_KICK_ADD_SET_STAFF; break;
                case REMOVED: key = MessageKey.ROLE_KICK_REMOVED_STAFF; break;
                case EXPIRED: key = MessageKey.ROLE_KICK_EXPIRED_STAFF; break;
                default: key = MessageKey.ROLE_KICK_GENERIC;
            }
        }
        // Traduz usando o locale padrão do servidor
        return Messages.translate(Message.of(key).with("group", info.groupDisplayName));
    }
}