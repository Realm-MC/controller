package com.realmmc.controller.spigot.services;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.modules.logger.LogService;
import com.realmmc.controller.modules.role.RoleService;
import com.realmmc.controller.shared.messaging.Message;
import com.realmmc.controller.shared.messaging.MessageKey;
import com.realmmc.controller.shared.messaging.Messages;
import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.storage.redis.RedisChannel;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import com.realmmc.controller.spigot.Main;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.*;
import redis.clients.jedis.Jedis;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardService implements Listener {

    private final Main plugin;
    private final RoleService roleService;
    private final ProfileService profileService;
    private final Map<UUID, Scoreboard> scoreboards = new ConcurrentHashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy");
    private final String serverLogCode;
    private int networkOnlineCount = 0;

    public ScoreboardService(Main plugin) {
        this.plugin = plugin;
        this.roleService = ServiceRegistry.getInstance().requireService(RoleService.class);
        this.profileService = ServiceRegistry.getInstance().requireService(ProfileService.class);

        this.dateFormat.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));

        Optional<LogService> logOpt = ServiceRegistry.getInstance().getService(LogService.class);
        if (logOpt.isPresent()) {
            this.serverLogCode = logOpt.get().getSessionCode();
        } else {
            this.serverLogCode = "XX00XX";
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::fetchNetworkPlayers, 0L, 60L);

        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 40L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        createScoreboard(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scoreboards.remove(event.getPlayer().getUniqueId());
    }

    private void fetchNetworkPlayers() {
        try (Jedis jedis = RedisManager.getResource()) {
            String countStr = jedis.get(RedisChannel.GLOBAL_PLAYER_COUNT.getName());
            if (countStr != null) {
                this.networkOnlineCount = Integer.parseInt(countStr);
            } else {
                this.networkOnlineCount = Bukkit.getOnlinePlayers().size();
            }
        } catch (Exception e) {
            this.networkOnlineCount = Bukkit.getOnlinePlayers().size();
        }
    }

    private void createScoreboard(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Locale locale = Messages.determineLocale(player);

        String title = Messages.translate(MessageKey.SCOREBOARD_TITLE, locale);
        Objective obj = sb.registerNewObjective("realm_board", Criteria.DUMMY, mm.deserialize(title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        obj.numberFormat(NumberFormat.blank());

        createTeam(sb, "date_code", 7);
        createTeam(sb, "spacer1", 6);
        createTeam(sb, "group", 5);
        createTeam(sb, "spacer2", 4);
        createTeam(sb, "online", 3);
        createTeam(sb, "spacer3", 2);
        createTeam(sb, "footer", 1);

        player.setScoreboard(sb);
        scoreboards.put(player.getUniqueId(), sb);
        updateBoard(player, sb);
    }

    private void createTeam(Scoreboard sb, String name, int score) {
        Team t = sb.registerNewTeam(name);
        String entry = ChatColor.values()[score].toString();
        t.addEntry(entry);
        sb.getObjective("realm_board").getScore(entry).setScore(score);
    }

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = scoreboards.get(p.getUniqueId());
            if (sb != null) updateBoard(p, sb);
        }
    }

    private void updateBoard(Player player, Scoreboard sb) {
        Locale locale = Messages.determineLocale(player);

        String dateStr = dateFormat.format(new Date());

        String groupDisplay = roleService.getSessionDataFromCache(player.getUniqueId())
                .map(data -> data.getPrimaryRole().getDisplayName())
                .orElse("<gray>...");

        String lineDateCode = Messages.translate(Message.of(MessageKey.SCOREBOARD_LINE_DATE_CODE)
                .with("date", dateStr)
                .with("code", serverLogCode), locale);

        String lineGroup = Messages.translate(Message.of(MessageKey.SCOREBOARD_LINE_GROUP)
                .with("group", groupDisplay), locale);

        String lineOnline = Messages.translate(Message.of(MessageKey.SCOREBOARD_LINE_ONLINE)
                .with("online", networkOnlineCount), locale);

        String lineFooter = Messages.translate(MessageKey.SCOREBOARD_FOOTER, locale);

        updateLine(sb, "date_code", lineDateCode);
        updateLine(sb, "spacer1", " ");
        updateLine(sb, "group", lineGroup);
        updateLine(sb, "spacer2", "  ");
        updateLine(sb, "online", lineOnline);
        updateLine(sb, "spacer3", "   ");
        updateLine(sb, "footer", lineFooter);

        String title = Messages.translate(MessageKey.SCOREBOARD_TITLE, locale);
        sb.getObjective("realm_board").displayName(mm.deserialize(title));
    }

    private void updateLine(Scoreboard sb, String teamName, String text) {
        Team t = sb.getTeam(teamName);
        if (t != null) {
            String legacyText = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(mm.deserialize(text));

            if (!t.getPrefix().equals(legacyText)) {
                t.setPrefix(legacyText);
            }
        }
    }
}