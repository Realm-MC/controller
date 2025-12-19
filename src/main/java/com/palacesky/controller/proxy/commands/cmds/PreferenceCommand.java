package com.palacesky.controller.proxy.commands.cmds;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.proxy.Proxy;
import com.palacesky.controller.proxy.commands.CommandInterface;
import com.palacesky.controller.shared.annotations.Cmd;
import com.palacesky.controller.shared.auth.AuthenticationGuard;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.preferences.Language;
import com.palacesky.controller.shared.preferences.Preferences;
import com.palacesky.controller.shared.preferences.PreferencesService;
import com.palacesky.controller.shared.profile.Profile;
import com.palacesky.controller.shared.profile.ProfileResolver;
import com.palacesky.controller.shared.profile.ProfileService;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.palacesky.controller.shared.utils.NicknameFormatter;
import com.palacesky.controller.shared.utils.TaskScheduler;
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
    private final ProfileService profileService;
    private final Optional<SoundPlayer> soundPlayerOpt;

    private final String permManager = "controller.manager";
    private final String permAdmin = "controller.administrator";
    private final String permHelper = "controller.helper";
    private final String permVip = "controller.vip";

    private final MessageKey KEY_ENABLED = MessageKey.COMMON_ENABLED;
    private final MessageKey KEY_DISABLED = MessageKey.COMMON_DISABLED;
    private final MessageKey KEY_LANG_PT = MessageKey.LANG_PT;
    private final MessageKey KEY_LANG_EN = MessageKey.LANG_EN;

    public PreferenceCommand() {
        this.logger = Proxy.getInstance().getLogger();
        this.preferencesService = ServiceRegistry.getInstance().requireService(PreferencesService.class);
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
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
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GENERIC));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (args.length < 2) {
            Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/preferencia info <usuário>"));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        String targetName = args[1];

        resolveProfileAsync(targetName).thenAccept(profileOpt -> {
            if (profileOpt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetName));
                playSound(sender, SoundKeys.ERROR);
                return;
            }
            Profile profile = profileOpt.get();
            Preferences prefs = preferencesService.ensurePreferences(profile);

            String name = NicknameFormatter.getNickname(profile, true);

            Locale loc = Messages.determineLocale(sender);

            Messages.send(sender, Message.of(MessageKey.PREF_INFO_HEADER).with("player", name));

            String langVal = translateLanguage(prefs.getServerLanguage(), loc);
            String scVal = Messages.translate(prefs.isStaffChatEnabled() ? KEY_ENABLED : KEY_DISABLED, loc);
            String alVal = Messages.translate(prefs.isAutoLogin() ? KEY_ENABLED : KEY_DISABLED, loc);
            String sessVal = Messages.translate(prefs.isSessionActive() ? KEY_ENABLED : KEY_DISABLED, loc);

            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE)
                    .with("key", Messages.translate(MessageKey.PREF_LABEL_LANGUAGE, loc))
                    .with("value", langVal));

            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE)
                    .with("key", Messages.translate(MessageKey.PREF_LABEL_STAFFCHAT, loc))
                    .with("value", scVal));

            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE)
                    .with("key", Messages.translate(MessageKey.PREF_LABEL_AUTOLOGIN, loc))
                    .with("value", alVal));

            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE)
                    .with("key", Messages.translate(MessageKey.PREF_LABEL_SESSION, loc))
                    .with("value", sessVal));

            sender.sendMessage(Component.empty());
            playSound(sender, SoundKeys.NOTIFICATION);
        }).exceptionally(ex -> {
            logger.log(Level.SEVERE, "Error processing preference info command", ex);
            Messages.send(sender, MessageKey.COMMAND_ERROR);
            return null;
        });
    }

    private void handleSelfChange(CommandSource sender, String prefKey) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            return;
        }

        if (!AuthenticationGuard.isAuthenticated(player.getUniqueId())) {
            Messages.send(sender, MessageKey.AUTH_STILL_CONNECTING);
            return;
        }

        UUID uuid = player.getUniqueId();
        String key = prefKey.toLowerCase();

        TaskScheduler.runAsync(() -> {
            try {
                Optional<Profile> profileOpt = profileService.getByUuid(uuid);
                if (profileOpt.isEmpty()) {
                    Messages.send(sender, MessageKey.KICK_GENERIC_PROFILE_ERROR);
                    return;
                }
                Profile profile = profileOpt.get();
                preferencesService.ensurePreferences(profile);

                boolean changed = false;
                MessageKey valKey = null;

                if (key.equals("linguagem") || key.equals("language")) {
                    Language newLang = preferencesService.toggleLanguage(uuid);
                    valKey = (newLang == Language.PORTUGUESE) ? KEY_LANG_PT : KEY_LANG_EN;
                    changed = true;

                } else if (key.equals("staffchat") || key.equals("chatstaff")) {
                    if (!player.hasPermission(permHelper)) {
                        Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", "Ajudante"));
                        playSound(sender, SoundKeys.ERROR);
                        return;
                    }
                    boolean newState = preferencesService.toggleStaffChat(uuid);
                    valKey = newState ? KEY_ENABLED : KEY_DISABLED;
                    changed = true;

                } else if (key.equals("autologin")) {
                    if (!profile.isPremiumAccount()) {
                        Messages.send(player, MessageKey.PREF_ERROR_AUTOLOGIN_PREMIUM_ONLY);
                        playSound(sender, SoundKeys.ERROR);
                        return;
                    }
                    boolean newState = preferencesService.toggleAutoLogin(uuid);
                    valKey = newState ? KEY_ENABLED : KEY_DISABLED;
                    changed = true;

                } else if (key.equals("sessao") || key.equals("session")) {
                    if (!player.hasPermission(permVip)) {
                        Messages.send(player, MessageKey.PREF_ERROR_SESSION_VIP_ONLY);
                        playSound(sender, SoundKeys.ERROR);
                        return;
                    }
                    boolean newState = preferencesService.toggleSession(uuid);
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
                logger.log(Level.SEVERE, "Error altering preferences self for " + player.getUsername(), e);
                Messages.send(sender, MessageKey.COMMAND_ERROR);
            }
        });
    }

    private void handleAdminChange(CommandSource sender, String input) {
        if (!sender.hasPermission(permManager)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GENERIC));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        int lastColon = input.lastIndexOf(':');
        if (lastColon == -1 || lastColon == 0 || lastColon == input.length() - 1) {
            Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/preferencia <usuário>:<preferência>"));
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        String targetName = input.substring(0, lastColon);
        String prefKey = input.substring(lastColon + 1).toLowerCase();

        resolveProfileAsync(targetName).thenAccept(profileOpt -> {
            if (profileOpt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetName));
                playSound(sender, SoundKeys.ERROR);
                return;
            }

            Profile p = profileOpt.get();
            UUID uuid = p.getUuid();
            preferencesService.ensurePreferences(p);

            boolean changed = false;
            MessageKey valKey = null;

            try {
                if (prefKey.equals("linguagem") || prefKey.equals("language")) {
                    Language newLang = preferencesService.toggleLanguage(uuid);
                    valKey = (newLang == Language.PORTUGUESE) ? KEY_LANG_PT : KEY_LANG_EN;
                    changed = true;
                } else if (prefKey.equals("staffchat") || prefKey.equals("chatstaff")) {
                    boolean v = preferencesService.toggleStaffChat(uuid);
                    valKey = v ? KEY_ENABLED : KEY_DISABLED;
                    changed = true;
                } else if (prefKey.equals("autologin")) {
                    boolean v = preferencesService.toggleAutoLogin(uuid);
                    valKey = v ? KEY_ENABLED : KEY_DISABLED;
                    changed = true;
                } else if (prefKey.equals("sessao") || prefKey.equals("session")) {
                    boolean v = preferencesService.toggleSession(uuid);
                    valKey = v ? KEY_ENABLED : KEY_DISABLED;
                    changed = true;
                } else {
                    Messages.send(sender, Message.of(MessageKey.PREF_UNKNOWN).with("pref", prefKey));
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }

                if (changed && valKey != null) {
                    String val = Messages.translate(valKey, Messages.determineLocale(sender));
                    String formattedName = NicknameFormatter.getNickname(p, true);

                    Messages.send(sender, Message.of(MessageKey.PREF_UPDATED_OTHER)
                            .with("player", formattedName)
                            .with("pref", prefKey)
                            .with("value", val));
                    playSound(sender, SoundKeys.SUCCESS);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in admin preference change for " + targetName, e);
                Messages.send(sender, MessageKey.COMMAND_ERROR);
            }
        }).exceptionally(ex -> {
            logger.log(Level.SEVERE, "Error resolving profile for admin preference change", ex);
            Messages.send(sender, MessageKey.COMMAND_ERROR);
            return null;
        });
    }

    private String translateLanguage(Language lang, Locale locale) {
        return Messages.translate(lang == Language.PORTUGUESE ? KEY_LANG_PT : KEY_LANG_EN, locale);
    }

    private CompletableFuture<Optional<Profile>> resolveProfileAsync(String input) {
        Executor exec = TaskScheduler.getAsyncExecutor();
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
        if (args.length == 1) {
            String current = args[0].toLowerCase();
            suggestions.add("linguagem");

            if (sender instanceof Player) {
                suggestions.add("autologin");
                if (sender.hasPermission(permVip)) suggestions.add("sessao");
                if (sender.hasPermission(permHelper)) suggestions.add("staffchat");
            }

            if (sender.hasPermission(permAdmin)) {
                suggestions.add("info");
            }

            if (sender.hasPermission(permManager)) {
                if (current.contains(":")) {
                    String[] parts = current.split(":");
                    if (parts.length > 0) {
                        String namePart = parts[0];
                        String prefPart = (parts.length > 1) ? parts[1] : "";

                        if ("linguagem".startsWith(prefPart)) suggestions.add(namePart + ":linguagem");
                        if ("autologin".startsWith(prefPart)) suggestions.add(namePart + ":autologin");
                        if ("sessao".startsWith(prefPart)) suggestions.add(namePart + ":sessao");
                        if ("staffchat".startsWith(prefPart)) suggestions.add(namePart + ":staffchat");
                    }
                } else {
                    Proxy.getInstance().getServer().getAllPlayers().stream()
                            .map(Player::getUsername)
                            .filter(name -> name.toLowerCase().startsWith(current))
                            .forEach(name -> suggestions.add(name + ":"));
                }
            }
            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(current))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("info") && sender.hasPermission(permAdmin)) {
            String current = args[1].toLowerCase();
            return Proxy.getInstance().getServer().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(current))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}