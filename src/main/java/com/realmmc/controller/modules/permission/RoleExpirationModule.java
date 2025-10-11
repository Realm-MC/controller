package com.realmmc.controller.modules.permission;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.spigot.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.SPIGOT})
public class RoleExpirationModule extends AbstractCoreModule {

    private BukkitTask expirationTask;

    public RoleExpirationModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "RoleExpiration";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Verifica e remove cargos temporários expirados dos jogadores.";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"Permission", "Profile"};
    }

    @Override
    public int getPriority() {
        return 35;
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Iniciando tarefa de verificação de expiração de cargos...");
        startExpirationTask();
    }

    @Override
    protected void onDisable() {
        if (expirationTask != null) {
            expirationTask.cancel();
            logger.info("Tarefa de verificação de expiração de cargos parada.");
        }
    }

    private void startExpirationTask() {
        ProfileService profileService = ServiceRegistry.getInstance()
                .getService(ProfileService.class)
                .orElseThrow(() -> new IllegalStateException("ProfileService não encontrado para a tarefa de expiração!"));

        expirationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getInstance(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                profileService.getByUuid(player.getUniqueId())
                        .ifPresent(profileService::checkAndRemoveExpiredRoles);
            }
        }, 20L * 60, 20L * 60);
    }
}