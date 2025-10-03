package com.realmmc.controller.spigot.entities.displayitems;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.realmmc.controller.spigot.Main;
import com.realmmc.controller.spigot.entities.actions.Actions;
import com.realmmc.controller.spigot.entities.config.DisplayConfigLoader;
import com.realmmc.controller.spigot.entities.config.DisplayEntry;
import com.realmmc.controller.spigot.entities.config.NPCConfigLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayItemService {
    private final Map<UUID, List<UUID>> spawnedByPlayer = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DisplayConfigLoader configLoader;
    private final Map<Integer, String> entityIdToEntryId = new ConcurrentHashMap<>();
    private final Map<String, Long> clickDebounce = new ConcurrentHashMap<>();
    private PacketListenerAbstract interactListener;

    public DisplayItemService() {
        this.configLoader = new DisplayConfigLoader();
        this.configLoader.load();
        try { clearAll(); } catch (Throwable ignored) {}
        loadSavedDisplays();
        try {
            if (interactListener == null) {
                interactListener = new PacketListenerAbstract() {
                    @Override
                    public void onPacketReceive(PacketReceiveEvent event) {
                        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                            int targetId = wrapper.getEntityId();
                            String entryId = entityIdToEntryId.get(targetId);
                            if (entryId == null) {
                                return;
                            }
                            Player player = event.getPlayer();
                            String actionName = String.valueOf(wrapper.getAction());
                            String handName = String.valueOf(wrapper.getHand());
                            if (handName != null && handName.equals("OFF_HAND")) {
                                return;
                            }

                            long now = System.currentTimeMillis();
                            String key = player.getUniqueId() + ":" + targetId;
                            Long last = clickDebounce.get(key);
                            if (last != null && now - last < 300) {
                                return;
                            }
                            clickDebounce.put(key, now);

                            DisplayEntry entry = configLoader.getById(entryId);
                            if (entry == null) {
                                return;
                            }
                            if (entry.getActions() == null || entry.getActions().isEmpty()) {
                                return;
                            }
                            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                                World w = player.getWorld();
                                try {
                                    World maybe = Bukkit.getWorld(entry.getWorld());
                                    if (maybe != null) w = maybe;
                                } catch (Throwable ignored) {}
                                Location eloc = new Location(w, entry.getX(), entry.getY(), entry.getZ(), entry.getYaw(), entry.getPitch());
                                Actions.runAll(player, entry, eloc, entry.getActions());
                            });
                        }
                    }
                };
                PacketEvents.getAPI().getEventManager().registerListener(interactListener);
            }
        } catch (Throwable ignored) {}
    }

    private void loadSavedDisplays() {
        for (DisplayEntry entry : configLoader.getEntries()) {
            if (entry.getType() != DisplayEntry.Type.DISPLAY_ITEM) {
                continue;
            }

            try {
                World world = Bukkit.getWorld(entry.getWorld());
                if (world == null) continue;

                Location location = new Location(world, entry.getX(), entry.getY(), entry.getZ(),
                        entry.getYaw(), entry.getPitch());

                ItemStack item = new ItemStack(Material.valueOf(entry.getItem()));
                Display.Billboard billboard = Display.Billboard.valueOf(entry.getBillboard());

                showWithoutSaving(entry, location, item, entry.getLines(), entry.getGlow(), billboard, entry.getScale());

            } catch (Exception e) {
                System.err.println("Erro ao carregar display ID " + entry.getId() + ": " + e.getMessage());
            }
        }
    }

    private void showWithoutSaving(DisplayEntry entry, Location base, ItemStack item, List<String> lines, boolean glow,
                                   Display.Billboard billboard, float scale) {
        List<UUID> entities = new ArrayList<>();

        Location itemLocation = base.clone().add(0, 1.4, 0);

        ItemDisplay itemDisplay = base.getWorld().spawn(itemLocation, ItemDisplay.class);
        itemDisplay.setItemStack(item != null ? item : new ItemStack(Material.DIAMOND));
        itemDisplay.setBillboard(billboard);
        itemDisplay.setShadowStrength(0.0f);
        itemDisplay.setBrightness(new Display.Brightness(15, 15));
        itemDisplay.setGlowing(glow);
        itemDisplay.customName(null);
        itemDisplay.setCustomNameVisible(false);
        itemDisplay.addScoreboardTag("controller_display_item");
        try { entityIdToEntryId.put(itemDisplay.getEntityId(), entry.getId()); } catch (Throwable ignored) {}

        Transformation transformation = new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(0, 0, 0, 1),
                new Vector3f(scale, scale, scale),
                new Quaternionf(0, 0, 0, 1)
        );
        itemDisplay.setTransformation(transformation);
        entities.add(itemDisplay.getUniqueId());

        try {
            ArmorStand hitbox = base.getWorld().spawn(itemLocation.clone().add(0, 0.2, 0), ArmorStand.class);
            hitbox.setInvisible(true);
            hitbox.setSmall(false);
            hitbox.setMarker(false);
            hitbox.setGravity(false);
            hitbox.setCollidable(false);
            hitbox.getEquipment().clear();
            hitbox.customName(null);
            hitbox.setCustomNameVisible(false);
            hitbox.addScoreboardTag("controller_display_hitbox");
            entities.add(hitbox.getUniqueId());
            entityIdToEntryId.put(hitbox.getEntityId(), entry.getId());
        } catch (Throwable ignored) {}

        if (lines != null && !lines.isEmpty()) {
            double baseY = itemLocation.getY() + (scale * 0.5);
            double step = 0.25;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                double textY = baseY + (lines.size() - 1 - i) * step;
                Location textLocation = new Location(base.getWorld(), base.getX(), textY, base.getZ());

                TextDisplay textDisplay = base.getWorld().spawn(textLocation, TextDisplay.class);
                Component component = miniMessage.deserialize(line);
                textDisplay.text(component);
                textDisplay.setBillboard(Display.Billboard.CENTER);
                textDisplay.setSeeThrough(true);
                textDisplay.setDefaultBackground(false);
                textDisplay.setShadowed(false);
                textDisplay.setLineWidth(200);
                textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
                textDisplay.setGlowing(glow);
                textDisplay.customName(null);
                textDisplay.setCustomNameVisible(false);
                textDisplay.addScoreboardTag("controller_display_line");
                try { entityIdToEntryId.put(textDisplay.getEntityId(), entry.getId()); } catch (Throwable ignored) {}

                entities.add(textDisplay.getUniqueId());
            }

            try {
                int segments = Math.max(1, (int) Math.ceil(lines.size() * 0.5));
                for (int s = 0; s < segments; s++) {
                    double y = base.getY() + 0.3 + s * 0.6;
                    ArmorStand hb = base.getWorld().spawn(new Location(base.getWorld(), base.getX(), y, base.getZ()), ArmorStand.class);
                    hb.setInvisible(true);
                    hb.setSmall(false);
                    hb.setMarker(false);
                    hb.setGravity(false);
                    hb.setCollidable(false);
                    hb.getEquipment().clear();
                    hb.customName(null);
                    hb.setCustomNameVisible(false);
                    hb.addScoreboardTag("controller_display_hitbox");
                    entities.add(hb.getUniqueId());
                    entityIdToEntryId.put(hb.getEntityId(), entry.getId());
                }
            } catch (Throwable ignored) {}
        }
    }

    public void show(Player player, Location base, ItemStack item, List<String> lines, boolean glow, Display.Billboard billboard, float scale, String id) {
        DisplayEntry entry = new DisplayEntry();
        entry.setId(id);
        entry.setType(DisplayEntry.Type.DISPLAY_ITEM);
        entry.setWorld(base.getWorld().getName());
        entry.setX(base.getX());
        entry.setY(base.getY());
        entry.setZ(base.getZ());
        entry.setYaw(base.getYaw());
        entry.setPitch(base.getPitch());
        entry.setItem(item != null ? item.getType().name() : Material.DIAMOND.name());
        if (lines == null || lines.isEmpty()) {
            lines = List.of("display_item");
        }
        entry.setLines(lines);
        entry.setGlow(glow);
        entry.setBillboard(billboard.name());
        entry.setScale(scale);
        if (entry.getActions() == null) {
            entry.setActions(new ArrayList<>());
        }

        configLoader.addEntry(entry);
        configLoader.save();

        showWithoutSaving(entry, base, item, lines, glow, billboard, scale);
    }

    public void reload() {
        configLoader.load();
        clearAll();
        loadSavedDisplays();
    }

    public void clearAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ItemDisplay && entity.getScoreboardTags().contains("controller_display_item")) {
                    entity.remove();
                } else if (entity instanceof TextDisplay && entity.getScoreboardTags().contains("controller_display_line")) {
                    entity.remove();
                } else if (entity instanceof ArmorStand && entity.getScoreboardTags().contains("controller_display_hitbox")) {
                    entity.remove();
                }
            }
        }
    }

    private Entity findEntityByUuid(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}