package com.realmmc.controller.shared.storage.mongodb;

public class MongoConfig {
    private final String connectionString;
    private final String database;

    public MongoConfig(String connectionString, String database) {
        this.connectionString = connectionString;
        this.database = database;
    }

    public String getConnectionString() { return connectionString; }
    public String getDatabase() { return database; }
}
