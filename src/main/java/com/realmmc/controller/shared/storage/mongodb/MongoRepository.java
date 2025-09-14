package com.realmmc.controller.shared.storage.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Optional;
import java.util.function.Consumer;

public interface MongoRepository<T> {
    MongoCollection<T> collection();

    default Optional<T> findOne(Bson filter) {
        return Optional.ofNullable(collection().find(filter).first());
    }

    default void insert(T entity) {
        collection().insertOne(entity);
    }

    default void replace(Bson filter, T entity) {
        collection().replaceOne(filter, entity, new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    default void update(Bson filter, Bson update) {
        collection().updateOne(filter, update);
    }

    default void delete(Bson filter) {
        collection().deleteOne(filter);
    }

    default void forEach(Bson filter, Consumer<T> consumer) {
        collection().find(filter).forEach(consumer);
    }

    static Bson idEquals(Object id) { return Filters.eq("_id", id); }
}
