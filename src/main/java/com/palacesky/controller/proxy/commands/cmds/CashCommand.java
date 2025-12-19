package com.palacesky.controller.proxy.commands.cmds;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.proxy.Proxy;
import com.palacesky.controller.proxy.commands.CommandInterface;
import com.palacesky.controller.shared.annotations.Cmd;
import com.palacesky.controller.shared.cash.CashService;
import com.palacesky.controller.shared.logs.CashLog;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.profile.Profile;
import com.palacesky.controller.shared.profile.ProfileResolver;
import com.palacesky.controller.shared.profile.ProfileService;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.palacesky.controller.shared.utils.NicknameFormatter;
import com.palacesky.controller.shared.utils.TaskScheduler;
import com.palacesky.controller.shared.utils.TimeUtils;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Cmd(cmd = "cash", aliases = {}, onlyPlayer = false)
public class CashCommand implements CommandInterface {

    private final String adminPermission = "controller.manager";
    private final String adminGroupName = "Gerente";
    private final Logger logger;
    private final Optional<SoundPlayer> soundPlayerOpt;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final ProfileService profileService;
    private final CashService cashService;
    private final DecimalFormat numberFormatter;
    private final ProxyServer proxyServer;

    public CashCommand() {
        this.logger = Proxy.getInstance().getLogger();
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.cashService = ServiceRegistry.getInstance().requireService(CashService.class);
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("pt", "BR"));
        symbols.setGroupingSeparator('.');
        this.numberFormatter = new DecimalFormat("#,###", symbols);
    }

    @Override
    public void execute(CommandSource sender, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                handleInfo(sender, new String[]{"info", ((Player) sender).getUsername()}, label);
            } else {
                showHelp(sender, label);
            }
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add": handleModify(sender, args, label, ModifyType.ADD); break;
            case "remove": handleModify(sender, args, label, ModifyType.REMOVE); break;
            case "set": handleModify(sender, args, label, ModifyType.SET); break;
            case "clear": handleModify(sender, args, label, ModifyType.CLEAR); break;
            case "top": handleTop(sender, label); break;
            case "history": handleHistory(sender, args, label); break;
            case "info": handleInfo(sender, args, label); break;
            case "help": showHelp(sender, label); break;
            default:
                if (subCommand.startsWith("id:")) {
                    handleLogLookup(sender, subCommand.split(":")[1]);
                } else {
                    handleInfo(sender, new String[]{"info", args[0]}, label);
                }
                break;
        }
    }

    private enum ModifyType { ADD, REMOVE, SET, CLEAR }

    private void handleModify(CommandSource sender, String[] args, String label, ModifyType type) {
        if (!sender.hasPermission(adminPermission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_NO_PERMISSION_GROUP).with("group", adminGroupName));
            playSound(sender, SoundKeys.USAGE_ERROR);
            return;
        }

        int expectedArgs = (type == ModifyType.CLEAR) ? 2 : 3;
        String usage = args[0] + " <usuário>" + (type == ModifyType.CLEAR ? "" : " <quantidade>");

        if (args.length < expectedArgs) {
            sendUsage(sender, label, usage);
            return;
        }

        String targetInput = args[1];
        int amount = 0;

        if (type != ModifyType.CLEAR) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0) {
                    Messages.send(sender, Message.of(MessageKey.CASH_ERROR_INVALID_AMOUNT).with("amount", args[2]));
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }
            } catch (NumberFormatException e) {
                Messages.send(sender, Message.of(MessageKey.CASH_ERROR_INVALID_AMOUNT).with("amount", args[2]));
                playSound(sender, SoundKeys.ERROR);
                return;
            }
        }

        final int finalAmount = amount;
        final UUID sourceUuid = (sender instanceof Player p) ? p.getUniqueId() : null;
        final String sourceName = (sender instanceof Player p) ? p.getUsername() : "CONSOLE";

        TaskScheduler.runAsync(() -> {
            try {
                Optional<Profile> targetProfileOpt = ProfileResolver.resolve(targetInput);

                if (targetProfileOpt.isEmpty()) {
                    Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetInput));
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }
                Profile targetProfile = targetProfileOpt.get();

                UUID targetUuid = targetProfile.getUuid();
                int oldBalance = targetProfile.getCash();
                int newBalance = 0;

                boolean isSelf = (sourceUuid != null && sourceUuid.equals(targetUuid));
                String formattedName = NicknameFormatter.getNickname(targetProfile, true);
                MessageKey successKey = MessageKey.COMMAND_ERROR;

                switch (type) {
                    case ADD:
                        newBalance = oldBalance + finalAmount;
                        profileService.addCash(targetUuid, finalAmount, sourceUuid, sourceName);
                        successKey = isSelf ? MessageKey.CASH_SUCCESS_ADD_SELF : MessageKey.CASH_SUCCESS_ADD;
                        break;
                    case REMOVE:
                        if (!profileService.removeCash(targetUuid, finalAmount, sourceUuid, sourceName)) {
                            Messages.send(sender, Message.of(MessageKey.CASH_ERROR_NEGATIVE_RESULT)
                                    .with("amount", formatCash(finalAmount))
                                    .with("player_name", formattedName));
                            playSound(sender, SoundKeys.ERROR);
                            return;
                        }
                        newBalance = oldBalance - finalAmount;
                        successKey = isSelf ? MessageKey.CASH_SUCCESS_REMOVE_SELF : MessageKey.CASH_SUCCESS_REMOVE;
                        break;
                    case SET:
                        newBalance = finalAmount;
                        profileService.setCash(targetUuid, newBalance, sourceUuid, sourceName);
                        successKey = isSelf ? MessageKey.CASH_SUCCESS_SET_SELF : MessageKey.CASH_SUCCESS_SET;
                        break;
                    case CLEAR:
                        newBalance = 0;
                        profileService.clearCash(targetUuid, sourceUuid, sourceName);
                        successKey = isSelf ? MessageKey.CASH_SUCCESS_CLEAR_SELF : MessageKey.CASH_SUCCESS_CLEAR;
                        break;
                }

                Messages.send(sender, Message.of(successKey)
                        .with("amount", formatCash(finalAmount))
                        .with("player_name", formattedName)
                        .with("old_balance", formatCash(oldBalance))
                        .with("new_balance", formatCash(newBalance))
                );
                playSound(sender, SoundKeys.SUCCESS);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao modificar cash", e);
                Messages.send(sender, MessageKey.COMMAND_ERROR);
            }
        });
    }

    private void handleHistory(CommandSource sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, label, "history <usuário>");
            return;
        }
        String targetName = args[1];
        TaskScheduler.runAsync(() -> {
            Optional<Profile> opt = ProfileResolver.resolve(targetName);
            if (opt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetName));
                return;
            }
            Profile profile = opt.get();
            List<CashLog> logs = profileService.getCashLogRepository().findByTarget(profile.getUuid(), 10);

            Messages.send(sender, Message.of(MessageKey.HISTORY_HEADER)
                    .with("type", "Cash")
                    .with("player", profile.getName()));

            if (logs.isEmpty()) {
                Messages.send(sender, MessageKey.COMMON_INFO_LIST_EMPTY);
            } else {
                for (CashLog log : logs) {
                    Messages.send(sender, Message.of(MessageKey.HISTORY_ENTRY_CASH)
                            .with("id", log.getId())
                            .with("action", log.getAction().name())
                            .with("amount", formatCash(log.getAmount()))
                            .with("source", log.getSource())
                            .with("date", TimeUtils.formatDate(log.getTimestamp())));
                }
            }
            playSound(sender, SoundKeys.NOTIFICATION);
        });
    }

    private void handleLogLookup(CommandSource sender, String id) {
        TaskScheduler.runAsync(() -> {
            Optional<CashLog> logOpt = profileService.getCashLogRepository().findById(id);
            if (logOpt.isEmpty()) {
                Messages.send(sender, Message.of(MessageKey.HISTORY_NOT_FOUND).with("id", id));
                return;
            }
            CashLog log = logOpt.get();
            Messages.send(sender, Message.of(MessageKey.HISTORY_DETAILS_HEADER).with("id", log.getId()));
            sendDetail(sender, "Target", log.getTargetName());
            sendDetail(sender, "Action", log.getAction().name());
            sendDetail(sender, "Amount", formatCash(log.getAmount()));
            sendDetail(sender, "Old Balance", formatCash(log.getOldBalance()));
            sendDetail(sender, "New Balance", formatCash(log.getNewBalance()));
            sendDetail(sender, "Source", log.getSource());
            sendDetail(sender, "Date", TimeUtils.formatDate(log.getTimestamp()));
            playSound(sender, SoundKeys.NOTIFICATION);
        });
    }

    private void sendDetail(CommandSource sender, String key, String val) {
        Messages.send(sender, Message.of(MessageKey.HISTORY_DETAILS_LINE).with("key", key).with("value", val));
    }

    private void handleTop(CommandSource sender, String label) {
        Locale locale = Messages.determineLocale(sender);

        TaskScheduler.runAsync(() -> {
            try {
                List<Profile> top10 = cashService.getCachedTop10();
                long nextUpdate = cashService.getNextUpdateTimestamp();
                String timeRemaining = TimeUtils.formatDuration(Math.max(0, nextUpdate - System.currentTimeMillis()));

                Messages.send(sender, MessageKey.CASH_TOP_HEADER);
                Messages.send(sender, Message.of(MessageKey.CASH_TOP_UPDATE_TIMER).with("time", timeRemaining));

                int position = 1;
                for (Profile p : top10) {
                    String formattedName = NicknameFormatter.getNickname(p, true);
                    String formattedCash = formatCash(p.getCash());

                    String lineFormat = Messages.translate(Message.of(MessageKey.CASH_TOP_LINE)
                            .with("position", position)
                            .with("player_name", formattedName)
                            .with("cash", formattedCash), locale);

                    Component hoverText = miniMessage.deserialize(
                            Messages.translate(Message.of(MessageKey.CASH_TOP_CLICK_HOVER).with("player", p.getName()), locale)
                    );

                    Component lineComponent = miniMessage.deserialize(lineFormat)
                            .hoverEvent(HoverEvent.showText(hoverText))
                            .clickEvent(ClickEvent.runCommand("/cash info " + p.getName()));

                    sender.sendMessage(lineComponent);
                    position++;
                }

                Messages.send(sender, MessageKey.CASH_TOP_FOOTER);
                playSound(sender, SoundKeys.NOTIFICATION);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao ver top cash", e);
                Messages.send(sender, MessageKey.COMMAND_ERROR);
            }
        });
    }

    private void handleInfo(CommandSource sender, String[] args, String label) {
        if (args.length < 2) {
            sendUsage(sender, label, "info <usuário>");
            return;
        }
        String targetInput = args[1];

        if (targetInput.toLowerCase().startsWith("id:")) {
            handleLogLookup(sender, targetInput.split(":")[1]);
            return;
        }

        TaskScheduler.runAsync(() -> {
            try {
                Optional<Profile> targetProfileOpt = ProfileResolver.resolve(targetInput);
                if (targetProfileOpt.isEmpty()) {
                    Messages.send(sender, Message.of(MessageKey.COMMON_PLAYER_NEVER_JOINED).with("player", targetInput));
                    playSound(sender, SoundKeys.ERROR);
                    return;
                }

                Profile targetProfile = targetProfileOpt.get();
                String formattedName = NicknameFormatter.getNickname(targetProfile, true);

                Messages.send(sender, Message.of(MessageKey.CASH_INFO_HEADER).with("player_name", formattedName));
                Messages.send(sender, Message.of(MessageKey.CASH_INFO_LINE_TOTAL).with("cash", formatCash(targetProfile.getCash())));

                if (targetProfile.getPendingCash() > 0) {
                    Messages.send(sender, Message.of(MessageKey.CASH_INFO_LINE_PENDING).with("cash", formatCash(targetProfile.getPendingCash())));
                } else {
                    Messages.send(sender, Message.of(MessageKey.CASH_INFO_LINE_PENDING).with("cash", "0"));
                }

                Messages.send(sender, MessageKey.CASH_TOP_FOOTER);
                playSound(sender, SoundKeys.NOTIFICATION);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Erro ao ver info cash", e);
                Messages.send(sender, MessageKey.COMMAND_ERROR);
            }
        });
    }

    private void showHelp(CommandSource sender, String label) {
        Messages.send(sender, Message.of(MessageKey.COMMON_HELP_HEADER).with("system", "Cash"));
        Messages.send(sender, MessageKey.CASH_HELP_VIEW);
        Messages.send(sender, MessageKey.CASH_HELP_TOP);
        if (sender.hasPermission(adminPermission)) {
            Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " info <usuário|id:log>").with("description", "Vê saldo ou detalhes de log."));
            Messages.send(sender, Message.of(MessageKey.COMMON_HELP_LINE).with("usage", "/" + label + " history <usuário>").with("description", "Vê histórico de transações."));
            Messages.send(sender, MessageKey.CASH_HELP_ADD);
            Messages.send(sender, MessageKey.CASH_HELP_REMOVE);
            Messages.send(sender, MessageKey.CASH_HELP_SET);
            Messages.send(sender, MessageKey.CASH_HELP_CLEAR);
        }
        Messages.send(sender, MessageKey.COMMON_HELP_FOOTER_FULL);
        playSound(sender, SoundKeys.NOTIFICATION);
    }

    private String formatCash(int cash) { return numberFormatter.format(cash); }
    private void sendUsage(CommandSource sender, String label, String usage) {
        Messages.send(sender, Message.of(MessageKey.COMMON_USAGE).with("usage", "/" + label + " " + usage));
        playSound(sender, SoundKeys.USAGE_ERROR);
    }
    private void playSound(CommandSource sender, String key) {
        if (sender instanceof Player player) {
            soundPlayerOpt.ifPresent(sp -> sp.playSound(player, key));
        }
    }
    @Override
    public List<String> tabComplete(CommandSource sender, String[] args) {
        final List<String> completions = new ArrayList<>();
        final String currentArg = (args.length > 0) ? args[args.length - 1].toLowerCase() : "";
        if (args.length == 1) {
            completions.add("top"); completions.add("help"); completions.add("info");
            if (sender.hasPermission(adminPermission)) {
                completions.addAll(Arrays.asList("add", "remove", "set", "clear", "history"));
            }
            proxyServer.getAllPlayers().stream().map(Player::getUsername).forEach(completions::add);
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (Arrays.asList("add", "remove", "set", "clear", "info", "history").contains(subCmd)) {
                proxyServer.getAllPlayers().stream().map(Player::getUsername).forEach(completions::add);
            }
        } else if (args.length == 3) {
            String subCmd = args[0].toLowerCase();
            if (Arrays.asList("add", "remove", "set").contains(subCmd) && sender.hasPermission(adminPermission)) {
                completions.addAll(Arrays.asList("100", "500", "1000", "5000", "10000"));
            }
        }
        return completions.stream().filter(s -> s.toLowerCase().startsWith(currentArg)).distinct().sorted().collect(Collectors.toList());
    }
}