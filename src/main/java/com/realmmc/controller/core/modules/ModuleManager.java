package com.realmmc.controller.core.modules;

import lombok.Getter;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.security.CodeSource;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ModuleManager {
    @Getter
    private static ModuleManager instance;

    private final Map<String, CoreModule> modules = new HashMap<>();
    private final List<CoreModule> enabledModulesInOrder = new ArrayList<>();
    private final Logger logger;

    private final Set<SystemType> claimedSystems = new HashSet<>();

    public ModuleManager(Logger logger) {
        this.logger = logger;
        instance = this;
    }

    public void claimSystem(SystemType type) {
        claimedSystems.add(type);
        logger.info("[ModuleManager] O sistema " + type.name() + " foi reivindicado por um plugin externo. O módulo padrão Controller NÃO será ativado.");
    }

    public boolean isClaimed(SystemType type) {
        return claimedSystems.contains(type);
    }

    public void autoRegisterModules(AutoRegister.Platform platform, Class<?> mainClass) {
        logger.info("A procurar por módulos de registo automático para plataforma " + platform + "...");
        String basePackage = "com.realmmc.controller.modules";

        try {
            Set<Class<?>> classes = getClassesInPackage(basePackage, mainClass, platform);
            for (Class<?> clazz : classes) {
                if (AbstractCoreModule.class.isAssignableFrom(clazz) && !clazz.isInterface() && clazz.isAnnotationPresent(AutoRegister.class)) {
                    AutoRegister annotation = clazz.getAnnotation(AutoRegister.class);

                    List<AutoRegister.Platform> platforms = Arrays.asList(annotation.platforms());

                    if (platforms.contains(AutoRegister.Platform.ALL) || platforms.contains(platform)) {
                        try {
                            Constructor<?> constructor = clazz.getConstructor(Logger.class);
                            CoreModule module = (CoreModule) constructor.newInstance(logger);
                            registerModule(module);
                        } catch (NoSuchMethodException ignored) {
                        } catch (Throwable e) {
                            logger.log(Level.SEVERE, "Falha ao instanciar módulo " + clazz.getSimpleName(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro crítico ao procurar por módulos de auto-registo.", e);
        }
    }

    private Set<Class<?>> getClassesInPackage(String packageName, Class<?> mainClass, AutoRegister.Platform currentPlatform) throws Exception {
        Set<Class<?>> classes = new HashSet<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = mainClass.getClassLoader();
        CodeSource src = mainClass.getProtectionDomain().getCodeSource();

        if (src != null) {
            URI jarUri = src.getLocation().toURI();
            try (JarFile jarFile = new JarFile(new File(jarUri))) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(path) || !name.endsWith(".class")) continue;

                    if (currentPlatform == AutoRegister.Platform.PROXY && name.contains("/spigot/")) {
                        continue;
                    }
                    if (currentPlatform == AutoRegister.Platform.SPIGOT && name.contains("/proxy/")) {
                        continue;
                    }

                    String className = name.replace('/', '.').substring(0, name.length() - 6);
                    try {
                        classes.add(Class.forName(className, false, classLoader));
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return classes;
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

    public void enableAllModules() {
        enabledModulesInOrder.clear();
        try {
            List<CoreModule> sortedModules = sortModulesByDependency();
            logger.info("Ordem de carregamento dos módulos: " + sortedModules.stream().map(CoreModule::getName).toList());

            for (CoreModule module : sortedModules) {
                enableModule(module);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao ordenar e habilitar módulos", e);
        }
    }

    public void disableAllModules() {
        ListIterator<CoreModule> iterator = enabledModulesInOrder.listIterator(enabledModulesInOrder.size());
        while (iterator.hasPrevious()) {
            CoreModule module = iterator.previous();
            try {
                if (module.isEnabled()) {
                    module.disable();
                    logger.info("Módulo desabilitado: " + module.getName());
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao desabilitar módulo " + module.getName(), e);
            }
        }
        enabledModulesInOrder.clear();
    }

    public void enableModule(CoreModule module) {
        try {
            if (!module.isEnabled()) {
                module.enable();
                logger.info("Módulo habilitado: " + module.getName());
                if (!enabledModulesInOrder.contains(module)) {
                    enabledModulesInOrder.add(module);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao habilitar módulo " + module.getName(), e);
        }
    }

    public void disableModule(CoreModule module) {
        try {
            if (module.isEnabled()) {
                module.disable();
                logger.info("Módulo desabilitado: " + module.getName());
                enabledModulesInOrder.remove(module);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao desabilitar módulo " + module.getName(), e);
        }
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
                continue;
            }
            if (visiting.contains(dependencyName)) {
                throw new IllegalStateException("Dependência circular detectada: " + moduleName + " -> " + dependencyName);
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