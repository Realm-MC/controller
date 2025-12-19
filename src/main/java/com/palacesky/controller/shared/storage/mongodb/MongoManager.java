package com.palacesky.controller.shared.storage.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.Objects;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public final class MongoManager {
    private static MongoClient client;
    private static MongoDatabase database;

    private MongoManager() {
    }

    public static synchronized void init(MongoConfig config) {
        if (client != null) return;
        Objects.requireNonNull(config, "MongoConfig cannot be null");

        PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromCodecs(),
                fromProviders(pojoCodecProvider)
        );

        MongoClientSettings settings = MongoClientSettings.builder()
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .codecRegistry(pojoCodecRegistry)
                .applyConnectionString(new com.mongodb.ConnectionString(config.connectionString()))
                .build();

        client = MongoClients.create(settings);
        database = client.getDatabase(config.database());
    }

    public static MongoDatabase db() {
        if (database == null) throw new IllegalStateException("MongoManager not initialized");
        return database;
    }

    public static synchronized void shutdown() {
        if (client != null) {
            client.close();
            client = null;
            database = null;
        }
    }
}
