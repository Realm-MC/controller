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

@Cmd(cmd = "preference", aliases = {"preferences", "preferencia", "toggle"}, onlyPlayer = false)
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

            // Tradução dos valores baseada no idioma de quem executa o comando (sender)
            Locale senderLocale = Messages.determineLocale(sender);

            String langVal = translateLanguage(prefs.getServerLanguage(), senderLocale);
            String staffChatVal = Messages.translate(
                    prefs.isStaffChatEnabled() ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE,
                    senderLocale
            );

            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Linguagem").with("value", langVal));
            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "StaffChat").with("value", staffChatVal));

            // Adiciona linha em branco no final
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
            String valTranslated = "";

            // Locale do Sender (Admin) para ver a mensagem de confirmação
            Locale senderLocale = Messages.determineLocale(sender);

            if (prefKey.equals("linguagem") || prefKey.equals("language")) {
                Language current = prefs.getServerLanguage();
                Language next = (current == Language.PORTUGUESE) ? Language.ENGLISH : Language.PORTUGUESE;
                prefs.setServerLanguage(next);
                valTranslated = translateLanguage(next, senderLocale);
                changed = true;
            } else if (prefKey.equals("staffchat") || prefKey.equals("chatstaff")) {
                boolean current = prefs.isStaffChatEnabled();
                prefs.setStaffChatEnabled(!current);
                valTranslated = Messages.translate(
                        !current ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE,
                        senderLocale
                );
                changed = true;
            } else {
                Messages.send(sender, Message.of(MessageKey.PREF_UNKNOWN).with("pref", prefKey));
                playSound(sender, SoundKeys.ERROR);
                return;
            }

            if (changed) {
                preferencesService.save(prefs);
                // Atualiza cache
                preferencesService.updateCachedPreferences(profile.getUuid(), prefs.getServerLanguage(), prefs.isStaffChatEnabled());

                String formattedName = NicknameFormatter.format(profile.getName(), sessionData.getPrimaryRole());

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
                Optional<Preferences> prefsOpt = preferencesService.getPreferences(uuid);
                if (prefsOpt.isEmpty()) {
                    preferencesService.loadAndCachePreferences(uuid);
                    prefsOpt = preferencesService.getPreferences(uuid);
                }

                if (prefsOpt.isEmpty()) {
                    Messages.send(sender, MessageKey.KICK_GENERIC_PROFILE_ERROR);
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }

                Preferences prefs = prefsOpt.get();
                boolean changed = false;

                // Variável para guardar o valor traduzido
                String valTranslated = "";

                if (key.equals("linguagem") || key.equals("language")) {
                    Language current = prefs.getServerLanguage();
                    Language next = (current == Language.PORTUGUESE) ? Language.ENGLISH : Language.PORTUGUESE;

                    // 1. Atualiza objeto local
                    prefs.setServerLanguage(next);

                    // 2. Salva e Atualiza Cache IMEDIATAMENTE
                    // Isso é crucial para que o 'Messages.determineLocale' e 'translate' peguem a mudança
                    preferencesService.save(prefs);
                    preferencesService.updateCachedPreferences(uuid, next, prefs.isStaffChatEnabled());

                    // 3. Gera o valor traduzido JÁ COM O NOVO LOCALE
                    valTranslated = translateLanguage(next, next.getLocale());
                    changed = true;

                } else if (key.equals("staffchat") || key.equals("chatstaff")) {
                    if (!player.hasPermission(permHelper)) {
                        Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", groupHelper));
                        playSound(sender, SoundKeys.ERROR);
                        return;
                    }
                    boolean current = prefs.isStaffChatEnabled();
                    prefs.setStaffChatEnabled(!current);

                    preferencesService.save(prefs);
                    preferencesService.updateCachedPreferences(uuid, prefs.getServerLanguage(), !current);

                    valTranslated = Messages.translate(
                            !current ? MessageKey.COMMON_INFO_BOOLEAN_TRUE : MessageKey.COMMON_INFO_BOOLEAN_FALSE,
                            prefs.getServerLanguage().getLocale()
                    );
                    changed = true;
                } else {
                    Messages.send(sender, Message.of(MessageKey.PREF_UNKNOWN).with("pref", key));
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }

                if (changed) {
                    // Nota: save() já foi chamado dentro dos ifs para garantir a ordem com a tradução
                    Message msg = Message.of(MessageKey.PREF_UPDATED_SELF)
                            .with("pref", key)
                            .with("value", valTranslated);

                    Messages.send(sender, msg);
                    playSound(sender, SoundKeys.SUCCESS);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error altering preferences for: " + player.getUsername(), e);
                Messages.send(sender, MessageKey.COMMAND_ERROR);
            }
        });
    }

    // Método auxiliar para traduzir nomes de linguagem manualmente ou via chave se tiver
    private String translateLanguage(Language lang, Locale locale) {
        // Idealmente você teria MessageKey.LANG_PORTUGUESE e MessageKey.LANG_ENGLISH
        // Se não tiver, retornamos o nome. Se tiver, usamos Messages.translate.
        // Exemplo assumindo que você vai criar as chaves:
        // return Messages.translate(lang == Language.PORTUGUESE ? MessageKey.LANG_PORTUGUESE : MessageKey.LANG_ENGLISH, locale);

        // Fallback hardcoded temporário se as chaves não existirem (Ajuste conforme seu MessageKey)
        if (lang == Language.PORTUGUESE) return "PORTUGUESE";
        if (lang == Language.ENGLISH) return "ENGLISH";
        return lang.name();
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

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            suggestions.add("linguagem");
            if (sender.hasPermission(permHelper)) suggestions.add("staffchat");
            if (sender.hasPermission(permAdmin)) suggestions.add("info");

            // Sugere jogadores para admin mode se tiver permissão
            if (sender.hasPermission(permManager)) {
                Proxy.getInstance().getServer().getAllPlayers().stream()
                        .map(p -> p.getUsername() + ":") // Sugere o início do formato
                        .filter(s -> s.toLowerCase().startsWith(current))
                        .forEach(suggestions::add);
            }

            // Filtra as opções normais
            List<String> filtered = suggestions.stream()
                    .filter(s -> s.startsWith(current))
                    .collect(Collectors.toList());

            return filtered;
        }

        // Sugestão para a segunda parte do formato user:pref
        if (args.length == 1 && args[0].contains(":") && sender.hasPermission(permManager)) {
            String current = args[0].toLowerCase();
            String[] parts = current.split(":");
            if (parts.length > 0) {
                String prefix = parts[0] + ":";
                String subCurrent = parts.length > 1 ? parts[1] : "";

                List<String> subs = new ArrayList<>();
                subs.add("linguagem");
                subs.add("staffchat");

                return subs.stream()
                        .map(s -> prefix + s)
                        .filter(s -> s.startsWith(current))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("info") && sender.hasPermission(permAdmin)) {
            String current = args[1].toLowerCase();
            return Proxy.getInstance().getServer().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(s -> s.toLowerCase().startsWith(current))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}