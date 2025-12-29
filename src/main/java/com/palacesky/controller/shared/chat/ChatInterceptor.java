package com.palacesky.controller.shared.chat;

import java.util.UUID;

public interface ChatInterceptor {
    boolean canSend(String channelId, UUID senderUuid, String message);
}