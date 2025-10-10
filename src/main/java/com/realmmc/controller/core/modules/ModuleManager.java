package com.realmmc.controller.core.modules;

import lombok.Getter;

import java.util.*;
import java.util.logging.Level;
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
            if (!module.isEnabled()) {
                module.enable();
                logger.info("Módulo habilitado: " + name);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao habilitar módulo " + name, e);
        }
    }

    public void disableModule(String name) {
        CoreModule module = modules.get(name);
        if (module == null) {
            logger.warning("Módulo não encontrado: " + name);
            return;
        }

        try {
            if (module.isEnabled()) {
                module.disable();
                logger.info("Módulo desabilitado: " + name);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao desabilitar módulo " + name, e);
        }
    }

    public void enableAllModules() {
        try {
            List<CoreModule> sortedModules = sortModulesByDependency();
            logger.info("Ordem de carregamento dos módulos: " + sortedModules.stream().map(CoreModule::getName).toList());
            sortedModules.forEach(module -> enableModule(module.getName()));
        } catch (Exception e) {
            logger.severe("Falha ao ordenar módulos por dependência: " + e.getMessage());
        }
    }

    public void disableAllModules() {
        modules.values().stream()
                .sorted(Comparator.comparingInt(CoreModule::getPriority).reversed())
                .forEach(module -> disableModule(module.getName()));
    }

    private List<CoreModule> sortModulesByDependency() throws IllegalStateException {
        List<CoreModule> sortedList = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        List<CoreModule> initialList = new ArrayList<>(modules.values());
        initialList.sort(Comparator.comparingInt(CoreModule::getPriority));

        for (CoreModule module : initialList) {
            if (!visited.contains(module.getName())) {
                visitModule(module, visited, visiting, sortedList);
            }
        }

        return sortedList;
    }

    private void visitModule(CoreModule module, Set<String> visited, Set<String> visiting, List<CoreModule> sortedList) {
        String moduleName = module.getName();

        visiting.add(moduleName);

        for (String dependencyName : module.getDependencies()) {
            CoreModule dependency = modules.get(dependencyName);

            if (dependency == null) {
                logger.warning("A dependência '" + dependencyName + "' para o módulo '" + moduleName + "' não foi encontrada.");
                continue;
            }

            if (visiting.contains(dependencyName)) {
                throw new IllegalStateException("Dependência circular detetada: " + moduleName + " -> " + dependencyName);
            }

            if (!visited.contains(dependencyName)) {
                visitModule(dependency, visited, visiting, sortedList);
            }
        }

        visiting.remove(moduleName);
        visited.add(moduleName);

        sortedList.add(module);
    }


    public Optional<CoreModule> getModule(String name) {
        return Optional.ofNullable(modules.get(name));
    }

    public Collection<CoreModule> getAllModules() {
        return Collections.unmodifiableCollection(modules.values());
    }
}