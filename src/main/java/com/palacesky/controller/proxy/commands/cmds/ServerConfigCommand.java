package com.palacesky.controller.proxy.commands.cmds;

import com.mongodb.client.model.Filters;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.modules.server.ServerRegistryService;
import com.palacesky.controller.modules.server.data.ServerInfo;
import com.palacesky.controller.modules.server.data.ServerInfoRepository;
import com.palacesky.controller.modules.server.data.ServerStatus;
import com.palacesky.controller.modules.server.data.ServerType;
import com.palacesky.controller.proxy.Proxy;
import com.palacesky.controller.proxy.commands.CommandInterface;
import com.palacesky.controller.shared.annotations.Cmd;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.role.Role;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.palacesky.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Cmd(cmd = "sconfig", aliases = {}, onlyPlayer = false)
public class ServerConfigCommand implements CommandInterface {

    private final String permission = "controller.manager";
    private final String requiredGroupName = "Gerente";
    private ServerInfoRepository repository;
    private ServerRegistryService serverRegistryService;
    private RoleService roleService;
    private ProxyServer proxyServer;
    private Optional<SoundPlayer> soundPlayerOpt;
    private Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ServerConfigCommand() {}

    public ServerInfoRepository getRepository() { if (repository == null) repository = new ServerInfoRepository(); return repository; }
    public ServerRegistryService getServerRegistryService() { if (serverRegistryService == null) serverRegistryService = ServiceRegistry.getInstance().requireService(ServerRegistryService.class); return serverRegistryService; }
    public RoleService getRoleService() { if (roleService == null) roleService = ServiceRegistry.getInstance().requireService(RoleService.class); return roleService; }
    public ProxyServer getProxyServer() { if (proxyServer == null) proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class); return proxyServer; }
    public Optional<SoundPlayer> getSoundPlayerOpt() { if (soundPlayerOpt == null) soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class); return soundPlayerOpt; }
    public Logger getLogger() { if (logger == null) logger = Proxy.getInstance().getLogger(); return logger; }

    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        if (!sender.hasPermission(permission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", requiredGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length == 0 || (args.length > 0 && args[0].equalsIgnoreCase("help"))) {
            showHelp(sender, label);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "criar": handleCreate(sender, args, label); break;
            case "deletar": handleDelete(sender, args, label); break;
            case "maxplayer": handleSetInt(sender, args, label, "maxplayer"); break;
            case "maxplayervip": handleSetInt(sender, args, label, "maxplayervip"); break;
            case "port": handleSetInt(sender, args, label, "port"); break;
            case "ip": handleSetString(sender, args, label, "ip"); break;
            case "mingroup": handleSetMinGroup(sender, args, label); break;
            case "pteroid": handleSetString(sender, args, label, "pteroid"); break;
            case "setdisplayname": handleSetString(sender, args, label, "displayname"); break;
            case "type": handleSetType(sender, args, label); break;
            case "info": handleInfo(sender, args, label); break;
            case "list": handleList(sender, label); break;
            case "reload": handleReload(sender); break;
            default: showHelp(sender, label); playSound(sender, SoundKeys.USAGE_ERROR); break;
        }
    }

    private void handleCreate(CommandSource sender, String[] args, String label) {
        if (args.length < 3) { sendUsage(sender, label, "criar <id_servidor> <nome_de_exibicao>"); return; }
        String serverId = args[1].toLowerCase();
        String displayName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        TaskScheduler.runAsync(() -> {
            try {
                if (getRepository().findByName(serverId).isPresent()) {
                    Messages.send(sender, Message.of(MessageKey.SCONFIG_ALREADY_EXISTS).with("id", serverId));
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }
                ServerInfo newServer = ServerInfo.builder().name(serverId).displayName(displayName).ip("0.0.0.0").port(25565).pterodactylId("NOT_SET").type(ServerType.PERSISTENT).status(ServerStatus.OFFLINE).maxPlayers(20).maxPlayersVip(30).minGroup("default").playerCount(0).build();
                getRepository().save(newServer);
                TaskScheduler.runSync(() -> {
                    Messages.send(sender, Message.of(MessageKey.SCONFIG_CREATED).with("id", serverId));
                    playSound(sender, SoundKeys.SUCCESS);
                });
            } catch (Exception e) { handleCommandError(sender, "criar servidor", e); }
        });
    }

    private void handleDelete(CommandSource sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, label, "deletar <id_servidor>"); return; }
        String serverId = args[1];
        TaskScheduler.runAsync(() -> {
            try {
                Optional<ServerInfo> serverOpt = getRepository().findByName(serverId);
                if (serverOpt.isEmpty()) { Messages.send(sender, Message.of(MessageKey.SCONFIG_NOT_FOUND).with("id", serverId)); playSound(sender, SoundKeys.ERROR); return; }
                getRepository().delete(Filters.eq("_id", serverId));
                TaskScheduler.runSync(() -> {
                    getServerRegistryService().unregisterServerFromVelocity(serverId);
                    Messages.send(sender, Message.of(MessageKey.SCONFIG_DELETED).with("id", serverId));
                    playSound(sender, SoundKeys.SUCCESS);
                });
            } catch (Exception e) { handleCommandError(sender, "deletar servidor", e); }
        });
    }

    private void handleSetInt(CommandSource sender, String[] args, String label, String property) {
        if (args.length < 3) { sendUsage(sender, label, args[0] + " <id_servidor> <numero>"); return; }
        String serverId = args[1];
        int value;
        try { value = Integer.parseInt(args[2]); } catch (NumberFormatException e) { Messages.send(sender, Message.of(MessageKey.SCONFIG_INVALID_NUMBER).with("value", args[2])); playSound(sender, SoundKeys.ERROR); return; }
        findAndModifyServer(sender, serverId, "set " + property, server -> {
            switch (property) {
                case "maxplayer" -> server.setMaxPlayers(value);
                case "maxplayervip" -> server.setMaxPlayersVip(value);
                case "port" -> server.setPort(value);
            }
        }, () -> {
            Messages.send(sender, Message.of(MessageKey.SCONFIG_PROP_UPDATED).with("prop", property).with("id", serverId).with("value", value));
            playSound(sender, SoundKeys.SETTING_UPDATE);
            if (property.equals("port")) getServerRegistryService().initialize();
        });
    }

    private void handleSetString(CommandSource sender, String[] args, String label, String property) {
        if (args.length < 3) { sendUsage(sender, label, args[0] + " <id_servidor> <valor>"); return; }
        String serverId = args[1];
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        findAndModifyServer(sender, serverId, "set " + property, server -> {
            switch (property) {
                case "ip" -> server.setIp(value);
                case "pteroid" -> server.setPterodactylId(value);
                case "displayname" -> server.setDisplayName(value);
            }
        }, () -> {
            Messages.send(sender, Message.of(MessageKey.SCONFIG_PROP_UPDATED).with("prop", property).with("id", serverId).with("value", value));
            playSound(sender, SoundKeys.SETTING_UPDATE);
            if (property.equals("ip") || property.equals("displayname")) getServerRegistryService().initialize();
        });
    }

    private void handleSetMinGroup(CommandSource sender, String[] args, String label) {
        if (args.length < 3) { sendUsage(sender, label, "mingroup <id_servidor> <nome_grupo>"); return; }
        String serverId = args[1];
        String groupName = args[2].toLowerCase();
        Optional<Role> roleOpt = getRoleService().getRole(groupName);
        if (roleOpt.isEmpty()) { Messages.send(sender, Message.of(MessageKey.ROLE_ERROR_GROUP_NOT_FOUND).with("group", groupName)); playSound(sender, SoundKeys.ERROR); return; }
        findAndModifyServer(sender, serverId, "set mingroup", server -> server.setMinGroup(groupName), () -> {
            Messages.send(sender, Message.of(MessageKey.SCONFIG_PROP_UPDATED).with("prop", "mingroup").with("id", serverId).with("value", groupName));
            playSound(sender, SoundKeys.SETTING_UPDATE);
        });
    }

    private void handleSetType(CommandSource sender, String[] args, String label) {
        if (args.length < 3) { sendUsage(sender, label, "type <id_servidor> <tipo>"); return; }
        String serverId = args[1];
        String typeName = args[2].toUpperCase();
        ServerType type;
        try { type = ServerType.valueOf(typeName); } catch (IllegalArgumentException e) { Messages.send(sender, Message.of(MessageKey.SCONFIG_INVALID_TYPE).with("types", Arrays.toString(ServerType.values()))); playSound(sender, SoundKeys.ERROR); return; }
        findAndModifyServer(sender, serverId, "set type", server -> server.setType(type), () -> {
            Messages.send(sender, Message.of(MessageKey.SCONFIG_PROP_UPDATED).with("prop", "type").with("id", serverId).with("value", type.name()));
            playSound(sender, SoundKeys.SETTING_UPDATE);
            getServerRegistryService().initialize();
        });
    }

    private void handleInfo(CommandSource sender, String[] args, String label) {
        if (args.length < 2) { sendUsage(sender, label, "info <id_servidor>"); return; }
        String serverId = args[1];
        Locale locale = Messages.determineLocale(sender);
        TaskScheduler.runAsync(() -> {
            try {
                Optional<ServerInfo> serverOpt = getRepository().findByName(serverId);
                if (serverOpt.isEmpty()) { Messages.send(sender, Message.of(MessageKey.SCONFIG_NOT_FOUND).with("id", serverId)); playSound(sender, SoundKeys.ERROR); return; }
                ServerInfo s = serverOpt.get();
                TaskScheduler.runSync(() -> {
                    MessageKey statusKey;
                    switch (s.getStatus()) {
                        case ONLINE: statusKey = MessageKey.SCONFIG_INFO_STATUS_ONLINE; break;
                        case STARTING: statusKey = MessageKey.SCONFIG_INFO_STATUS_STARTING; break;
                        case STOPPING: statusKey = MessageKey.SCONFIG_INFO_STATUS_STOPPING; break;
                        default: statusKey = MessageKey.SCONFIG_INFO_STATUS_OFFLINE;
                    }
                    String translatedStatus = Messages.translate(statusKey, locale);
                    Messages.send(sender, Message.of(MessageKey.SCONFIG_INFO_HEADER).with("id", s.getName()));
                    Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.SCONFIG_INFO_KEY_DISPLAYNAME, locale)).with("value", s.getDisplayName()));
                    Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.SCONFIG_INFO_KEY_TYPE, locale)).with("value", s.getType().name()));
                    Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.SCONFIG_INFO_KEY_STATUS, locale)).with("value", translatedStatus));
                    Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.SCONFIG_INFO_KEY_ADDRESS, locale)).with("value", s.getIp() + ":" + s.getPort()));
                    Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.SCONFIG_INFO_KEY_PTEROID, locale)).with("value", s.getPterodactylId()));
                    Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.SCONFIG_INFO_KEY_PLAYERS, locale)).with("value", s.getPlayerCount() + " / " + s.getMaxPlayers()));
                    Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.SCONFIG_INFO_KEY_MAXVIP, locale)).with("value", s.getMaxPlayersVip()));
                    Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", Messages.translate(MessageKey.SCONFIG_INFO_KEY_MINGROUP, locale)).with("value", s.getMinGroup()));
                    Messages.send(sender, "<white>");
                    playSound(sender, SoundKeys.NOTIFICATION);
                });
            } catch (Exception e) { handleCommandError(sender, "info servidor", e); }
        });
    }

    private void handleList(CommandSource sender, String label) {
        Locale locale = Messages.determineLocale(sender);
        TaskScheduler.runAsync(() -> {
            try {
                List<ServerInfo> servers = getRepository().collection().find().into(new ArrayList<>());
                servers.sort(Comparator.comparing(ServerInfo::getType).thenComparing(ServerInfo::getName));
                TaskScheduler.runSync(() -> {
                    Messages.send(sender, Message.of(MessageKey.SCONFIG_LIST_HEADER).with("count", servers.size()));
                    if (servers.isEmpty()) { Messages.send(sender, MessageKey.SCONFIG_LIST_EMPTY); } else {
                        for (ServerInfo s : servers) {
                            MessageKey statusKey;
                            String statusColor;
                            switch (s.getStatus()) {
                                case ONLINE: statusKey = MessageKey.SCONFIG_INFO_STATUS_ONLINE; statusColor = "<green>"; break;
                                case STARTING: statusKey = MessageKey.SCONFIG_INFO_STATUS_STARTING; statusColor = "<yellow>"; break;
                                case STOPPING: statusKey = MessageKey.SCONFIG_INFO_STATUS_STOPPING; statusColor = "<dark_red>"; break;
                                case OFFLINE: default: statusKey = MessageKey.SCONFIG_INFO_STATUS_OFFLINE; statusColor = "<red>"; break;
                            }
                            String translatedStatus = Messages.translate(statusKey, locale);
                            Map<String, Object> placeholders = new HashMap<>();
                            placeholders.put("name", s.getName());
                            placeholders.put("displayName", s.getDisplayName());
                            placeholders.put("type", s.getType().name());
                            placeholders.put("status_color", statusColor);
                            placeholders.put("status", translatedStatus);
                            placeholders.put("ip", s.getIp());
                            placeholders.put("port", s.getPort());
                            placeholders.put("pteroId", s.getPterodactylId());
                            placeholders.put("players", s.getPlayerCount());
                            placeholders.put("max_players", s.getMaxPlayers());
                            placeholders.put("max_vip", s.getMaxPlayersVip());
                            placeholders.put("min_group", s.getMinGroup());
                            placeholders.put("status", translatedStatus.replaceAll("<[^>]*>", ""));

                            String hoverHeader = Messages.translate(Message.of(MessageKey.SCONFIG_LIST_LINE_HOVER_HEADER).with(placeholders), locale);
                            String hoverDetails = Messages.translate(Message.of(MessageKey.SCONFIG_LIST_LINE_HOVER_DETAILS).with(placeholders), locale);
                            MessageKey hoverActionKey;
                            if (s.getStatus() == ServerStatus.ONLINE) hoverActionKey = MessageKey.SCONFIG_LIST_LINE_HOVER_ACTION_ONLINE;
                            else if (s.getStatus() == ServerStatus.STARTING) hoverActionKey = MessageKey.SCONFIG_LIST_LINE_HOVER_ACTION_STARTING;
                            else hoverActionKey = MessageKey.SCONFIG_LIST_LINE_HOVER_ACTION_OFFLINE;
                            String hoverAction = Messages.translate(hoverActionKey, locale);
                            Component hoverComponent = miniMessage.deserialize(hoverHeader + "\n" + hoverDetails + "\n" + hoverAction);
                            String lineText = Messages.translate(Message.of(MessageKey.SCONFIG_LIST_LINE).with(placeholders), locale);
                            Component lineComponent = miniMessage.deserialize(lineText).hoverEvent(HoverEvent.showText(hoverComponent));
                            if (s.getStatus() == ServerStatus.ONLINE && sender instanceof Player) {
                                lineComponent = lineComponent.clickEvent(ClickEvent.runCommand("/server " + s.getName()));
                            }
                            sender.sendMessage(lineComponent);
                        }
                    }
                    Messages.send(sender, "<white>");
                    playSound(sender, SoundKeys.NOTIFICATION);
                });
            } catch (Exception e) { handleCommandError(sender, "listar servidores", e); }
        });
    }

    private void handleReload(CommandSource sender) {
        TaskScheduler.runAsync(() -> {
            try {
                getServerRegistryService().initialize();

                getServerRegistryService().reloadTemplates();

                TaskScheduler.runSync(() -> {
                    Messages.send(sender, MessageKey.SCONFIG_RELOADED);
                    playSound(sender, SoundKeys.SUCCESS);
                });
            } catch (Exception e) { handleCommandError(sender, "reload servidores", e); }
        });
    }

    private void findAndModifyServer(CommandSource sender, String serverName, String actionName, Consumer<ServerInfo> modification, Runnable onMainThreadSuccess) {
        TaskScheduler.runAsync(() -> {
            try {
                Optional<ServerInfo> serverOpt = getRepository().findByName(serverName);
                if (serverOpt.isEmpty()) { Messages.send(sender, Message.of(MessageKey.SCONFIG_NOT_FOUND).with("id", serverName)); playSound(sender, SoundKeys.ERROR); return; }
                ServerInfo server = serverOpt.get();
                modification.accept(server);
                getRepository().save(server);
                TaskScheduler.runSync(onMainThreadSuccess);
            } catch (Exception e) { handleCommandError(sender, actionName, e); }
        });
    }

    private void showHelp(CommandSource sender, String label) {
        Locale locale = Messages.determineLocale(sender);
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Configura\u00e7\u00e3o de Servidores"));
        Map<String, MessageKey> helpMessages = new LinkedHashMap<>();
        helpMessages.put("/{label} criar <id> <displayname>", MessageKey.SCONFIG_HELP_CREATE);
        helpMessages.put("/{label} deletar <id>", MessageKey.SCONFIG_HELP_DELETE);
        helpMessages.put("/{label} list", MessageKey.SCONFIG_HELP_LIST);
        helpMessages.put("/{label} info <id>", MessageKey.SCONFIG_HELP_INFO);
        helpMessages.put("/{label} reload", MessageKey.SCONFIG_HELP_RELOAD);
        helpMessages.put("/{label} setdisplayname <id> <nome>", MessageKey.SCONFIG_HELP_SETDISPLAYNAME);
        helpMessages.put("/{label} ip <id> <ip>", MessageKey.SCONFIG_HELP_IP);
        helpMessages.put("/{label} port <id> <porta>", MessageKey.SCONFIG_HELP_PORT);
        helpMessages.put("/{label} pteroid <id> <id_pterodactyl>", MessageKey.SCONFIG_HELP_PTEROID);
        helpMessages.put("/{label} maxplayer <id> <num>", MessageKey.SCONFIG_HELP_MAXPLAYER);
        helpMessages.put("/{label} maxplayervip <id> <num>", MessageKey.SCONFIG_HELP_MAXPLAYERVIP);
        helpMessages.put("/{label} mingroup <id> <grupo>", MessageKey.SCONFIG_HELP_MINGROUP);
        helpMessages.put("/{label} type <id> <tipo>", MessageKey.SCONFIG_HELP_TYPE);
        for (Map.Entry<String, MessageKey> entry : helpMessages.entrySet()) {
            Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", entry.getKey().replace("{label}", label)).with("description", Messages.translate(entry.getValue(), locale)));
        }
        Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_REQUIRED);
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void sendUsage(CommandSource sender, String label, String usage) {
        Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/" + label + " " + usage));
        playSound(sender, SoundKeys.USAGE_ERROR);
    }
    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player player) {
            getSoundPlayerOpt().ifPresent(sp -> sp.playSound(player, key));
        }
    }
    private Void handleCommandError(CommandSource sender, String action, Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) { cause = cause.getCause(); }
        getLogger().log(Level.SEVERE, "Erro durante ação '" + action + "' no ServerConfigCommand", cause);
        Messages.send(sender, MessageKey.COMMAND_ERROR);
        playSound(sender, SoundKeys.ERROR);
        return null;
    }
    @Override
    public List<String> tabComplete(CommandSource sender, String[] args) {
        if (!sender.hasPermission(permission)) return Collections.emptyList();
        final String currentArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        List<String> suggestions = new ArrayList<>();
        switch (args.length) {
            case 1: suggestions.addAll(Arrays.asList("criar", "deletar", "maxplayer", "maxplayervip", "ip", "port", "mingroup", "pteroid", "setdisplayname", "type", "info", "list", "reload", "help")); break;
            case 2:
                String subCmd = args[0].toLowerCase();
                if (Arrays.asList("deletar", "maxplayer", "maxplayervip", "ip", "port", "mingroup", "pteroid", "setdisplayname", "type", "info").contains(subCmd)) {
                    Set<String> serverNames = getProxyServer().getAllServers().stream().map(RegisteredServer::getServerInfo).map(com.velocitypowered.api.proxy.server.ServerInfo::getName).collect(Collectors.toSet());
                    try { getRepository().collection().find().forEach(serverInfo -> serverNames.add(serverInfo.getName())); } catch (Exception e) { getLogger().warning("Falha ao buscar nomes do DB: " + e.getMessage()); }
                    suggestions.addAll(serverNames);
                }
                break;
            case 3:
                String subCmd3 = args[0].toLowerCase();
                if (subCmd3.equals("type")) suggestions.addAll(Stream.of(ServerType.values()).map(Enum::name).collect(Collectors.toList()));
                else if (subCmd3.equals("mingroup")) suggestions.addAll(getRoleService().getAllCachedRoles().stream().map(Role::getName).collect(Collectors.toList()));
                else if (subCmd3.equals("maxplayer") || subCmd3.equals("maxplayervip") || subCmd3.equals("port")) suggestions.addAll(Arrays.asList("20", "50", "100", "25565", "8080"));
                else if (subCmd3.equals("ip")) suggestions.addAll(Arrays.asList("127.0.0.1", "10.0.0.1", "192.168.1.1"));
                break;
        }
        return suggestions.stream().map(String::toLowerCase).filter(s -> s.startsWith(currentArg)).map(s -> suggestions.stream().filter(orig -> orig.toLowerCase().equals(s)).findFirst().orElse(s)).distinct().sorted().collect(Collectors.toList());
    }
}