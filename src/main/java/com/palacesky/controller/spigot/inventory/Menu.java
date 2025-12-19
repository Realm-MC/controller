package com.palacesky.controller.spigot.inventory;

import com.palacesky.controller.shared.profile.Profile;
import com.palacesky.controller.spigot.utils.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Menu implements InventoryHolder {

    public static final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();

    protected final Profile profile;
    private final String title;
    private final int size;
    private final boolean returnBack;

    private Inventory backingInventory;
    private final Map<Integer, MenuItem> menuItems = new LinkedHashMap<>();

    protected Menu previousMenu;
    protected int realSize;

    public Menu(Profile profile, String title, int rows, boolean returnBack) {
        this.profile = profile;
        this.title = title;
        this.size = Math.max(1, Math.min(6, rows)) * 9;
        this.returnBack = returnBack;
    }

    public abstract void setupItems();

    protected void addBackButtonIfNeeded() {
        if (returnBack && previousMenu != null) {
            // Coloca o botão de voltar no canto inferior esquerdo ou último slot livre
            // Ajuste conforme design desejado
            int slot = realSize - 9;
            if(slot < 0) slot = 0;

            setItem(slot, new MenuItem(
                    new ItemBuilder(Material.ARROW).name("<red>Voltar").build(),
                    event -> previousMenu.open()
            ));
        }
    }

    public void open() {
        this.realSize = size;
        // Se tiver botão de voltar e for pequeno, aumenta tamanho (opcional)
        if (returnBack && previousMenu != null && size <= 45) {
            // Lógica opcional para aumentar inventário se precisar de espaço pro botão
        }

        this.backingInventory = Bukkit.createInventory(this, realSize, MiniMessage.miniMessage().deserialize(title));
        this.menuItems.clear();

        setupItems();
        addBackButtonIfNeeded();

        // Popula o inventário
        for (Map.Entry<Integer, MenuItem> entry : menuItems.entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < realSize) {
                backingInventory.setItem(entry.getKey(), entry.getValue().getItemStack());
            }
        }

        Player player = resolvePlayer();
        if (player != null) {
            player.openInventory(backingInventory);
            openMenus.put(player.getUniqueId(), this);
        }
    }

    protected void setItem(int slot, MenuItem menuItem) {
        menuItems.put(slot, menuItem);
    }

    public MenuItem getItem(int slot) {
        return menuItems.get(slot);
    }

    public void cancel() {
        // Hook para limpeza
    }

    public <T extends Menu> void openNextMenu(T next) {
        next.previousMenu = this;
        next.open();
    }

    public void refreshInventory() {
        if (backingInventory == null) return;
        // Limpa e recria
        backingInventory.clear();
        menuItems.clear();
        setupItems();
        addBackButtonIfNeeded();

        for (Map.Entry<Integer, MenuItem> entry : menuItems.entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < realSize) {
                backingInventory.setItem(entry.getKey(), entry.getValue().getItemStack());
            }
        }
        // Atualiza view
        Player p = resolvePlayer();
        if(p != null) p.updateInventory();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return backingInventory;
    }

    protected Player resolvePlayer() {
        return Bukkit.getPlayer(profile.getUuid());
    }

    public int getSize() { return size; }
}