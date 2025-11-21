package com.realmmc.controller.spigot.entities.displayitems;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.entities.actions.Actions;
import com.realmmc.controller.spigot.entities.config.DisplayConfigLoader;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DisplayItemService {
    private final DisplayConfigLoader configLoader;
    private final Map<Integer, String> entityIdToEntryId = new ConcurrentHashMap<>();
    private final Map<String, Long> clickDebounce = new ConcurrentHashMap<>();
    private PacketListenerAbstract interactListener;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.builder().character('&').hexColors().build();

    public DisplayItemService() {
        this.configLoader = new DisplayConfigLoader();
        this.configLoader.load();
        try { clearAll(); } catch (Throwable ignored) {}
        loadSavedDisplays();

        this.interactListener = new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                    try {
                        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                        int targetId = wrapper.getEntityId();
                        String entryId = entityIdToEntryId.get(targetId);
                        if (entryId == null) return;

                        Player player = (Player) event.getPlayer();
                        if (wrapper.getHand() != InteractionHand.MAIN_HAND) return;

                        long now = System.currentTimeMillis();
                        String key = player.getUniqueId() + ":" + targetId;
                        if (clickDebounce.getOrDefault(key, 0L) > now - 300) return;
                        clickDebounce.put(key, now);

                        DisplayEntry entry = configLoader.getById(entryId);
                        if (entry == null) return;

                        List<String> actions = entry.getActions();
                        if (actions == null || actions.isEmpty()) return;

                        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                            World w = Bukkit.getWorld(entry.getWorld());
                            if (w == null) w = player.getWorld();
                            Location eloc = new Location(w, entry.getX(), entry.getY(), entry.getZ());
                            Actions.runAll(player, entry, eloc, actions);
                        });
                    } catch (Exception e) {
                    }
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(interactListener);
    }

    public void cleanup() {
        if (interactListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(interactListener);
        }
        clearAll();
    }

    private void loadSavedDisplays() {
        for (DisplayEntry entry : configLoader.getEntries()) {
            if (entry.getType() != DisplayEntry.Type.DISPLAY_ITEM) continue;
            try {
                World world = Bukkit.getWorld(entry.getWorld());
                if (world == null) continue;
                Location location = new Location(world, entry.getX(), entry.getY(), entry.getZ(), entry.getYaw(), entry.getPitch());
                spawnEntitiesForEntry(entry, location);
            } catch (Exception e) {
                System.err.println("Erro ao carregar display ID " + entry.getId() + ": " + e.getMessage());
            }
        }
    }

    private void spawnEntitiesForEntry(DisplayEntry entry, Location base) {
        ItemStack item = new ItemStack(Material.valueOf(entry.getItem()));
        Display.Billboard billboard = Display.Billboard.valueOf(entry.getBillboard());

        Location itemLocation = base.clone();
        ItemDisplay itemDisplay = base.getWorld().spawn(itemLocation, ItemDisplay.class);
        itemDisplay.setItemStack(item);
        itemDisplay.setBillboard(billboard);
        itemDisplay.setShadowStrength(0.0f);
        itemDisplay.setBrightness(new Display.Brightness(15, 15));
        itemDisplay.setGlowing(Boolean.TRUE.equals(entry.getGlow()));
        itemDisplay.setPersistent(false);
        itemDisplay.addScoreboardTag("controller_display_item");

        Transformation transformation = itemDisplay.getTransformation();
        float scale = entry.getScale() != null ? entry.getScale() : 1.0f;
        transformation.getScale().set(scale, scale, scale);
        itemDisplay.setTransformation(transformation);

        entityIdToEntryId.put(itemDisplay.getEntityId(), entry.getId());

        ArmorStand hitbox = base.getWorld().spawn(itemLocation.clone().add(0, scale / 2.0, 0), ArmorStand.class);
        hitbox.setInvisible(true);
        hitbox.setMarker(false);
        hitbox.setGravity(false);
        hitbox.setCollidable(false);
        hitbox.setPersistent(false);
        hitbox.addScoreboardTag("controller_display_hitbox");
        entityIdToEntryId.put(hitbox.getEntityId(), entry.getId());

        List<String> lines = entry.getLines();
        if (lines != null && !lines.isEmpty() && Boolean.TRUE.equals(entry.getHologramVisible())) {

            double lineStep = 0.30;
            double baseOffsetY = -0.30;

            double lowestLineY = base.getY() + baseOffsetY;

            double totalHologramHeight = (lines.size() - 1) * lineStep;
            double highestLineY = lowestLineY + totalHologramHeight;

            double lineY = highestLineY;

            for (String lineText : lines) {
                TextDisplay textDisplay = base.getWorld().spawn(new Location(base.getWorld(), base.getX(), lineY, base.getZ()), TextDisplay.class);
                textDisplay.text(MiniMessage.miniMessage().deserialize(MiniMessage.miniMessage().serialize(legacySerializer.deserialize(lineText))));
                textDisplay.setBillboard(Display.Billboard.CENTER);
                textDisplay.setSeeThrough(true);
                textDisplay.setDefaultBackground(false);
                textDisplay.setShadowed(true);
                textDisplay.setPersistent(false);
                textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
                textDisplay.addScoreboardTag("controller_display_item");

                lineY -= lineStep;
            }
        }
    }

    public void reload() {
        clearAll();
        configLoader.load();
        loadSavedDisplays();
    }

    public void clearAll() {
        entityIdToEntryId.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("controller_display_item") ||
                        entity.getScoreboardTags().contains("controller_display_hitbox")) {
                    entity.remove();
                }
            }
        }
    }

    public void createDisplay(String id, Location location, Material material) {
        DisplayEntry entry = new DisplayEntry();
        entry.setId(id);
        entry.setType(DisplayEntry.Type.DISPLAY_ITEM);
        entry.setWorld(location.getWorld().getName());
        entry.setX(location.getX());
        entry.setY(location.getY());
        entry.setZ(location.getZ());
        entry.setYaw(location.getYaw());
        entry.setPitch(location.getPitch());
        entry.setItem(material.name());
        entry.setLines(new ArrayList<>());
        entry.setGlow(false);
        entry.setBillboard(Display.Billboard.CENTER.name());
        entry.setScale(1.0f);
        entry.setActions(new ArrayList<>());
        entry.setHologramVisible(true);

        configLoader.addEntry(entry);
        configLoader.save();
        reload();
    }

    public void cloneDisplay(String originalId, String newId, Location location) {
        DisplayEntry originalEntry = configLoader.getById(originalId);
        if (originalEntry == null) return;

        DisplayEntry newEntry = new DisplayEntry();
        newEntry.setId(newId);
        newEntry.setType(originalEntry.getType());
        newEntry.setItem(originalEntry.getItem());
        newEntry.setScale(originalEntry.getScale());
        newEntry.setBillboard(originalEntry.getBillboard());
        newEntry.setGlow(originalEntry.getGlow());
        newEntry.setHologramVisible(originalEntry.getHologramVisible());
        newEntry.setLines((originalEntry.getLines() != null) ? new ArrayList<>(originalEntry.getLines()) : new ArrayList<>());
        newEntry.setActions((originalEntry.getActions() != null) ? new ArrayList<>(originalEntry.getActions()) : new ArrayList<>());

        newEntry.setWorld(location.getWorld().getName());
        newEntry.setX(location.getX());
        newEntry.setY(location.getY());
        newEntry.setZ(location.getZ());
        newEntry.setYaw(location.getYaw());
        newEntry.setPitch(location.getPitch());

        configLoader.addEntry(newEntry);
        configLoader.save();
        reload();
    }

    public void removeDisplay(String id) {
        if (configLoader.removeEntry(id)) {
            reload();
        }
    }

    public void teleportDisplay(String id, Location location) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            entry.setWorld(location.getWorld().getName());
            entry.setX(location.getX());
            entry.setY(location.getY());
            entry.setZ(location.getZ());
            entry.setYaw(location.getYaw());
            entry.setPitch(location.getPitch());
            configLoader.updateEntry(entry);
            configLoader.save();
            reload();
        }
    }

    public void setItem(String id, Material material) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            entry.setItem(material.name());
            configLoader.updateEntry(entry);
            configLoader.save();
            reload();
        }
    }

    public void setScale(String id, float scale) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            entry.setScale(scale);
            configLoader.updateEntry(entry);
            configLoader.save();
            reload();
        }
    }

    public void setBillboard(String id, String billboardType) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            entry.setBillboard(billboardType);
            configLoader.updateEntry(entry);
            configLoader.save();
            reload();
        }
    }

    public boolean toggleGlow(String id) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            boolean newState = !Boolean.TRUE.equals(entry.getGlow());
            entry.setGlow(newState);
            configLoader.updateEntry(entry);
            configLoader.save();
            reload();
            return newState;
        }
        return false;
    }

    public boolean toggleLinesVisibility(String id) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            boolean newState = !Boolean.TRUE.equals(entry.getHologramVisible());
            entry.setHologramVisible(newState);
            configLoader.updateEntry(entry);
            configLoader.save();
            reload();
            return newState;
        }
        return false;
    }

    public void addLine(String id, String text) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> lines = (entry.getLines() != null) ? new ArrayList<>(entry.getLines()) : new ArrayList<>();
            lines.add(text);
            entry.setLines(lines);
            configLoader.updateEntry(entry);
            configLoader.save();
            reload();
        }
    }

    public boolean setLine(String id, int lineIndex, String text) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> lines = entry.getLines();
            if (lines == null) return false;
            if (lineIndex > 0 && lineIndex <= lines.size()) {
                lines.set(lineIndex - 1, text);
                entry.setLines(lines);
                configLoader.updateEntry(entry);
                configLoader.save();
                reload();
                return true;
            }
        }
        return false;
    }

    public boolean removeLine(String id, int lineIndex) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> lines = entry.getLines();
            if (lines == null) return false;
            if (lineIndex > 0 && lineIndex <= lines.size()) {
                lines.remove(lineIndex - 1);
                entry.setLines(lines);
                configLoader.updateEntry(entry);
                configLoader.save();
                reload();
                return true;
            }
        }
        return false;
    }

    public void addAction(String id, String action) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> actions = (entry.getActions() != null) ? new ArrayList<>(entry.getActions()) : new ArrayList<>();
            actions.add(action);
            entry.setActions(actions);
            configLoader.updateEntry(entry);
            configLoader.save();
        }
    }

    public boolean removeAction(String id, int actionIndex) {
        DisplayEntry entry = configLoader.getById(id);
        if (entry != null) {
            List<String> actions = entry.getActions();
            if (actions == null) return false;
            if (actionIndex > 0 && actionIndex <= actions.size()) {
                actions.remove(actionIndex - 1);
                entry.setActions(actions);
                configLoader.updateEntry(entry);
                configLoader.save();
                return true;
            }
        }
        return false;
    }

    public DisplayEntry getDisplayEntry(String id) {
        return configLoader.getById(id);
    }

    public Set<String> getAllDisplayIds() {
        return configLoader.getEntries().stream()
                .map(DisplayEntry::getId)
                .collect(Collectors.toSet());
    }
}