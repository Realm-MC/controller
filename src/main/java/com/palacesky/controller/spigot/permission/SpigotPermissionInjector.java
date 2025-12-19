package com.palacesky.controller.spigot.permission;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpigotPermissionInjector implements Listener {

    private final Logger logger;
    private final RoleService roleService;
    private final Plugin plugin;
    private final Field permissibleField;
    private final Map<UUID, PermissibleBase> originalPermissibles = new ConcurrentHashMap<>();

    public SpigotPermissionInjector(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        try {
            this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        } catch (IllegalStateException e) {
            logger.log(Level.SEVERE, "[SpigotPerm] Critical Error: RoleService not found during initialization!", e);
            throw new RuntimeException("Failed to initialize SpigotPermissionInjector: RoleService missing.", e);
        }

        Field tempField = null;
        String version = null;
        try {
            String bukkitVersion = Bukkit.getServer().getBukkitVersion();
            version = bukkitVersion.split("-")[0];

            Class<?> craftHumanEntityClass;
            try {
                craftHumanEntityClass = Class.forName("org.bukkit.craftbukkit.entity.CraftHumanEntity");
                logger.fine("[SpigotPerm] Attempting to find 'perm' field in 1.17+ structure...");
            } catch (ClassNotFoundException e) {
                version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                logger.fine("[SpigotPerm] Attempting to find 'perm' field in < 1.17 structure (" + version + ")...");
                craftHumanEntityClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftHumanEntity");
            }

            tempField = craftHumanEntityClass.getDeclaredField("perm");
            tempField.setAccessible(true);
            logger.info("[SpigotPerm] 'perm' field located successfully via reflection.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[SpigotPerm] !!! CRITICAL FAILURE LOCATING 'perm' FIELD VIA REFLECTION !!!");
            logger.log(Level.SEVERE, "[SpigotPerm] Bukkit Version Detected: " + Bukkit.getServer().getBukkitVersion() + (version != null ? " | NMS Package Version: " + version : ""));
            logger.log(Level.SEVERE, "[SpigotPerm] Custom permission system will NOT work.");
            logger.log(Level.SEVERE, "[SpigotPerm] Error: ", e);
        }
        this.permissibleField = tempField;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLoginInject(PlayerLoginEvent event) {
        if (permissibleField == null || event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            if (permissibleField == null && event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
                logger.warning("[SpigotPerm] Injection skipped for " + event.getPlayer().getName() + " - reflection failed during initialization.");
            }
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        try {
            PermissibleBase currentPermissible = (PermissibleBase) permissibleField.get(player);

            if (!(currentPermissible instanceof RealmPermissible)) {
                originalPermissibles.put(uuid, currentPermissible);
                RealmPermissible customPerm = new RealmPermissible(player, roleService);
                permissibleField.set(player, customPerm);
                logger.finer("[SpigotPerm] RealmPermissible injected for " + player.getName());
            } else {
                logger.finer("[SpigotPerm] RealmPermissible was already injected for " + player.getName());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[SpigotPerm] Failed to inject RealmPermissible for " + player.getName(), e);
            String translatedKick = Messages.translate(MessageKey.KICK_PERMISSION_SYSTEM_ERROR);
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, translatedKick);
            originalPermissibles.remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuitRestore(PlayerQuitEvent event) {
        if (permissibleField == null) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PermissibleBase original = originalPermissibles.remove(uuid);

        if (original != null) {
            try {
                Object currentPerm = permissibleField.get(player);
                if (currentPerm instanceof RealmPermissible) {
                    permissibleField.set(player, original);
                    logger.finer("[SpigotPerm] Original PermissibleBase restored for " + player.getName());
                } else {
                    logger.warning("[SpigotPerm] PermissibleBase for " + player.getName() + " was not RealmPermissible on quit. Not restored.");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[SpigotPerm] Failed to restore original PermissibleBase for " + player.getName(), e);
            }
        } else {
            try {
                Object currentPerm = permissibleField.get(player);
                if (currentPerm instanceof RealmPermissible) {
                    logger.warning("[SpigotPerm] RealmPermissible found in " + player.getName() + " on quit, but no original saved. Attempting to restore default (MAY FAIL).");
                    permissibleField.set(player, new PermissibleBase(player));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[SpigotPerm] Failed to attempt clean up of RealmPermissible on quit for " + player.getName(), e);
            }
        }
    }

    public void cleanupOnDisable() {
        originalPermissibles.clear();
        logger.info("[SpigotPerm] Original permissibles map cleared.");
    }
}