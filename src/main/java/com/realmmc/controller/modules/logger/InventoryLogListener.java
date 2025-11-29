package com.realmmc.controller.modules.logger;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventoryLogListener implements Listener {

    private final LogService logService;

    public InventoryLogListener(LogService logService) {
        this.logService = logService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInv = e.getClickedInventory();
        if (clickedInv == null) return;

        if (clickedInv.getType() == InventoryType.PLAYER || clickedInv.getType() == InventoryType.CRAFTING) {
            if (!e.isShiftClick()) return;
        }

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            item = e.getCursor();
        }

        if (item == null || item.getType().isAir()) return;

        String containerType = e.getView().getTopInventory().getType().name();
        String action = e.getAction().name();
        String itemInfo = item.getAmount() + "x " + item.getType().name();

        Location loc = player.getLocation();
        String locationStr = String.format("%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        logService.log("INV", String.format(
                "%s interagiu com %s (%s): %s em %s",
                player.getName(),
                containerType,
                action,
                itemInfo,
                locationStr
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack item = e.getItemDrop().getItemStack();
        String itemInfo = item.getAmount() + "x " + item.getType().name();
        Location loc = e.getPlayer().getLocation();

        logService.log("DROP", String.format(
                "%s dropou %s em %.1f, %.1f, %.1f",
                e.getPlayer().getName(),
                itemInfo,
                loc.getX(), loc.getY(), loc.getZ()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;

        ItemStack item = e.getItem().getItemStack();
        String itemInfo = item.getAmount() + "x " + item.getType().name();

        logService.log("PICKUP", String.format(
                "%s apanhou %s",
                player.getName(),
                itemInfo
        ));
    }
}