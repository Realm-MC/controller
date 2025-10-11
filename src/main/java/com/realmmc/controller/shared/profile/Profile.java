package com.realmmc.controller.shared.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private String lastClientVersion;
    private String lastClientType;
    @Builder.Default
    private int cash = 0;
    private Integer cashTopPosition;
    private Long cashTopPositionEnteredAt;
    @Builder.Default
    private boolean premiumAccount = false;
    @Builder.Default
    private List<Integer> roleIds = new ArrayList<>();
    @Builder.Default
    private Map<String, Long> roleExpirations = new HashMap<>();
    @Builder.Default
    private Map<String, Long> pausedRoleDurations = new HashMap<>();
    @Builder.Default
    private List<String> extraPermissions = new ArrayList<>();
    private long createdAt;
    private long updatedAt;
}