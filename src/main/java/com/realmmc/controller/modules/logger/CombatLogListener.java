package com.realmmc.controller.modules.logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;

public class CombatLogListener implements Listener {

    private final LogService logService;
    private final DecimalFormat df = new DecimalFormat("0.0");

    public CombatLogListener(LogService logService) {
        this.logService = logService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !(e.getDamager() instanceof Player attacker)) {
            return;
        }

        double distance = attacker.getLocation().distance(victim.getLocation());

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        String weaponName = weapon.getType().name();

        double finalDamage = e.getFinalDamage();
        double victimHealth = victim.getHealth();

        boolean isCritical = !attacker.isOnGround() && attacker.getFallDistance() > 0.0F;

        String logMessage = String.format(
                "%s (HP: %s) hit %s (HP: %s) with %s. Dmg: %s. Dist: %s blocks. Crit: %s",
                attacker.getName(), df.format(attacker.getHealth()),
                victim.getName(), df.format(victimHealth),
                weaponName,
                df.format(finalDamage),
                df.format(distance),
                isCritical ? "YES" : "NO"
        );

        logService.log("PVP", logMessage);
    }
}