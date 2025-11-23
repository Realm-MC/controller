package com.realmmc.controller.spigot.listeners;

import com.realmmc.controller.shared.annotations.Listeners;
import com.realmmc.controller.spigot.inventory.Menu;
import com.realmmc.controller.spigot.inventory.MenuItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

@Listeners
public class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }

        event.setCancelled(true); // Bloqueia interação por padrão

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return; // Clicou no inventário do jogador
        }

        MenuItem item = menu.getItem(event.getSlot());
        if (item != null) {
            item.execute(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) {
            menu.cancel(); // Para tarefas de update
            Menu.openMenus.remove(event.getPlayer().getUniqueId());
        }
    }
}