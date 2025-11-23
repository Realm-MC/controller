package com.realmmc.controller.proxy.commands.cmds;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.proxy.Proxy;
import com.realmmc.controller.proxy.commands.CommandInterface;
import com.realmmc.controller.shared.annotations.Cmd;
import com.realmmc.controller.shared.cosmetics.CosmeticsService;
import com.realmmc.controller.shared.cosmetics.medals.Medal;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileResolver;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.sounds.SoundKeys;
import com.realmmc.controller.shared.sounds.SoundPlayer;
import com.realmmc.controller.shared.utils.NicknameFormatter;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Cmd(cmd = "medalha", aliases = {"medalhas", "medal", "medals"}, onlyPlayer = false)
public class MedalCommand implements CommandInterface {

    private final ProfileService profileService;
    private final CosmeticsService cosmeticsService;
    private final RoleService roleService;
    private final Optional<SoundPlayer> soundPlayerOpt;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Logger logger;

    private final String PERM_ADMIN = "controller.administrator";
    private final String PERM_MANAGER = "controller.manager";

    public MedalCommand() {
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.cosmeticsService = ServiceRegistry.getInstance().requireService(CosmeticsService.class);
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.logger = Proxy.getInstance().getLogger();
    }

    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                handleList((Player) sender);
            } else {
                showHelp(sender, label);
            }
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "info":
                handleInfo(sender, args);
                break;
            case "equipar":
                handleEquip(sender, args);
                break;
            case "desequipar":
                handleUnequip(sender, args);
                break;
            case "add":
                handleAdd(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "help":
            default:
                showHelp(sender, label);
                break;
        }
    }

    private void handleList(Player player) {
        TaskScheduler.runAsync(() -> {
            UUID uuid = player.getUniqueId();
            Profile profile = profileService.getByUuid(uuid).orElseThrow();
            cosmeticsService.ensureCosmetics(profile);
            List<String> ownedIds = cosmeticsService.getCachedMedals(uuid);

            if (ownedIds.isEmpty()) {
                Messages.send(player, MessageKey.MEDAL_LIST_EMPTY);
                playSound(player, SoundKeys.ERROR);
                return;
            }

            Messages.send(player, MessageKey.MEDAL_LIST_HEADER);

            TextComponent.Builder listBuilder = Component.text();

            List<Medal> validMedals = ownedIds.stream()
                    .map(Medal::fromId)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            if (validMedals.isEmpty()) {
                Messages.send(player, MessageKey.MEDAL_LIST_EMPTY);
                playSound(player, SoundKeys.ERROR);
                return;
            }

            String currentlyEquipped = profile.getEquippedMedal();

            for (int i = 0; i < validMedals.size(); i++) {
                Medal medal = validMedals.get(i);
                boolean isEquipped = medal.getId().equalsIgnoreCase(currentlyEquipped);

                String displayName = medal.getDisplayName();

                Component hoverText;
                ClickEvent clickEvent;
                Component medalComponent;

                if (isEquipped) {
                    hoverText = mm.deserialize("<red>Clique para desequipar\n" + displayName);
                    clickEvent = ClickEvent.runCommand("/medalha desequipar");
                    medalComponent = mm.deserialize("<green>" + displayName.replaceAll("<[^>]*>", ""));
                } else {
                    hoverText = mm.deserialize(Messages.translate(Message.of(MessageKey.MEDAL_LIST_ITEM_HOVER).with("medal", displayName)));
                    clickEvent = ClickEvent.runCommand("/medalha equipar " + medal.getId());
                    medalComponent = mm.deserialize(Messages.translate(Message.of(MessageKey.MEDAL_LIST_ITEM).with("medal", displayName)));
                }

                medalComponent = medalComponent
                        .hoverEvent(HoverEvent.showText(hoverText))
                        .clickEvent(clickEvent);

                listBuilder.append(medalComponent);

                if (i < validMedals.size() - 1) {
                    listBuilder.append(mm.deserialize("<gray>, "));
                } else {
                    listBuilder.append(mm.deserialize("<gray>."));
                }
            }

            player.sendMessage(listBuilder.build());
            player.sendMessage(Component.empty());
            playSound(player, SoundKeys.NOTIFICATION);
        });
    }

    private void handleInfo(CommandSource sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GENERIC));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (args.length < 2) {
            Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/medalha info <id|usuário>"));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String target = args[1];

        Optional<Medal> medalOpt = Medal.fromId(target);

        if (medalOpt.isPresent()) {
            Medal m = medalOpt.get();
            Messages.send(sender, Message.of(MessageKey.MEDAL_INFO_HEADER).with("id", m.getId()));
            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Nome").with("value", m.name()));
            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Display").with("value", m.getDisplayName()));
            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Prefix").with("value", m.getPrefix()));
            Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Servidores Permitidos").with("value", m.getAllowedTypes().isEmpty() ? "Todos" : m.getAllowedTypes().toString()));
            playSound(sender, SoundKeys.NOTIFICATION);
        } else {
            resolveProfileAsync(target).thenAccept(profileOpt -> {
                if (profileOpt.isEmpty()) {
                    Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", target));
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }
                Profile p = profileOpt.get();
                cosmeticsService.ensureCosmetics(p);
                List<String> medals = cosmeticsService.getCachedMedals(p.getUuid());

                String formattedName = NicknameFormatter.getNickname(p.getUuid(), true, p.getName());
                String equippedId = p.getEquippedMedal();
                String equippedDisplay = Medal.fromId(equippedId).map(Medal::getDisplayName).orElse("<gray>Nenhuma");

                Messages.send(sender, Message.of(MessageKey.COMMON_INFO_HEADER).with("subject", "Medalhas de " + formattedName));
                Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LINE).with("key", "Equipada Atual").with("value", equippedDisplay));
                Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_HEADER).with("key", "Desbloqueadas").with("count", medals.size()));

                if (medals.isEmpty()) {
                    Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY);
                } else {
                    int idx = 1;
                    for (String mId : medals) {
                        String dName = Medal.fromId(mId).map(Medal::getDisplayName).orElse(mId);
                        Messages.send(sender, Message.of(MessageKey.COMMON_INFO_LIST_ITEM).with("index", idx++).with("value", dName + " <dark_gray>(" + mId + ")"));
                    }
                }
                playSound(sender, SoundKeys.NOTIFICATION);
            });
        }
    }

    private void handleEquip(CommandSource sender, String[] args) {
        if (args.length < 2) {
            Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/medalha equipar <id>"));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        if (args[1].contains(":") && sender.hasPermission(PERM_MANAGER)) {
            String[] parts = args[1].split(":");
            if (parts.length < 2) return;

            String targetName = parts[0];
            String medalId = parts[1];

            Optional<Medal> mOpt = Medal.fromId(medalId);
            if (mOpt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.MEDAL_NOT_FOUND).with("id", medalId));
                playSound(sender, SoundKeys.ERROR);
                return;
            }

            resolveProfileAsync(targetName).thenAccept(pOpt -> {
                if(pOpt.isEmpty()) {
                    Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetName));
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }
                Profile p = pOpt.get();
                p.setEquippedMedal(mOpt.get().getId());
                profileService.save(p);

                Messages.send(sender, Message.of(MessageKey.MEDAL_ADMIN_EQUIP)
                        .with("medal", mOpt.get().getDisplayName())
                        .with("player", p.getName()));
                playSound(sender, SoundKeys.SUCCESS);
            });
            return;
        }

        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            return;
        }

        String medalId = args[1];
        Optional<Medal> mOpt = Medal.fromId(medalId);
        if (mOpt.isEmpty()) {
            Messages.send(sender, Message.of(MessageKey.MEDAL_NOT_FOUND).with("id", medalId));
            playSound(player, SoundKeys.ERROR);
            return;
        }

        TaskScheduler.runAsync(() -> {
            if (!cosmeticsService.hasMedal(player.getUniqueId(), medalId)) {
                Messages.send(player, Message.of(MessageKey.MEDAL_NOT_OWNED).with("medal", mOpt.get().getDisplayName()));
                playSound(player, SoundKeys.ERROR);
                return;
            }

            Profile p = profileService.getByUuid(player.getUniqueId()).orElseThrow();
            p.setEquippedMedal(medalId.toLowerCase());
            profileService.save(p);

            Messages.send(player, Message.of(MessageKey.MEDAL_EQUIPPED).with("medal", mOpt.get().getDisplayName()));
            playSound(player, SoundKeys.SUCCESS);
        });
    }

    private void handleUnequip(CommandSource sender, String[] args) {
        if (args.length > 1 && sender.hasPermission(PERM_MANAGER)) {
            String targetName = args[1];
            resolveProfileAsync(targetName).thenAccept(pOpt -> {
                if(pOpt.isEmpty()) {
                    Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetName));
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }
                Profile p = pOpt.get();
                p.setEquippedMedal("none");
                profileService.save(p);

                Messages.send(sender, Message.of(MessageKey.MEDAL_ADMIN_UNEQUIP).with("player", p.getName()));
                playSound(sender, SoundKeys.SUCCESS);
            });
            return;
        }

        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ONLY_PLAYERS);
            return;
        }

        TaskScheduler.runAsync(() -> {
            Profile p = profileService.getByUuid(player.getUniqueId()).orElseThrow();
            if ("none".equals(p.getEquippedMedal())) {
                return;
            }
            p.setEquippedMedal("none");
            profileService.save(p);

            Messages.send(player, MessageKey.MEDAL_UNEQUIPPED);
            playSound(player, SoundKeys.SUCCESS);
        });
    }

    private void handleAdd(CommandSource sender, String[] args) {
        if (!sender.hasPermission(PERM_MANAGER)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GENERIC));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (args.length < 3) {
            Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/medalha add <usuário> <medalha>"));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String target = args[1];
        String medalId = args[2];
        Optional<Medal> mOpt = Medal.fromId(medalId);
        if (mOpt.isEmpty()) {
            Messages.send(sender, Message.of(MessageKey.MEDAL_NOT_FOUND).with("id", medalId));
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        resolveProfileAsync(target).thenAccept(pOpt -> {
            if (pOpt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", target));
                playSound(sender, SoundKeys.ERROR);
                return;
            }
            Profile p = pOpt.get();

            if (cosmeticsService.hasMedal(p.getUuid(), medalId)) {
                Messages.send(sender, MessageKey.MEDAL_ALREADY_OWNED);
                playSound(sender, SoundKeys.ERROR);
                return;
            }

            cosmeticsService.addMedal(p.getUuid(), p.getName(), medalId);

            Messages.send(sender, Message.of(MessageKey.MEDAL_ADMIN_ADD)
                    .with("medal", mOpt.get().getDisplayName())
                    .with("player", p.getName()));
            playSound(sender, SoundKeys.SUCCESS);
        });
    }

    private void handleRemove(CommandSource sender, String[] args) {
        if (!sender.hasPermission(PERM_MANAGER)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GENERIC));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }
        if (args.length < 3) {
            Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/medalha remove <usuário> <medalha>"));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        String target = args[1];
        String medalId = args[2];
        Optional<Medal> mOpt = Medal.fromId(medalId);
        if (mOpt.isEmpty()) {
            Messages.send(sender, Message.of(MessageKey.MEDAL_NOT_FOUND).with("id", medalId));
            playSound(sender, SoundKeys.ERROR);
            return;
        }

        resolveProfileAsync(target).thenAccept(pOpt -> {
            if (pOpt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", target));
                playSound(sender, SoundKeys.ERROR);
                return;
            }
            Profile p = pOpt.get();

            cosmeticsService.removeMedal(p.getUuid(), medalId);

            if (p.getEquippedMedal().equalsIgnoreCase(medalId)) {
                p.setEquippedMedal("none");
                profileService.save(p);
            }

            Messages.send(sender, Message.of(MessageKey.MEDAL_ADMIN_REMOVE)
                    .with("medal", mOpt.get().getDisplayName())
                    .with("player", p.getName()));
            playSound(sender, SoundKeys.SUCCESS);
        });
    }

    private void showHelp(CommandSource sender, String label) {
        Locale locale = Messages.determineLocale(sender);
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Medalhas"));

        Map<String, MessageKey> helps = new LinkedHashMap<>();
        helps.put("/" + label, MessageKey.MEDAL_HELP_LIST);
        helps.put("/" + label + " equipar <id>", MessageKey.MEDAL_HELP_EQUIP);
        helps.put("/" + label + " desequipar", MessageKey.MEDAL_HELP_UNEQUIP);

        if (sender.hasPermission(PERM_ADMIN)) {
            helps.put("/" + label + " info <id|user>", MessageKey.MEDAL_HELP_INFO);
        }
        if (sender.hasPermission(PERM_MANAGER)) {
            helps.put("/" + label + " add <user> <id>", MessageKey.MEDAL_HELP_ADD);
            helps.put("/" + label + " remove <user> <id>", MessageKey.MEDAL_HELP_REMOVE);
            helps.put("/" + label + " equipar <user>:<id>", MessageKey.MEDAL_HELP_ADMIN_EQUIP);
        }

        for (var entry : helps.entrySet()) {
            Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE)
                    .with("usage", entry.getKey())
                    .with("description", Messages.translate(entry.getValue(), locale)));
        }
        Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_FULL);
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player) {
            soundPlayerOpt.ifPresent(sp -> sp.playSound(sender, key));
        }
    }

    private CompletableFuture<Optional<Profile>> resolveProfileAsync(String input) {
        return CompletableFuture.supplyAsync(() -> ProfileResolver.resolve(input), TaskScheduler.getAsyncExecutor());
    }

    @Override
    public List<String> tabComplete(CommandSource sender, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("equipar", "desequipar", "help"));
            if (sender.hasPermission(PERM_ADMIN)) base.add("info");
            if (sender.hasPermission(PERM_MANAGER)) { base.add("add"); base.add("remove"); }
            return base.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("equipar") || (sub.equals("info") && !sender.hasPermission(PERM_MANAGER))) {
                return Arrays.stream(Medal.values()).map(Medal::getId).filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("add") || sub.equals("remove") || sub.equals("info") || sub.equals("desequipar")) {
                return Proxy.getInstance().getServer().getAllPlayers().stream().map(Player::getUsername).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("remove")) {
                return Arrays.stream(Medal.values()).map(Medal::getId).filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}