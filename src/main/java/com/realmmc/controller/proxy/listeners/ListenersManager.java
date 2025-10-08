package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.shared.annotations.Listeners;
import com.velocitypowered.api.proxy.ProxyServer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

public final class ListenersManager {

    private ListenersManager() {
    }

    public static void registerAll(ProxyServer server, Object plugin) {
        final String pkg = "com.realmmc.controller.proxy.listeners";
        ClassLoader cl = plugin.getClass().getClassLoader();

        Set<Class<?>> discovered = findAnnotatedClasses(plugin, pkg, Listeners.class, cl);

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

                server.getEventManager().register(plugin, instance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static Set<Class<?>> findAnnotatedClasses(Object plugin,
                                                      String basePackage,
                                                      Class<Listeners> annotation,
                                                      ClassLoader classLoader) {
        Set<Class<?>> result = new HashSet<>();
        String basePath = basePackage.replace('.', '/');
        File codeSource;
        try {
            codeSource = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return result;
        }

        if (codeSource.isDirectory()) {
            Path start = Paths.get(codeSource.getAbsolutePath(), basePath);
            if (Files.exists(start)) {
                try {
                    Files.walk(start)
                            .filter(p -> p.toString().endsWith(".class"))
                            .forEach(p -> {
                                String fqcn = toClassName(codeSource.toPath(), p);
                                if (fqcn != null && !fqcn.contains("$")) {
                                    tryAddIfAnnotated(result, fqcn, annotation, classLoader);
                                }
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
                            if (!fqcn.contains("$")) {
                                tryAddIfAnnotated(result, fqcn, annotation, classLoader);
                            }
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
                return path
                        .replace(File.separatorChar, '.')
                        .replace(".class", "");
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
