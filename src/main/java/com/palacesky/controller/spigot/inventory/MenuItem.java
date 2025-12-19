package com.palacesky.controller.spigot.inventory;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import java.util.function.Consumer;

public class MenuItem {
    private final ItemStack itemStack;
    private final Consumer<InventoryClickEvent> action;

    public MenuItem(ItemStack itemStack, Consumer<InventoryClickEvent> action) {
        this.itemStack = itemStack;
        this.action = action;
    }

    public MenuItem(ItemStack itemStack) {
        this(itemStack, null);
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void execute(InventoryClickEvent event) {
        if (action != null) {
            action.accept(event);
        }
    }
}