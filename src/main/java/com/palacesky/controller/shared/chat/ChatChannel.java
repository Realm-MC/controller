package com.palacesky.controller.shared.chat;

import java.util.UUID;

public interface ChatChannel {

    String getId();

    String getPermission();

    String getFormat();

    boolean isGlobal();

    default boolean canSee(UUID viewerUuid) {
        return true;
    }
}