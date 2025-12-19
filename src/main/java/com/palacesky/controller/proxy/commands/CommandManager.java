package com.palacesky.controller.proxy.commands;

import com.palacesky.controller.proxy.Proxy;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.MessagingSDK;
import com.palacesky.controller.shared.annotations.Cmd;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Level;

public class CommandManager implements SimpleCommand {

    private final String name;
    private final CommandInterface cmdImpl;
    private final boolean onlyPlayer;
    private final String[] aliases;
    private final MessagingSDK sdk = MessagingSDK.getInstance();

    private static final Map<String, List<CommandManager>> registeredCommands = new HashMap<>();

    public CommandManager(String pluginId, String name, CommandInterface cmdImpl, boolean onlyPlayer, String... aliases) {
        this.name = name;
        this.cmdImpl = cmdImpl;
        this.onlyPlayer = onlyPlayer;
        this.aliases = aliases;
        register(pluginId);
    }

    @Override
    public void execute(SimpleCommand.Invocation invocation) {
        var sender = invocation.source();
        String[] args = invocation.arguments();
        var logger = Proxy.getInstance().getLogger();

        logger.info(String.format("[Command] Executando '%s' %s", name, args.length == 0 ? "" : String.join(" ", args)));

        if (onlyPlayer && !(sender instanceof Player)) {
            sdk.sendMessage(sender, MessageKey.ONLY_PLAYERS);
            logger.warning(String.format("[Command] Tentativa por não-jogador em %s", name));
            return;
        }

        try {
            cmdImpl.execute(sender, name, args);
            logger.fine(String.format("[Command] '%s' executado com sucesso", name));
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("[Command] Erro ao executar '%s'", name), e);
            sdk.sendMessage(sender, MessageKey.COMMAND_ERROR);
        }
    }

    @Override
    public List<String> suggest(SimpleCommand.Invocation invocation) {
        var sender = invocation.source();
        String[] args = invocation.arguments();
        var logger = Proxy.getInstance().getLogger();

        try {
            return cmdImpl.tabComplete(sender, args);
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("[Command] Erro ao sugerir tab para %s", name), e);
            return List.of();
        }
    }

    private void register(String pluginId) {
        var server = Proxy.getInstance().getServer();
        var meta = server.getCommandManager()
                .metaBuilder(name)
                .aliases(aliases)
                .build();
        server.getCommandManager().register(meta, this);

        registeredCommands.computeIfAbsent(pluginId, k -> new ArrayList<>()).add(this);
        Proxy.getInstance().getLogger().info(String.format("[Command] Registrado '%s' %s (Plugin: %s)", name, aliases.length == 0 ? "" : String.join(", ", aliases), pluginId));
    }

    public static void unregisterPluginCommands(String pluginId) {
        List<CommandManager> commands = registeredCommands.remove(pluginId);
        if (commands == null) return;

        for (CommandManager cmd : commands) {
            Proxy.getInstance().getLogger().info(String.format("[Command] Comando removido: %s (Plugin: %s)", cmd.name, pluginId));
        }
    }

    public static void registerAll(Object plugin) {
        try {
            Class<?> pluginClass = plugin.getClass();
            PluginContainer container = Proxy.getInstance().getServer().getPluginManager().fromInstance(plugin).orElseThrow();
            String pluginId = container.getDescription().getId();

            String basePackage = "com.palacesky.controller.proxy.commands.cmds";
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
                    new CommandManager(pluginId, ann.cmd(), impl, ann.onlyPlayer(), ann.aliases());
                    Proxy.getInstance().getLogger().info(String.format("[Command] Registrado: %s (%s) do plugin %s", ann.cmd(), cmdClass.getSimpleName(), pluginId));
                } catch (Exception e) {
                    Proxy.getInstance().getLogger().log(Level.SEVERE, String.format("[Command] Falha ao registrar %s (Plugin: %s)", cmdClass.getName(), pluginId), e);
                }
            }

        } catch (Exception e) {
            Proxy.getInstance().getLogger().log(Level.SEVERE, "[Command] Falha no scan de comandos plugáveis", e);
        }
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
                            if (fqcn != null && !fqcn.contains("$")) {
                                tryLoad(result, fqcn, cl);
                            }
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
                            if (!fqcn.contains("$")) {
                                tryLoad(result, fqcn, cl);
                            }
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