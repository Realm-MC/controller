package com.realmmc.controller.proxy.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.PlayerSessionData;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.proxy.commands.CommandInterface;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.preferences.Language;
import com.realmmc.controller.shared.preferences.Preferences;
import com.realmmc.controller.shared.preferences.PreferencesService;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileResolver;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.utils.NicknameFormatter;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Cmd(cmd = "preference", aliases = {"preferences", "preferencia", "preferencias", "toggle"}, onlyPlayer = false)
public class PreferenceCommand implements CommandInterface {

    private final Logger logger;
    private final PreferencesService preferencesService;
    private final RoleService roleService;
    private final Optional<SoundPlayer> soundPlayerOpt;

    private final String permManager = "controller.manager";
    private final String permAdmin = "controller.administrator";
    private final String permHelper = "controller.helper";

    private final String groupManager = "Gerente";
    private final String groupAdmin = "Administrador";
    private final String groupHelper = "Ajudante";

    private final MessageKey KEY_ENABLED = MessageKey.COMMON_ENABLED;
    private final MessageKey KEY_DISABLED = MessageKey.COMMON_DISABLED;
    private final MessageKey KEY_LANG_PT = MessageKey.LANG_PT;
    private final MessageKey KEY_LANG_EN = MessageKey.LANG_EN;

    public PreferenceCommand() {
        this.logger = Proxy.getInstance().getLogger();
        this.preferencesService = ServiceRegistry.getInstance().requireService(PreferencesService.class);
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
    }

    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        if (args.length == 0) {
            Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/" + label + " <preferência>"));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String arg0 = args[0];

        if (arg0.equalsIgnoreCase("info")) {
            handleInfo(sender, args);
            return;
        }

        if (arg0.contains(":")) {
            handleAdminChange(sender, arg0);
        } else {
            handleSelfChange(sender, arg0);
        }
    }

    private void handleInfo(CommandSource sender, String[] args) {
        if (!sender.hasPermission(permAdmin)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", groupAdmin));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args.length < 2) {
            Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/preferencia info <usuário>"));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String targetName = args[1];

        resolveProfileAsync(targetName).thenCompose(targetProfileOpt -> {
            if (targetProfileOpt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetName));
                playSound(sender, SoundKeys.ERROR);
                return CompletableFuture.completedFuture(null);
            }

            Profile profile = targetProfileOpt.get();
            return roleService.loadPlayerDataAsync(profile.getUuid())
                    .thenApply(sessionData -> new AbstractMap.SimpleImmutableEntry<>(profile, sessionData));
        }).thenAccept(entry -> {
            if (entry == null) return;
            Profile profile = entry.getKey();
            PlayerSessionData sessionData = entry.getValue();
            if (sessionData == null) sessionData = roleService.getDefaultSessionData(profile.getUuid());

            Preferences prefs = preferencesService.ensurePreferences(profile);
            String formattedName = NicknameFormatter.format(profile.getName(), sessionData.getPrimaryRole());

            Messages.send(sender, Message.of(MessageKey.PREF_INFO_HEADER).with("player", formattedName));

            Locale senderLocale = Messages.determineLocale(sender);
            String langVal = translateLanguage(prefs.getServerLanguage(), senderLocale);

            String staffChatVal = Messages.translate(
                    prefs.isStaffChatEnabled() ? KEY_ENABLED : KEY_DISABLED,
                    senderLocale
            );

            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Linguagem").with("value", langVal));
            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "StaffChat").with("value", staffChatVal));

