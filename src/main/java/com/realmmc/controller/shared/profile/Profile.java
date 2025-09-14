package com.realmmc.controller.shared.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile {
    @BsonId
    private UUID id;
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
    private long cash = 0L;
    private Integer cashTopPosition;
    private Long cashTopPositionEnteredAt;
    private long createdAt;
    private long updatedAt;
}
