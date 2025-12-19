package com.palacesky.controller.shared.cosmetics.medals;

import com.palacesky.controller.shared.messaging.MessageKey;
import lombok.Getter;

@Getter
public enum MedalCategory {
    GERAL(MessageKey.MEDAL_CATEGORY_GENERAL),
    PVP(MessageKey.MEDAL_CATEGORY_PVP),
    EVENTOS(MessageKey.MEDAL_CATEGORY_EVENT),
    VIP(MessageKey.MEDAL_CATEGORY_VIP),
    ESPECIAL(MessageKey.MEDAL_CATEGORY_SPECIAL);

    private final MessageKey displayKey;

    MedalCategory(MessageKey displayKey) {
        this.displayKey = displayKey;
    }
}