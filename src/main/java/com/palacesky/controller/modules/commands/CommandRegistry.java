package com.palacesky.controller.modules.commands;

import lombok.Getter;

import java.util.*;
import java.util.logging.Logger;

public class CommandRegistry {
    @Getter
    private static CommandRegistry instance;

    private final Map<String, CommandInfo> commands = new HashMap<>();
    private final Logger logger;

    public CommandRegistry(Logger logger) {
        this.logger = logger;
        instance = this;
    }

    public void registerCommand(String name, String description, String permission, String... aliases) {
        CommandInfo info = new CommandInfo(name, description, permission, aliases);
        commands.put(name.toLowerCase(), info);

        for (String alias : aliases) {
            commands.put(alias.toLowerCase(), info);
        }

        logger.info("Comando registrado: " + name + " (aliases: " + Arrays.toString(aliases) + ")");
    }

    public void unregisterCommand(String name) {
        CommandInfo info = commands.remove(name.toLowerCase());
        if (info != null) {
            for (String alias : info.aliases()) {
                commands.remove(alias.toLowerCase());
            }
            logger.info("Comando removido: " + name);
        }
    }

    public Optional<CommandInfo> getCommand(String name) {
        return Optional.ofNullable(commands.get(name.toLowerCase()));
    }

    public Collection<CommandInfo> getAllCommands() {
        return new HashSet<>(commands.values());
    }

    public void unregisterAll() {
        commands.clear();
        logger.info("Todos os comandos foram removidos");
    }

    public record CommandInfo(String name, String description, String permission, String... aliases) {
            public CommandInfo(String name, String description, String permission, String... aliases) {
                this.name = name;
                this.description = description;
                this.permission = permission;
                this.aliases = aliases != null ? aliases : new String[0];
            }
        }
}