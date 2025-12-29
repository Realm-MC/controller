package com.palacesky.controller.modules.motd.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotdData {
    @BsonId
    private String id;
    private String line1;
    private String line2;
    private boolean custom;
    private long updatedAt;
}