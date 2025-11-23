package com.realmmc.controller.shared.cash;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashLog {

    public enum Action {
        ADD, REMOVE, SET, CLEAR
    }

    @BsonId
    private ObjectId id;

    private UUID targetUuid;
    private int amount;
    private Action action;
    private UUID sourceUuid;
    private String sourceName;
    private long timestamp;
}