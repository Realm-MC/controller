package com.realmmc.controller.modules.permission;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.permission.PermissionService;
import com.realmmc.controller.shared.role.RoleService;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.ALL})
public class PermissionModule extends AbstractCoreModule {

    public PermissionModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "Permission";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Módulo responsável pelo sistema de permissões e cargos";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"Database"};
    }

    @Override
    public int getPriority() {
        return 15;
    }

    @Override
    protected void onEnable() throws Exception {
        logger.info("Inicializando sistema de permissões e cargos...");
        RoleService roleService = new RoleService();
        PermissionService permissionService = new PermissionService(roleService);
        ServiceRegistry.getInstance().registerService(RoleService.class, roleService);
        ServiceRegistry.getInstance().registerService(PermissionService.class, permissionService);
        logger.info("Sistema de permissões e cargos inicializado com sucesso!");
    }

    @Override
    protected void onDisable() {
        logger.info("Finalizando sistema de permissões...");
        ServiceRegistry.getInstance().getService(PermissionService.class).ifPresent(PermissionService::clearAllCache);
        ServiceRegistry.getInstance().unregisterService(RoleService.class);
        ServiceRegistry.getInstance().unregisterService(PermissionService.class);
        logger.info("Sistema de permissões finalizado!");
    }
}