package com.palacesky.controller.proxy.listeners;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.palacesky.controller.core.services.ServiceRegistry;
import com.palacesky.controller.modules.motd.MotdService;
import com.palacesky.controller.proxy.Proxy;
import com.palacesky.controller.shared.sounds.SoundKeys;
import com.palacesky.controller.shared.sounds.SoundPlayer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class MotdChatListener extends PacketListenerAbstract {

    private final MotdService motdService;
    private final Optional<SoundPlayer> soundPlayerOpt;
    private final ProxyServer proxyServer;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public MotdChatListener() {
        super(PacketListenerPriority.LOWEST);
        this.motdService = ServiceRegistry.getInstance().requireService(MotdService.class);
        this.soundPlayerOpt = ServiceRegistry.getInstance().getService(SoundPlayer.class);
        this.proxyServer = ServiceRegistry.getInstance().requireService(ProxyServer.class);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE) {

            UUID uuid = event.getUser().getUUID();

            if (!motdService.isEditing(uuid)) {
                return;
            }

            event.setCancelled(true);

            Optional<Player> playerOpt = proxyServer.getPlayer(uuid);
            if (playerOpt.isEmpty()) return;
            Player player = playerOpt.get();

            WrapperPlayClientChatMessage wrapper = new WrapperPlayClientChatMessage(event);
            String message = wrapper.getMessage();

            if (message.equalsIgnoreCase("cancelar") || message.equalsIgnoreCase("\"cancelar\"")) {
                motdService.removeEditor(uuid);
                player.sendMessage(legacy.deserialize("&cAlteração da MOTD cancelada. Nenhuma mudança foi aplicada."));
                playSound(player, SoundKeys.ERROR);
                return;
            }

            motdService.setLine2(message);
            motdService.removeEditor(uuid);

            player.sendMessage(legacy.deserialize("&aA segunda linha do MOTD foi alterada com sucesso!"));
            playSound(player, SoundKeys.SUCCESS);
        }
    }

    private void playSound(Player player, String key) {
        soundPlayerOpt.ifPresent(sp -> sp.playSound(player, key));
    }
}