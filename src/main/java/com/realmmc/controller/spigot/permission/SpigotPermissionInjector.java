package com.realmmc.controller.spigot.permission;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissibleBase; // Import necessário
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Injeta o RealmPermissible nos jogadores do Spigot via reflexão no login
 * e restaura o original no logout.
 */
public class SpigotPermissionInjector implements Listener {

    private final Logger logger;
    private final RoleService roleService;
    private final Plugin plugin;
    private final Field permissibleField; // Campo 'perm' refletido
    private final Map<UUID, PermissibleBase> originalPermissibles = new ConcurrentHashMap<>();

    public SpigotPermissionInjector(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        try {
            this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "Erro crítico: RoleService não encontrado ao iniciar SpigotPermissionInjector!", e);
            throw new RuntimeException("Falha ao inicializar SpigotPermissionInjector: RoleService ausente.", e);
        }

        // --- Reflexão para encontrar o campo 'perm' ---
        Field tempField = null;
        String version = null; // Para log
        try {
            // Usa o Bukkit API para obter a versão NMS
            String bukkitVersion = Bukkit.getServer().getBukkitVersion(); // Ex: 1.21-R0.1-SNAPSHOT
            version = bukkitVersion.split("-")[0]; // Tenta extrair "1.21"

            // Heurística para encontrar a classe CraftHumanEntity correta (pode precisar de ajuste)
            // Pacote mudou em 1.17+ para não incluir versão
            Class<?> craftHumanEntityClass;
            try {
                craftHumanEntityClass = Class.forName("org.bukkit.craftbukkit.entity.CraftHumanEntity"); // 1.17+
                logger.fine("[PermissionInjector] Tentando encontrar campo 'perm' na estrutura 1.17+...");
            } catch (ClassNotFoundException e) {
                // Fallback para estrutura < 1.17 (incluindo versão no pacote)
                version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3]; // Formato antigo: v1_16_R3
                logger.fine("[PermissionInjector] Tentando encontrar campo 'perm' na estrutura < 1.17 (" + version + ")...");
                craftHumanEntityClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftHumanEntity");
            }

            // Tenta acessar o campo 'perm'
            tempField = craftHumanEntityClass.getDeclaredField("perm");
            tempField.setAccessible(true);
            logger.info("[PermissionInjector] Campo 'perm' localizado com sucesso via reflexão.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[PermissionInjector] !!! FALHA CRÍTICA AO LOCALIZAR CAMPO 'perm' VIA REFLEXÃO !!!");
            logger.log(Level.SEVERE, "[PermissionInjector] Bukkit Version Detectada: " + Bukkit.getServer().getBukkitVersion() + (version != null ? " | NMS Package Version: " + version : ""));
            logger.log(Level.SEVERE, "[PermissionInjector] O sistema de permissões customizado NÃO funcionará.");
            logger.log(Level.SEVERE, "[PermissionInjector] Erro: ", e);
            // Permite que o plugin continue, mas permissões não serão injetadas
        }
        this.permissibleField = tempField; // Pode ser null se a reflexão falhou
    }

    /**
     * No evento de Login (após permissões serem pré-carregadas pelo SessionService),
     * injeta o RealmPermissible.
     */
    @EventHandler(priority = EventPriority.HIGH) // Roda DEPOIS do SessionService
    public void onPlayerLoginInject(PlayerLoginEvent event) {
        if (permissibleField == null || event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            if (permissibleField == null && event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
                logger.warning("[PermissionInjector] Injeção pulada para " + event.getPlayer().getName() + " - falha na inicialização da reflexão.");
            }
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        try {
            // Obtém o PermissibleBase atual (pode ser o padrão ou de outro plugin)
            PermissibleBase currentPermissible = (PermissibleBase) permissibleField.get(player);

            // Só injeta se não for o nosso e guarda o original
            if (!(currentPermissible instanceof RealmPermissible)) {
                originalPermissibles.put(uuid, currentPermissible); // Guarda o que estava lá
                RealmPermissible customPerm = new RealmPermissible(player, roleService);
                permissibleField.set(player, customPerm);
                logger.finer("[PermissionInjector] RealmPermissible injetado para " + player.getName());
            } else {
                logger.finer("[PermissionInjector] RealmPermissible já estava injetado para " + player.getName());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[PermissionInjector] Falha ao injetar RealmPermissible para " + player.getName(), e);
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "§cErro ao inicializar sistema de permissões.");
            originalPermissibles.remove(uuid); // Limpa se guardou algo
        }
    }

    /**
     * No evento de Quit, restaura o PermissibleBase original.
     */
    @EventHandler(priority = EventPriority.MONITOR) // Roda por último
    public void onPlayerQuitRestore(PlayerQuitEvent event) {
        if (permissibleField == null) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PermissibleBase original = originalPermissibles.remove(uuid);

        if (original != null) {
            try {
                // Verifica se o Permissible atual é o nosso antes de restaurar
                Object currentPerm = permissibleField.get(player);
                if (currentPerm instanceof RealmPermissible) {
                    permissibleField.set(player, original);
                    logger.finer("[PermissionInjector] PermissibleBase original restaurado para " + player.getName());
                } else {
                    logger.warning("[PermissionInjector] PermissibleBase para " + player.getName() + " não era RealmPermissible no quit. Não restaurado.");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[PermissionInjector] Falha ao restaurar PermissibleBase original para " + player.getName(), e);
            }
        } else {
            // Se não tínhamos um original guardado, apenas garante que o nosso não está mais lá (caso raro)
            try {
                Object currentPerm = permissibleField.get(player);
                if (currentPerm instanceof RealmPermissible) {
                    logger.warning("[PermissionInjector] RealmPermissible encontrado em " + player.getName() + " no quit, mas sem original guardado. Tentando restaurar padrão (PODE FALHAR).");
                    permissibleField.set(player, new PermissibleBase(player)); // Tenta restaurar padrão
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[PermissionInjector] Falha ao tentar limpar RealmPermissible no quit para " + player.getName(), e);
            }
        }
    }

    /**
     * Limpa o mapa de permissíveis originais (chamado no onDisable do plugin).
     */
    public void cleanupOnDisable() {
        originalPermissibles.clear();
        logger.info("[PermissionInjector] Mapa de permissíveis originais limpo.");
    }
}