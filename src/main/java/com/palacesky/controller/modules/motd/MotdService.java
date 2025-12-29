package com.palacesky.controller.modules.motd;

import com.palacesky.controller.core.modules.AutoRegister;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.motd.data.MotdData;
import com.palacesky.controller.modules.motd.data.MotdRepository;
import com.palacesky.controller.shared.storage.redis.RedisChannel;
import com.palacesky.controller.shared.storage.redis.RedisMessageListener;
import com.palacesky.controller.shared.storage.redis.RedisPublisher;
import com.palacesky.controller.shared.storage.redis.RedisSubscriber;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.PROXY})
public class MotdService implements RedisMessageListener {

    private final Logger logger;
    private final MotdRepository repository;
    private MotdData cachedData;
    private final Set<UUID> editors = ConcurrentHashMap.newKeySet();

    private static final String DEFAULT_LINE_1 = "&6&lPalaceSky&6.com &e⚡︎ &7[1.21.4]";
    private static final String DEFAULT_LINE_2 = "&ewww.palacesky.com";
    private static final String WRONG_VERSION_LINE_1 = "&6&lPalaceSky&6.com &e⚡︎ &7[1.21.4]";
    private static final String WRONG_VERSION_LINE_2 = "&cEste servidor é exclusivo para a versão &l1.21.4";
    private static final String WRONG_VERSION_HOVER = "&c⚠️Versão não suportada!";

    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public MotdService(Logger logger) {
        this.logger = logger;
        this.repository = new MotdRepository();
        loadFromDb();
        ServiceRegistry.getInstance().getService(RedisSubscriber.class)
                .ifPresent(sub -> sub.registerListener(RedisChannel.CONTROLLER_BROADCAST, this));
    }

    private void loadFromDb() {
        this.cachedData = repository.getGlobalMotd().orElseGet(() -> {
            MotdData def = MotdData.builder().id("global_motd").line1(DEFAULT_LINE_1).line2(DEFAULT_LINE_2).custom(false).updatedAt(System.currentTimeMillis()).build();
            try { repository.save(def); } catch (Exception e) { e.printStackTrace(); }
            return def;
        });
    }

    public void setLine2(String newLine2) {
        if (cachedData == null) loadFromDb();
        this.cachedData.setLine2(newLine2);
        this.cachedData.setCustom(true);
        this.cachedData.setUpdatedAt(System.currentTimeMillis());
        saveAndPublish();
    }

    public void resetToDefault() {
        if (cachedData == null) loadFromDb();
        this.cachedData.setLine1(DEFAULT_LINE_1);
        this.cachedData.setLine2(DEFAULT_LINE_2);
        this.cachedData.setCustom(false);
        this.cachedData.setUpdatedAt(System.currentTimeMillis());
        saveAndPublish();
    }

    private void saveAndPublish() {
        try {
            repository.save(this.cachedData);
            RedisPublisher.publish(RedisChannel.CONTROLLER_BROADCAST, "motd_update");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erro ao salvar MOTD", e);
        }
    }

    public void addEditor(UUID uuid) { this.editors.add(uuid); }
    public void removeEditor(UUID uuid) { this.editors.remove(uuid); }
    public boolean isEditing(UUID uuid) { return this.editors.contains(uuid); }

    public Component getMotdComponent() {
        if (cachedData == null) loadFromDb();
        String combined = cachedData.getLine1() + "\n" + cachedData.getLine2();
        return legacy.deserialize(combined);
    }

    public Component getWrongVersionMotd() { return legacy.deserialize(WRONG_VERSION_LINE_1 + "\n" + WRONG_VERSION_LINE_2); }
    public Component getWrongVersionHover() { return legacy.deserialize(WRONG_VERSION_HOVER); }
    public String getCurrentLine1() { return cachedData != null ? cachedData.getLine1() : DEFAULT_LINE_1; }
    public String getCurrentLine2() { return cachedData != null ? cachedData.getLine2() : DEFAULT_LINE_2; }
    public boolean isCustom() { return cachedData != null && cachedData.isCustom(); }

    @Override
    public void onMessage(String channel, String message) {
        if ("motd_update".equals(message)) loadFromDb();
    }
}