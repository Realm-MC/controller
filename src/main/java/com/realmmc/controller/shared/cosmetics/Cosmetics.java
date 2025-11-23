package com.realmmc.controller.shared.cosmetics;

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
public class Cosmetics {
    @BsonId
    private Integer id;
    private UUID uuid;
    private String name;

    @Builder.Default
    private List<String> unlockedMedals = new ArrayList<>();

    // Futuro: private List<String> unlockedParticles...
    // Futuro: private List<String> unlockedTags...
}