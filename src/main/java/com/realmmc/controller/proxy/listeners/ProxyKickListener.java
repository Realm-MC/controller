package com.realmmc.controller.proxy.listeners;

import com.realmmc.controller.shared.annotations.Listeners;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent.DisconnectPlayer;
import com.velocitypowered.api.event.player.KickedFromServerEvent.Notify;
import com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer;
import net.kyori.adventure.text.Component;

@Listeners
public class ProxyKickListener {

    @Subscribe
    public void onKick(KickedFromServerEvent event) {
        if (event.getServer().getServerInfo().getName().startsWith("login")) {

            Component reason = event.getServerKickReason().orElse(Component.empty());

            event.setResult(DisconnectPlayer.create(reason));
        }
    }
}