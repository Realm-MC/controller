package com.realmmc.controller.shared.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Role {

    @BsonId
    private String name;

    private String displayName;

    private String prefix;

    private String suffix;

    private String color;

    private RoleType type;

    private int weight;

    @Builder.Default
    private List<String> permissions = new ArrayList<>();

    @Builder.Default
    private List<String> inheritance = new ArrayList<>();

    private long createdAt;

    private long updatedAt;

    @JsonIgnore
    @Builder.Default
    private Set<String> cachedEffectivePermissions = Collections.emptySet();
}