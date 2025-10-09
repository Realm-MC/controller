package com.realmmc.controller.core.modules;

import lombok.Getter;

import java.util.*;
import java.util.logging.Logger;

public class ModuleManager {
    @Getter
    private static ModuleManager instance;

    private final Map<String, CoreModule> modules = new HashMap<>();
    private final Logger logger;

    public ModuleManager(Logger logger) {
        this.logger = logger;
        instance = this;
    }

    public void registerModule(CoreModule module) {
        String name = module.getName();
        if (modules.containsKey(name)) {
            logger.warning("Módulo " + name + " já está registrado!");
            return;
        }

        modules.put(name, module);
        logger.info("Módulo registrado: " + name);
    }

    public void enableModule(String name) {
        CoreModule module = modules.get(name);
        if (module == null) {
            logger.warning("Módulo não encontrado: " + name);
            return;
        }

        try {
            module.enable();
            logger.info("Módulo habilitado: " + name);
        } catch (Exception e) {
            logger.severe("Erro ao habilitar módulo " + name + ": " + e.getMessage());
        }
    }

    public void disableModule(String name) {
        CoreModule module = modules.get(name);
        if (module == null) {
            logger.warning("Módulo não encontrado: " + name);
            return;
        }

        try {
            module.disable();
            logger.info("Módulo desabilitado: " + name);
        } catch (Exception e) {
            logger.severe("Erro ao desabilitar módulo " + name + ": " + e.getMessage());
        }
    }

    public void enableAllModules() {
        modules.values().stream()
                .sorted(Comparator.comparingInt(CoreModule::getPriority))
                .forEach(module -> enableModule(module.getName()));
    }

    public void disableAllModules() {
        modules.keySet().forEach(this::disableModule);
    }

    public Optional<CoreModule> getModule(String name) {
        return Optional.ofNullable(modules.get(name));
    }

    public Collection<CoreModule> getAllModules() {
        return Collections.unmodifiableCollection(modules.values());
    }
}