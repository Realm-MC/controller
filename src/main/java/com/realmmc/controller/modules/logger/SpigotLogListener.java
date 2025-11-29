package com.realmmc.controller.modules.logger;

import com.realmmc.controller.spigot.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

public class SpigotLogListener implements Listener {

    private final LogService logService;

    public SpigotLogListener(LogService logService) {
        this.logService = logService;

        Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getInstance(), () -> {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                Location l = p.getLocation();
                String pos = String.format("%.1f, %.1f, %.1f (World: %s)", l.getX(), l.getY(), l.getZ(), l.getWorld().getName());
                logService.log("POS", p.getName() + " em " + pos);
            }
        }, 100L, 100L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        logService.log("JOIN", e.getPlayer().getName() + " entrou. IP: " + e.getPlayer().getAddress());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        logService.log("QUIT", e.getPlayer().getName() + " saiu.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent e) {
        logService.log("CHAT", e.getPlayer().getName() + ": " + e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        logService.log("CMD", e.getPlayer().getName() + " executou: " + e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction().name().contains("BLOCK")) {
            String block = e.getClickedBlock() != null ? e.getClickedBlock().getType().name() : "AIR";
            logService.log("INTERACT", e.getPlayer().getName() + " interagiu com " + block + " (" + e.getAction() + ")");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        logService.log("DEATH", e.getDeathMessage());
    }
}