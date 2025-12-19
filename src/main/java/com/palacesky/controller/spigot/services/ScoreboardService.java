package com.palacesky.controller.spigot.services;

import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.logger.LogService;
import com.palacesky.controller.modules.role.RoleService;
import com.palacesky.controller.shared.messaging.Message;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;
import com.palacesky.controller.shared.profile.Profile;
import com.palacesky.controller.shared.profile.ProfileService;
import com.palacesky.controller.shared.role.Role;
import com.palacesky.controller.shared.utils.TaskScheduler;
import com.palacesky.controller.spigot.api.RealmScoreboard;
import com.palacesky.controller.spigot.cash.SpigotCashCache;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ScoreboardService implements Listener {

    private final Map<UUID, RealmScoreboard> boards = new ConcurrentHashMap<>();
    private final ProfileService profileService;
    private final RoleService roleService;

    private final Optional<LogService> logService;
    private final Optional<SpigotCashCache> cashCache;
    private final Optional<SpigotGlobalCache> globalCache;

    private final SimpleDateFormat dateFormat;
    private final DecimalFormat cashFormatter;

    public ScoreboardService() {
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);

        this.logService = ServiceRegistry.getInstance().getService(LogService.class);
        this.cashCache = ServiceRegistry.getInstance().getService(SpigotCashCache.class);
        this.globalCache = ServiceRegistry.getInstance().getService(SpigotGlobalCache.class);

        this.dateFormat = new SimpleDateFormat("dd/MM/yy");
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("pt", "BR"));
        symbols.setGroupingSeparator('.');
        this.cashFormatter = new DecimalFormat("#,###", symbols);

        TaskScheduler.runSyncTimer(this::updateAll, 1L, 1L, TimeUnit.SECONDS);
    }

    public void createScoreboard(Player player) {
        if (boards.containsKey(player.getUniqueId())) return;
        RealmScoreboard sb = new RealmScoreboard(player);
        updateBoard(player, sb);
        boards.put(player.getUniqueId(), sb);
    }

    public void removeScoreboard(Player player) {
        RealmScoreboard sb = boards.remove(player.getUniqueId());
        if (sb != null) {
            sb.delete();
        }
    }

    public void shutdown() {
        boards.values().forEach(RealmScoreboard::delete);
        boards.clear();
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                RealmScoreboard sb = boards.get(player.getUniqueId());
                if (sb == null) {
                    createScoreboard(player);
                } else {
                    updateBoard(player, sb);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateBoard(Player player, RealmScoreboard sb) {
        Locale locale = Messages.determineLocale(player);

        try {
            String title = Messages.translate(MessageKey.SCOREBOARD_TITLE, locale);
            sb.updateTitle(title);
        } catch (Exception e) {
            sb.updateTitle("<gold><bold>REALMC");
        }

        String date = dateFormat.format(new Date());
        String logCode = logService.map(LogService::getSessionCode).orElse("----");

        int online = globalCache.map(SpigotGlobalCache::getGlobalOnlineCount)
                .orElse(Bukkit.getOnlinePlayers().size());

        String groupDisplay = "<gray>Membro";
        try {
            var session = roleService.getSessionDataFromCache(player.getUniqueId());
            if (session.isPresent()) {
                groupDisplay = session.get().getPrimaryRole().getDisplayName();
            } else {
                Optional<Profile> profile = profileService.getByUuid(player.getUniqueId());
                if (profile.isPresent()) {
                    String roleName = profile.get().getPrimaryRoleName();
                    Optional<Role> role = roleService.getRole(roleName);
                    if (role.isPresent()) groupDisplay = role.get().getDisplayName();
                }
            }
        } catch (Exception ignored) {}

        int cashAmount = 0;
        if (cashCache.isPresent()) {
            cashAmount = cashCache.get().getCachedCash(player.getUniqueId());
        }
        String cashFormatted = cashFormatter.format(cashAmount);

        List<String> lines = new ArrayList<>();

        lines.add(Messages.translate(Message.of(MessageKey.SCOREBOARD_LINE_DATE_CODE)
                .with("date", date)
                .with("code", logCode), locale));

        lines.add("");

        lines.add(Messages.translate(Message.of(MessageKey.SCOREBOARD_LINE_GROUP)
                .with("group", groupDisplay), locale));

        lines.add(Messages.translate(Message.of(MessageKey.SCOREBOARD_LINE_CASH)
                .with("cash", cashFormatted), locale));

        lines.add(" ");

        lines.add(Messages.translate(Message.of(MessageKey.SCOREBOARD_LINE_ONLINE)
                .with("online", online), locale));

        lines.add("  ");

        lines.add(Messages.translate(MessageKey.SCOREBOARD_FOOTER, locale));

        sb.updateLines(lines);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        createScoreboard(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeScoreboard(event.getPlayer());
    }
}