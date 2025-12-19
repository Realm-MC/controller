package com.palacesky.controller.shared.logs;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.palacesky.controller.shared.storage.mongodb.AbstractMongoRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LogRepository {

    public static class Role extends AbstractMongoRepository<RoleLog> {
        public Role() { super(RoleLog.class, "logsRoles"); }

        public void insert(RoleLog log) { collection().insertOne(log); }

        public List<RoleLog> findByTarget(UUID uuid, int limit) {
            return collection().find(Filters.eq("targetUuid", uuid))
                    .sort(Sorts.descending("timestamp"))
                    .limit(limit)
                    .into(new ArrayList<>());
        }

        public Optional<RoleLog> findById(String id) {
            return Optional.ofNullable(collection().find(Filters.eq("_id", id)).first());
        }
    }

    public static class Cash extends AbstractMongoRepository<CashLog> {
        public Cash() { super(CashLog.class, "logsCash"); }

        public void insert(CashLog log) { collection().insertOne(log); }

        public List<CashLog> findByTarget(UUID uuid, int limit) {
            return collection().find(Filters.eq("targetUuid", uuid))
                    .sort(Sorts.descending("timestamp"))
                    .limit(limit)
                    .into(new ArrayList<>());
        }

        public Optional<CashLog> findById(String id) {
            return Optional.ofNullable(collection().find(Filters.eq("_id", id)).first());
        }
    }
}