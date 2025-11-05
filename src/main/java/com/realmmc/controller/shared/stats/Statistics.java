package com.realmmc.controller.shared.stats;

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
public class Statistics {

    @BsonId
    private Integer id;

    private UUID uuid;

    private String name;

    @Builder.Default
    private long onlineTime = 0L;

}