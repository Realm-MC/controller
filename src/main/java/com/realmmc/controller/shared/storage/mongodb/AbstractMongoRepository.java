package com.realmmc.controller.shared.storage.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public abstract class AbstractMongoRepository<T> implements MongoRepository<T> {
    private final MongoCollection<T> collection;

    protected AbstractMongoRepository(Class<T> type, String collectionName) {
        MongoDatabase db = MongoManager.db();
        this.collection = db.getCollection(collectionName, type);
    }

    @Override
    public MongoCollection<T> collection() {
        return collection;
    }
}
