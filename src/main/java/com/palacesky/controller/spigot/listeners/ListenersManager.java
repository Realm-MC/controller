package com.palacesky.controller.spigot.listeners;

import com.palacesky.controller.shared.annotations.Listeners;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Level;

public final class ListenersManager {

    private ListenersManager() {
    }

    public static void registerAll(Plugin plugin) {
        final String basePackage = "com.palacesky.controller.spigot.listeners";
        ClassLoader cl = plugin.getClass().getClassLoader();
        try {
            File codeSource = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            Set<Class<?>> discovered = findAnnotatedClasses(codeSource, basePackage, Listeners.class, cl);
            for (Class<?> clazz : discovered) {
                try {
                    Object instance;
                    try {
                        Constructor<?> ctor = clazz.getDeclaredConstructor();
                        ctor.setAccessible(true);
                        instance = ctor.newInstance();
                    } catch (NoSuchMethodException e) {
                        Field instanceField = clazz.getField("INSTANCE");
                        instance = instanceField.get(null);
                    }
                    if (instance instanceof Listener) {
                        Bukkit.getPluginManager().registerEvents((Listener) instance, plugin);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[Listeners] Falha ao registrar listener: " + clazz.getName(), e);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Listeners] Falha no scan de listeners", e);
        }
    }

    private static Set<Class<?>> findAnnotatedClasses(File codeSource,
                                                      String basePackage,
                                                      Class<Listeners> annotation,
                                                      ClassLoader classLoader) {
        Set<Class<?>> result = new HashSet<>();
        String basePath = basePackage.replace('.', '/');
        if (codeSource.isDirectory()) {
            Path start = Paths.get(codeSource.getAbsolutePath(), basePath);
            if (Files.exists(start)) {
                try {
                    Files.walk(start)
                            .filter(p -> p.toString().endsWith(".class"))
                            .forEach(p -> {
                                String fqcn = toClassName(codeSource.toPath(), p);
                                if (fqcn != null && !fqcn.contains("$"))
                                    tryAddIfAnnotated(result, fqcn, annotation, classLoader);
                            });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (codeSource.isFile() && codeSource.getName().endsWith(".jar")) {
            try (JarFile jar = new JarFile(codeSource)) {
                jar.stream()
                        .filter(e -> !e.isDirectory())
                        .filter(e -> e.getName().endsWith(".class"))
                        .filter(e -> e.getName().startsWith(basePath))
                        .forEach(e -> {
                            String fqcn = e.getName().replace('/', '.').replace(".class", "");
                            if (!fqcn.contains("$")) tryAddIfAnnotated(result, fqcn, annotation, classLoader);
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private static String toClassName(Path root, Path classFile) {
        try {
            Path rel = root.relativize(classFile);
            String path = rel.toString();
            if (path.endsWith(".class")) {
                return path.replace(File.separatorChar, '.').replace(".class", "");
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static void tryAddIfAnnotated(Set<Class<?>> out,
                                          String fqcn,
                                          Class<Listeners> annotation,
                                          ClassLoader cl) {
        try {
            Class<?> cls = Class.forName(fqcn, false, cl);
            if (cls.isAnnotationPresent(annotation)) {
                out.add(cls);
            }
        } catch (NoClassDefFoundError | ClassNotFoundException ignored) {
        }
    }
}
