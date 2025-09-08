package com.realmmc.controller.proxy.commands;

import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.shared.annotations.Cmd;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.net.URL;
import java.util.*;
import java.util.logging.Level;

public class CommandManager implements SimpleCommand {

    private final String name;
    private final CommandInterface cmdImpl;
    private final boolean onlyPlayer;
    private final String[] aliases;
    private final MiniMessage mm = MiniMessage.miniMessage();

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
            sender.sendMessage(mm.deserialize("<red>Somente jogadores podem usar este comando."));
            logger.warning(String.format("[Command] Tentativa por não-jogador em %s", name));
            return;
        }

        try {
            cmdImpl.execute(sender, name, args);
            logger.fine(String.format("[Command] '%s' executado com sucesso", name));
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("[Command] Erro ao executar '%s'", name), e);
            sender.sendMessage(mm.deserialize("<red>Ocorreu um erro ao executar este comando."));
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
            URL jarUrl = pluginClass.getProtectionDomain().getCodeSource().getLocation();

            var config = new ConfigurationBuilder()
                    .setUrls(jarUrl)
                    .setScanners(Scanners.SubTypes, Scanners.TypesAnnotated)
                    .addClassLoaders(pluginClass.getClassLoader());

            Reflections reflections = new Reflections(config);
            Set<Class<? extends CommandInterface>> types = reflections.getSubTypesOf(CommandInterface.class);

            for (Class<? extends CommandInterface> clazz : types) {
                Cmd ann = clazz.getAnnotation(Cmd.class);
                if (ann == null) continue;

                try {
                    CommandInterface impl = clazz.getDeclaredConstructor().newInstance();
                    new CommandManager(pluginId, ann.cmd(), impl, ann.onlyPlayer(), ann.aliases());
                    Proxy.getInstance().getLogger().info(String.format("[Command] Registrado: %s (%s) do plugin %s", ann.cmd(), clazz.getSimpleName(), pluginId));
                } catch (Exception e) {
                    Proxy.getInstance().getLogger().log(Level.SEVERE, String.format("[Command] Falha ao registrar %s (Plugin: %s)", clazz.getName(), pluginId), e);
                }
            }

        } catch (Exception e) {
            Proxy.getInstance().getLogger().log(Level.SEVERE, "[Command] Falha no scan de comandos plugáveis", e);
        }
    }
}