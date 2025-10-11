package com.realmmc.controller.shared.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @BsonId
    private Integer id;
    private String name;
    private String displayName;
    private String prefix;
    @Builder.Default
    private List<String> permissions = new ArrayList<>();
    @Builder.Default
    private List<String> inherits = new ArrayList<>();
    private int weight;
    private long createdAt;
    private long updatedAt;
}