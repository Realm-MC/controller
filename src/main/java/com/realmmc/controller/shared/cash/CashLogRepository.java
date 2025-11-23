package com.realmmc.controller.shared.cash;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.realmmc.controller.shared.storage.mongodb.AbstractMongoRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CashLogRepository extends AbstractMongoRepository<CashLog> {

    public CashLogRepository() {
        super(CashLog.class, "cashlogs");
        ensureIndexes();
    }

    private void ensureIndexes() {
        MongoCollection<CashLog> col = collection();
        col.createIndex(Indexes.ascending("targetUuid"));
        col.createIndex(Indexes.descending("timestamp"));
    }

    public void log(CashLog log) {
        collection().insertOne(log);
    }

    public List<CashLog> findByUuid(UUID targetUuid, int limit) {
        return collection()
                .find(Filters.eq("targetUuid", targetUuid))
                .sort(Sorts.descending("timestamp"))
                .limit(Math.max(1, limit))
                .into(new ArrayList<>());
    }
}