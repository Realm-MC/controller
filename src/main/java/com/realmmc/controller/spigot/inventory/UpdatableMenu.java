package com.realmmc.controller.spigot.inventory;

import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.utils.TaskScheduler;
import com.realmmc.controller.spigot.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class UpdatableMenu extends Menu {

    private final long updateIntervalTicks;
    private ScheduledFuture<?> updateTask;

    public UpdatableMenu(Profile profile, String title, int rows, boolean returnBack, long updateIntervalTicks) {
        super(profile, title, rows, returnBack);
        this.updateIntervalTicks = updateIntervalTicks;
    }

    @Override
    public void open() {
        super.open();
        startUpdating();
    }

    private void startUpdating() {
        stopUpdating();
        if (updateIntervalTicks <= 0) return;

        long periodMs = updateIntervalTicks * 50;

        this.updateTask = TaskScheduler.runAsyncTimer(() -> {
            Player p = resolvePlayer();
            if (p == null || !p.isOnline()) {
                stopUpdating();
                return;
            }

            Bukkit.getScheduler().runTask(Main.getInstance(), this::updateMenu);

        }, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    private void stopUpdating() {
        if (updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
        }
    }

    @Override
    public void cancel() {
        stopUpdating();
        super.cancel();
    }

    public void updateMenu() {
        setupItems();
        update();
    }

    private void update() {
        Player player = resolvePlayer();
        if (player == null) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() == this) {
            for(int i=0; i<getSize(); i++) {
                MenuItem item = getItem(i);
                if(item != null) {
                    top.setItem(i, item.getItemStack());
                } else {
                    top.setItem(i, null);
                }
            }
        }
    }
}