package com.realmmc.controller.spigot.commands;

import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Level;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final String name;
    private final CommandInterface impl;
    private final boolean onlyPlayer;
    private final String[] aliases;
    private final Plugin plugin;

    private static final Map<String, List<PluginCommand>> registered = new HashMap<>();

    private CommandManager(Plugin plugin, String name, CommandInterface impl, boolean onlyPlayer, String... aliases) {
        this.plugin = plugin;
        this.name = name;
        this.impl = impl;
        this.onlyPlayer = onlyPlayer;
        this.aliases = aliases == null ? new String[0] : aliases;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (onlyPlayer && !(sender instanceof org.bukkit.entity.Player)) {
                Messages.send(sender, MessageKey.ONLY_PLAYERS);
                return true;
            }
            impl.execute(sender, label, args);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Command] Erro ao executar '" + name + "'", e);
            Messages.send(sender, MessageKey.COMMAND_ERROR);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            List<String> res = impl.tabComplete(sender, args);
            return res != null ? res : Collections.emptyList();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Command] Erro ao sugerir tab para '" + name + "'", e);
            return Collections.emptyList();
        }
    }

    private PluginCommand buildAndRegister() {
        try {
            Constructor<PluginCommand> ctor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            ctor.setAccessible(true);
            PluginCommand pc = ctor.newInstance(name, plugin);
            if (aliases.length > 0) pc.setAliases(Arrays.asList(aliases));
            pc.setExecutor(this);
            pc.setTabCompleter(this);

            CommandMap commandMap = getCommandMap();
            String fallbackPrefix = plugin.getDescription().getName().toLowerCase(Locale.ROOT);
            commandMap.register(fallbackPrefix, pc);
            return pc;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Command] Falha ao registrar '" + name + "'", e);
            return null;
        }
    }

    private static CommandMap getCommandMap() throws Exception {
        PluginManager pm = Bukkit.getPluginManager();
        Field f = pm.getClass().getDeclaredField("commandMap");
        f.setAccessible(true);
        return (CommandMap) f.get(pm);
    }

    public static void registerAll(Plugin plugin) {
        try {
            String basePackage = "com.realmmc.controller.spigot.commands.cmds";
            Class<?> pluginClass = plugin.getClass();
            ClassLoader cl = pluginClass.getClassLoader();
            File codeSource = new File(pluginClass.getProtectionDomain().getCodeSource().getLocation().toURI());

            Set<Class<?>> discovered = findClasses(codeSource, basePackage, cl);

            for (Class<?> clazz : discovered) {
                if (!CommandInterface.class.isAssignableFrom(clazz)) continue;
                @SuppressWarnings("unchecked")
                Class<? extends CommandInterface> cmdClass = (Class<? extends CommandInterface>) clazz;

                Cmd ann = cmdClass.getAnnotation(Cmd.class);
                if (ann == null) continue;

                try {
                    CommandInterface impl = cmdClass.getDeclaredConstructor().newInstance();
                    CommandManager cm = new CommandManager(plugin, ann.cmd(), impl, ann.onlyPlayer(), ann.aliases());
                    PluginCommand pc = cm.buildAndRegister();
                    if (pc != null) {
                        registered.computeIfAbsent(plugin.getName(), k -> new ArrayList<>()).add(pc);
                        plugin.getLogger().info("[Command] Registrado: " + ann.cmd() + " (" + cmdClass.getSimpleName() + ")");
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[Command] Falha ao registrar " + cmdClass.getName(), e);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Command] Falha no scan de comandos", e);
        }
    }

    public static void unregisterAll(Plugin plugin) {
        plugin.getLogger().info("[Command] Unregistration skipped (no longer necessary).");
    }

    private static Set<Class<?>> findClasses(File codeSource, String basePackage, ClassLoader cl) throws IOException {
        Set<Class<?>> result = new HashSet<>();
        String basePath = basePackage.replace('.', '/');
        if (codeSource.isDirectory()) {
            Path start = Paths.get(codeSource.getAbsolutePath(), basePath);
            if (Files.exists(start)) {
                Files.walk(start)
                        .filter(p -> p.toString().endsWith(".class"))
                        .forEach(p -> {
                            String fqcn = toClassName(codeSource.toPath(), p);
                            if (fqcn != null && !fqcn.contains("$")) tryLoad(result, fqcn, cl);
                        });
            }
        } else if (codeSource.isFile() && codeSource.getName().endsWith(".jar")) {
            try (JarFile jar = new JarFile(codeSource)) {
                jar.stream()
                        .filter(e -> !e.isDirectory())
                        .filter(e -> e.getName().endsWith(".class"))
                        .filter(e -> e.getName().startsWith(basePath))
                        .forEach(e -> {
                            String fqcn = e.getName().replace('/', '.').replace(".class", "");
                            if (!fqcn.contains("$")) tryLoad(result, fqcn, cl);
                        });
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

    private static void tryLoad(Set<Class<?>> out, String fqcn, ClassLoader cl) {
        try {
            Class<?> cls = Class.forName(fqcn, false, cl);
            out.add(cls);
        } catch (Throwable ignored) {
        }
    }
}