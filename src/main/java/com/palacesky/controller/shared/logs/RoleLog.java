package com.palacesky.controller.shared.logs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleLog {
    @BsonId
    private String id;
    private UUID targetUuid;
    private String targetName;
    private String source;
    private LogType action;
    private String roleName;
    private String duration;
    private long timestamp;
    private String context;
}