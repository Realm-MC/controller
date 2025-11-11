package com.realmmc.controller.shared.preferences;

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
public class Preferences {
    @BsonId
    private Integer id;
    private UUID uuid;
    private String name;

    @Builder.Default
    private Language serverLanguage = Language.getDefault();

    @Builder.Default
    private boolean staffChatEnabled = true;
}