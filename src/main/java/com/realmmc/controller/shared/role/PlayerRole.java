package com.realmmc.controller.shared.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerRole {

    public enum Status {
        ACTIVE,
        EXPIRED,
        REMOVED
    }

    @Builder.Default
    private String instanceId = UUID.randomUUID().toString().substring(0, 8);

    private String addedBy;

    private String roleName;

    @Builder.Default
    private long addedAt = System.currentTimeMillis();

    @Builder.Default
    private Long expiresAt = null;

    @Builder.Default
    private boolean paused = false;

    @Builder.Default
    private Long pausedTimeRemaining = null;

    @Builder.Default
    private Status status = Status.ACTIVE;

    @Builder.Default
    private Long removedAt = null;

    @Builder.Default
    private boolean pendingNotification = false;

    public boolean isPermanent() {
        return expiresAt == null && pausedTimeRemaining == null;
    }

    public boolean hasExpired() {
        return !isPaused() && expiresAt != null && System.currentTimeMillis() > expiresAt;
    }

    public boolean isActive() {
        return status == Status.ACTIVE && !isPaused() && !hasExpired();
    }
}