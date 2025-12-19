package com.palacesky.controller.shared.cosmetics.medals;

import com.palacesky.controller.shared.messaging.MessageKey;
import lombok.Getter;

@Getter
public enum MedalRarity {
    COMUM(MessageKey.MEDAL_RARITY_COMMON),
    INCOMUM(MessageKey.MEDAL_RARITY_UNCOMMON),
    RARA(MessageKey.MEDAL_RARITY_RARE),
    EPICA(MessageKey.MEDAL_RARITY_EPIC),
    LENDARIA(MessageKey.MEDAL_RARITY_LEGENDARY),
    EVENTO(MessageKey.MEDAL_RARITY_EVENT),
    SAZONAL(MessageKey.MEDAL_RARITY_SEASONAL),
    UNICA(MessageKey.MEDAL_RARITY_UNIQUE);

    private final MessageKey displayKey;

    MedalRarity(MessageKey displayKey) {
        this.displayKey = displayKey;
    }
}