            sender.sendMessage(Component.empty());
            playSound(sender, SoundKeys.NOTIFICATION);

        }).exceptionally(ex -> {
            logger.log(Level.SEVERE, "Error processing info command", ex);
            Messages.send(sender, MessageKey.COMMAND_ERROR);
            return null;
        });
    }

    private void handleAdminChange(CommandSource sender, String input) {
        if (!sender.hasPermission(permManager)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", groupManager));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        int lastColonIndex = input.lastIndexOf(':');
        if (lastColonIndex == -1 || lastColonIndex == 0 || lastColonIndex == input.length() - 1) {
            Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/preferencia <usuário>:<preferência>"));
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        String targetName = input.substring(0, lastColonIndex);
        String prefKey = input.substring(lastColonIndex + 1).toLowerCase();

        resolveProfileAsync(targetName).thenCompose(targetProfileOpt -> {
            if (targetProfileOpt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetName));
                playSound(sender, SoundKeys.ERROR);
                return CompletableFuture.completedFuture(null);
            }
            Profile profile = targetProfileOpt.get();
            return roleService.loadPlayerDataAsync(profile.getUuid())
                    .thenApply(sessionData -> new AbstractMap.SimpleImmutableEntry<>(profile, sessionData));
        }).thenAccept(entry -> {
            if (entry == null) return;
            Profile profile = entry.getKey();
            PlayerSessionData sessionData = entry.getValue();
            if (sessionData == null) sessionData = roleService.getDefaultSessionData(profile.getUuid());

            Preferences prefs = preferencesService.ensurePreferences(profile);
            boolean changed = false;

            MessageKey valKey = null;

            Locale senderLocale = Messages.determineLocale(sender);

            if (prefKey.equals("linguagem") || prefKey.equals("language")) {
                Language newLang = preferencesService.toggleLanguage(profile.getUuid());
                valKey = (newLang == Language.PORTUGUESE) ? KEY_LANG_PT : KEY_LANG_EN;
                changed = true;
            } else if (prefKey.equals("staffchat") || prefKey.equals("chatstaff")) {
                boolean newState = preferencesService.toggleStaffChat(profile.getUuid());
                valKey = newState ? KEY_ENABLED : KEY_DISABLED;
                changed = true;
            } else {
                Messages.send(sender, Message.of(MessageKey.PREF_UNKNOWN).with("pref", prefKey));
                playSound(sender, SoundKeys.ERROR);
                return;
            }

            if (changed && valKey != null) {
                String formattedName = NicknameFormatter.format(profile.getName(), sessionData.getPrimaryRole());

                String valTranslated = Messages.translate(valKey, senderLocale);

                Messages.send(sender, Message.of(MessageKey.PREF_UPDATED_OTHER)
                        .with("player", formattedName)
                        .with("pref", prefKey)
                        .with("value", valTranslated));
                playSound(sender, SoundKeys.SUCCESS);
            }
        });
    }

    private void handleSelfChange(CommandSource sender, String prefKey) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            return;
        }

        UUID uuid = player.getUniqueId();
        String key = prefKey.toLowerCase();

        TaskScheduler.runAsync(() -> {
            try {
                if (preferencesService.getOrLoadPreferences(uuid).isEmpty()) {
                    Messages.send(sender, MessageKey.KICK_GENERIC_PROFILE_ERROR);
                    return;
                }

                boolean changed = false;
                MessageKey valKey = null;

                if (key.equals("linguagem") || key.equals("language")) {
                    Language newLang = preferencesService.toggleLanguage(uuid);
                    valKey = (newLang == Language.PORTUGUESE) ? KEY_LANG_PT : KEY_LANG_EN;

                    preferencesService.updateCachedPreferences(uuid, newLang, preferencesService.getCachedStaffChatEnabled(uuid).orElse(true));
                    changed = true;

                } else if (key.equals("staffchat") || key.equals("chatstaff")) {
                    if (!player.hasPermission(permHelper)) {
                        Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", groupHelper));
                        playSound(sender, SoundKeys.ERROR);
                        return;
                    }
                    boolean newState = preferencesService.toggleStaffChat(uuid);
                    valKey = newState ? KEY_ENABLED : KEY_DISABLED;
                    changed = true;
                } else {
                    Messages.send(sender, Message.of(MessageKey.PREF_UNKNOWN).with("pref", key));
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }

                if (changed && valKey != null) {
                    Locale newLocale = Messages.determineLocale(sender);
                    String valTranslated = Messages.translate(valKey, newLocale);

                    Messages.send(sender, Message.of(MessageKey.PREF_UPDATED_SELF)
                            .with("pref", key)
                            .with("value", valTranslated));
                    playSound(sender, SoundKeys.SUCCESS);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error altering preferences for: " + player.getUsername(), e);
                Messages.send(sender, MessageKey.COMMAND_ERROR);
            }
        });
    }

    private String translateLanguage(Language lang, Locale locale) {
        return Messages.translate(lang == Language.PORTUGUESE ? KEY_LANG_PT : KEY_LANG_EN, locale);
    }

    private CompletableFuture<Optional<Profile>> resolveProfileAsync(String input) {
        Executor exec = roleService.getAsyncExecutor();
        if (exec == null) exec = ForkJoinPool.commonPool();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ProfileResolver.resolve(input);
            } catch (Exception e) {
                return Optional.empty();
            }
        }, exec);
    }

    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player player) {
            soundPlayerOpt.ifPresent(sp -> sp.playSound(player, key));
        }
    }

    @Override
    public List<String> tabComplete(CommandSource sender, String[] args) {
        List<String> suggestions = new ArrayList<>();
        String current = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            suggestions.add("linguagem");

            if (sender.hasPermission(permHelper)) {
                suggestions.add("staffchat");
            }

            if (sender.hasPermission(permAdmin)) {
                suggestions.add("info");
            }

            if (sender.hasPermission(permManager)) {
                if (!current.contains(":")) {
                    Proxy.getInstance().getServer().getAllPlayers().forEach(p -> {
                        if (p.getUsername().toLowerCase().startsWith(current)) {
                            suggestions.add(p.getUsername() + ":");
                        }
                    });
                }
                else {
                    String[] parts = current.split(":");
                    String namePart = parts[0];
                    suggestions.add(namePart + ":linguagem");
                    suggestions.add(namePart + ":staffchat");
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("info") && sender.hasPermission(permAdmin)) {
                Proxy.getInstance().getServer().getAllPlayers().forEach(p -> {
                    if (p.getUsername().toLowerCase().startsWith(current)) {
                        suggestions.add(p.getUsername());
                    }
                });
            }
        }

        return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(current))
                .sorted()
                .collect(Collectors.toList());
    }
}