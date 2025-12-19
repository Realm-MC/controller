package com.palacesky.controller.shared.cosmetics.medals;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnlockedMedal {
    private String medalId;
    private long obtainedAt;
    private Long expiresAt;

    public boolean hasExpired() {
        return expiresAt != null && System.currentTimeMillis() > expiresAt;
    }
}