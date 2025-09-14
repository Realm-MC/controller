package com.realmmc.controller.shared.storage.redis;

public class RedisConfig {
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final boolean ssl;

    public RedisConfig(String host, int port, String password, int database, boolean ssl) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.database = database;
        this.ssl = ssl;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPassword() { return password; }
    public int getDatabase() { return database; }
    public boolean isSsl() { return ssl; }
}
