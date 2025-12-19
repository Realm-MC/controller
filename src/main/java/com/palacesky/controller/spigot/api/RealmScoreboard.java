package com.palacesky.controller.spigot.api;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

public class RealmScoreboard {

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<String> currentLines = new ArrayList<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public RealmScoreboard(Player player) {
        this.player = player;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("sidebar", "dummy", Component.empty());
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        this.objective.numberFormat(NumberFormat.blank());

        player.setScoreboard(scoreboard);
    }

    public void updateTitle(String title) {
        if (title == null) return;
        try {
            objective.displayName(miniMessage.deserialize(title));
        } catch (Exception e) {
            objective.setDisplayName(title);
        }
    }

    public void updateLines(List<String> newLines) {
        if (newLines == null) return;

        if (newLines.size() < currentLines.size()) {
            for (int i = newLines.size(); i < currentLines.size(); i++) {
                resetLine(getScoreByLine(i));
            }
        }

        int scoreBase = 15;

        for (int i = 0; i < newLines.size(); i++) {
            if (i >= 15) break;
            setLine(scoreBase - i, newLines.get(i));
        }

        this.currentLines.clear();
        this.currentLines.addAll(newLines);
    }

    private int getScoreByLine(int index) {
        return 15 - index;
    }

    private void setLine(int scoreValue, String text) {
        String teamName = "sb_line_" + scoreValue;
        Team team = scoreboard.getTeam(teamName);

        String entry;
        try {
            entry = ChatColor.values()[scoreValue].toString();
        } catch (IndexOutOfBoundsException e) {
            return;
        }

        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.addEntry(entry);
        }

        try {
            team.prefix(miniMessage.deserialize(text));
        } catch (Exception e) {
            team.setPrefix(text);
        }

        Score scoreObj = objective.getScore(entry);
        scoreObj.setScore(scoreValue);

        scoreObj.numberFormat(NumberFormat.blank());
    }

    private void resetLine(int score) {
        try {
            String entry = ChatColor.values()[score].toString();
            scoreboard.resetScores(entry);
        } catch (Exception ignored) {}
    }

    public void delete() {
        try {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        } catch (Exception ignored) {}
    }
}