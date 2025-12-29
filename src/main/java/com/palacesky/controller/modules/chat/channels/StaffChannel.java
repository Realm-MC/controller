package com.palacesky.controller.modules.chat.channels;

import com.palacesky.controller.shared.chat.ChatChannel;
import com.palacesky.controller.shared.messaging.MessageKey;
import com.palacesky.controller.shared.messaging.Messages;

public class StaffChannel implements ChatChannel {

    @Override
    public String getId() {
        return "staff";
    }

    @Override
    public String getPermission() {
        return "controller.helper";
    }

    @Override
    public String getFormat() {
        return Messages.translate(MessageKey.STAFFCHAT_FORMAT)
                .replace("{formatted_name}", "{player}");
    }

    @Override
    public boolean isGlobal() {
        return true;
    }
}