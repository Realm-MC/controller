package com.palacesky.controller.shared.cosmetics;

import com.palacesky.controller.shared.cosmetics.medals.UnlockedMedal;
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
    private List<UnlockedMedal> unlockedMedals = new ArrayList<>();
}