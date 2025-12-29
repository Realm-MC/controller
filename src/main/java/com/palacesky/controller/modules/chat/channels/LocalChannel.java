package com.palacesky.controller.modules.chat.channels;

import com.palacesky.controller.shared.chat.ChatChannel;

public class LocalChannel implements ChatChannel {

    @Override
    public String getId() {
        return "local";
    }

    @Override
    public String getPermission() {
        return null;
    }

    @Override
    public String getFormat() {
        return "{player}<gray>: <white>{message}";
    }

    @Override
    public boolean isGlobal() {
        return false;
    }
}