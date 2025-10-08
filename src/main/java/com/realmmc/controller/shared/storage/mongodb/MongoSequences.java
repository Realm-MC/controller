package com.realmmc.controller.shared.storage.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;

public final class MongoSequences {
    private MongoSequences() {
    }

    public static int getNext(String key) {
        MongoCollection<Document> counters = MongoManager.db().getCollection("counters");
        Document filter = new Document("_id", key);
        Document update = new Document("$inc", new Document("seq", 1));
        FindOneAndUpdateOptions opts = new FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(ReturnDocument.AFTER);
        Document res = counters.findOneAndUpdate(filter, update, opts);
        if (res == null) {
            counters.insertOne(new Document("_id", key).append("seq", 1));
            res = counters.findOneAndUpdate(filter, update, opts);
        }
        return res.getInteger("seq", 1);
    }
}
