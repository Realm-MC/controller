package com.realmmc.controller.shared.profile;

import com.realmmc.controller.shared.role.PlayerRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile {
    @BsonId
    private Integer id;
    private UUID uuid;
    private String name;
    private String username;
    private String firstIp;
    private String lastIp;
    @Builder.Default
    private List<String> ipHistory = new ArrayList<>();
    private long firstLogin;
    private long lastLogin;
    @Builder.Default
    private long lastLogout = 0L;
    private String lastClientVersion;
    private String lastClientType;
    private String firstClientVersion;
    private String firstClientType;
    @Builder.Default
    private List<PlayerRole> roles = new ArrayList<>();
    private String primaryRoleName;
    @Builder.Default
    private int cash = 0;
    @Builder.Default
    private int pendingCash = 0;
    private Integer cashTopPosition;
    private Long cashTopPositionEnteredAt;
    @Builder.Default
    private boolean premiumAccount = false;
    @Builder.Default
    private String equippedMedal = "none";
    private long createdAt;
    private long updatedAt;
}