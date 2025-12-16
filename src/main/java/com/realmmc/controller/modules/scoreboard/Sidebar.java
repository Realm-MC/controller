package com.realmmc.controller.modules.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

public class Sidebar {

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<String> currentLines = new ArrayList<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public Sidebar(Player player) {
        this.player = player;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("sidebar", "dummy", Component.empty());
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(scoreboard);
    }

    public void setTitle(String title) {
        Component component = miniMessage.deserialize(title);
        objective.displayName(component);
    }

    public void setLines(List<String> newLines) {
        if (newLines.size() != currentLines.size()) {
            resetScores();
        }

        int score = 15;

        for (String lineText : newLines) {
            setLine(score, lineText);
            score--;
            if (score < 1) break;
        }

        this.currentLines.clear();
        this.currentLines.addAll(newLines);
    }

    private void setLine(int score, String text) {
        String teamName = "sl_" + score;
        Team team = scoreboard.getTeam(teamName);
        String entry = ChatColor.values()[score].toString();

        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.addEntry(entry);
        }

        Component component = miniMessage.deserialize(text);

        try {
            team.prefix(component);
            objective.getScore(entry).setScore(score);
        } catch (NoSuchMethodError e) {
            String legacyText = legacy.serialize(component);
            if (legacyText.length() > 64) legacyText = legacyText.substring(0, 64);
            team.setPrefix(legacyText);
            objective.getScore(entry).setScore(score);
        }
    }

    private void resetScores() {
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
    }

    public void remove() {
    }
}