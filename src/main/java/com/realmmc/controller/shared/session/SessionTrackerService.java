package com.realmmc.controller.shared.session;

import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Optional;

public class SessionTrackerService {

    private static final Logger LOGGER = Logger.getLogger(SessionTrackerService.class.getName());

    private static final String SESSION_PREFIX = "controller:session:";
    private static final String ONLINE_ALL_KEY = "controller:online:all";
    private static final String ONLINE_PROXY_PREFIX = "controller:online:proxy:";
    private static final String ONLINE_SERVER_PREFIX = "controller:online:server:";
    private static final String IP_TRACKING_PREFIX = "controller:ip_tracking:";

    private static final int SESSION_HASH_TTL_SECONDS = 300;
    private static final int ONLINE_SET_TTL_SECONDS = 60;

    private final ProfileService profileService;

    public SessionTrackerService() {
        this.profileService = ServiceRegistry.getInstance().getService(ProfileService.class)
                .orElseThrow(() -> new IllegalStateException("ProfileService not found for SessionTrackerService"));
    }

    private String getSessionKey(UUID uuid) { return SESSION_PREFIX + uuid.toString(); }
    private String getProxySetKey(String proxyId) { return ONLINE_PROXY_PREFIX + proxyId; }
    private String getServerSetKey(String serverName) { return ONLINE_SERVER_PREFIX + serverName; }
    private String getIpSetKey(String ipAddress) { return IP_TRACKING_PREFIX + ipAddress; }

    @FunctionalInterface interface JedisVoidOperation { void apply(Jedis jedis); }
    @FunctionalInterface interface JedisOperation<T> { T apply(Jedis jedis); }

    private void executeJedisVoid(JedisVoidOperation operation) {
        try (Jedis jedis = RedisManager.getResource()) {
            operation.apply(jedis);
        } catch (JedisConnectionException e) {
            LOGGER.log(Level.SEVERE, "[SessionTracker] Redis connection error", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[SessionTracker] Unexpected Redis error", e);
        }
    }

    private <T> Optional<T> executeJedis(JedisOperation<T> operation) {
        try (Jedis jedis = RedisManager.getResource()) {
            return Optional.ofNullable(operation.apply(jedis));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[SessionTracker] Error retrieving data from Redis", e);
            return Optional.empty();
        }
    }

    public void startSession(UUID uuid, String username, String proxyId, String initialServer, int protocol, int ping,
                             String ip, String clientVersion, String clientType, boolean isPremium, String currentMedal) {
        if (uuid == null || username == null || proxyId == null) return;

        String sessionKey = getSessionKey(uuid);
        String proxySetKey = getProxySetKey(proxyId);
        String serverSetKey = (initialServer != null && !initialServer.isEmpty()) ? getServerSetKey(initialServer) : null;
        long now = System.currentTimeMillis();

        executeJedisVoid(jedis -> {
            Map<String, String> sessionData = new HashMap<>();

            sessionData.put("username", username);
            sessionData.put("proxyId", proxyId);
            sessionData.put("state", "CONNECTING");
            sessionData.put("lastHeartbeat", String.valueOf(now));

            if (initialServer != null) sessionData.put("currentServer", initialServer);
            if (protocol != -1) sessionData.put("protocol", String.valueOf(protocol));
            sessionData.put("ping", String.valueOf(ping));
            if (ip != null) sessionData.put("ip", ip);
            if (clientVersion != null) sessionData.put("clientVersion", clientVersion);
            if (clientType != null) sessionData.put("clientType", clientType);
            sessionData.put("isPremium", String.valueOf(isPremium));
            sessionData.put("medal", currentMedal != null ? currentMedal : "none");

            Pipeline pipe = jedis.pipelined();

            pipe.hmset(sessionKey, sessionData);
            pipe.expire(sessionKey, SESSION_HASH_TTL_SECONDS);

            pipe.sadd(ONLINE_ALL_KEY, username);

            pipe.sadd(proxySetKey, username);
            pipe.expire(proxySetKey, ONLINE_SET_TTL_SECONDS);

            if (serverSetKey != null) {
                pipe.sadd(serverSetKey, username);
                pipe.expire(serverSetKey, ONLINE_SET_TTL_SECONDS);
            }

            if (ip != null && !ip.isEmpty()) {
                String ipSetKey = getIpSetKey(ip);
                pipe.sadd(ipSetKey, uuid.toString());
                pipe.expire(ipSetKey, SESSION_HASH_TTL_SECONDS);
            }

            pipe.sync();
            LOGGER.info("[SessionTracker] Session started for " + username + " (" + uuid + ") on " + proxyId);
        });
    }

    public void setSessionState(UUID uuid, String state) {
        if (uuid == null || state == null) return;
        String sessionKey = getSessionKey(uuid);
        long now = System.currentTimeMillis();

        executeJedisVoid(jedis -> {
            if (jedis.exists(sessionKey)) {
                Pipeline pipe = jedis.pipelined();
                pipe.hset(sessionKey, "state", state);
                pipe.hset(sessionKey, "lastHeartbeat", String.valueOf(now));
                pipe.expire(sessionKey, SESSION_HASH_TTL_SECONDS);
                pipe.sync();
                LOGGER.info("[SessionTracker] State updated to " + state + " for " + uuid);
            }
        });
    }

    public void endSession(UUID uuid, String usernameArg) {
        if (uuid == null) return;
        String sessionKey = getSessionKey(uuid);

        executeJedisVoid(jedis -> {
            String username = usernameArg;
            String proxyId = null;
            String currentServer = null;
            String ip = null;

            if (username == null || username.isEmpty()) {
                List<String> data = jedis.hmget(sessionKey, "username", "proxyId", "currentServer", "ip");
                username = data.get(0);
                proxyId = data.get(1);
                currentServer = data.get(2);
                ip = data.get(3);
            } else {
                List<String> data = jedis.hmget(sessionKey, "proxyId", "currentServer", "ip");
                proxyId = data.get(0);
                currentServer = data.get(1);
                ip = data.get(2);
            }

            Pipeline pipe = jedis.pipelined();

            pipe.del(sessionKey);

            if (username != null) {
                pipe.srem(ONLINE_ALL_KEY, username);
                if (proxyId != null) pipe.srem(getProxySetKey(proxyId), username);
                if (currentServer != null) pipe.srem(getServerSetKey(currentServer), username);
            }

            if (ip != null) {
                pipe.srem(getIpSetKey(ip), uuid.toString());
            }

            pipe.sync();
            LOGGER.info("[SessionTracker] Session ended for " + (username != null ? username : uuid));
        });
    }

    public void endSession(UUID uuid) {
        endSession(uuid, null);
    }

    public void updateHeartbeat(UUID uuid, String currentServer, int ping, int protocol) {
        if (uuid == null) return;
        String sessionKey = getSessionKey(uuid);
        long now = System.currentTimeMillis();

        executeJedisVoid(jedis -> {
            if (!jedis.exists(sessionKey)) return;

            List<String> existingData = jedis.hmget(sessionKey, "ip", "proxyId", "username", "currentServer");
            String ip = existingData.get(0);
            String proxyId = existingData.get(1);
            String username = existingData.get(2);
            String savedServer = existingData.get(3);

            Pipeline pipe = jedis.pipelined();
            Map<String, String> updates = new HashMap<>();
            updates.put("lastHeartbeat", String.valueOf(now));
            updates.put("ping", String.valueOf(ping));
            if (protocol != -1) updates.put("protocol", String.valueOf(protocol));

            if (currentServer != null) {
                updates.put("currentServer", currentServer);
            }

            pipe.hmset(sessionKey, updates);
            pipe.expire(sessionKey, SESSION_HASH_TTL_SECONDS);

            if (ip != null) pipe.expire(getIpSetKey(ip), SESSION_HASH_TTL_SECONDS);

            if (proxyId != null && username != null) {
                pipe.expire(getProxySetKey(proxyId), ONLINE_SET_TTL_SECONDS);
            }

            String serverToRenew = (currentServer != null) ? currentServer : savedServer;
            if (serverToRenew != null && username != null) {
                pipe.expire(getServerSetKey(serverToRenew), ONLINE_SET_TTL_SECONDS);
            }

            pipe.sync();
        });
    }

    public void updateServer(UUID uuid, String username, String oldServer, String newServer) {
        if (uuid == null || username == null) return;
        String sessionKey = getSessionKey(uuid);

        executeJedisVoid(jedis -> {
            Pipeline pipe = jedis.pipelined();

            if (newServer != null) {
                pipe.hset(sessionKey, "currentServer", newServer);
            } else {
                pipe.hdel(sessionKey, "currentServer");
            }

            if (oldServer != null) pipe.srem(getServerSetKey(oldServer), username);
            if (newServer != null) {
                pipe.sadd(getServerSetKey(newServer), username);
                pipe.expire(getServerSetKey(newServer), ONLINE_SET_TTL_SECONDS);
            }

            pipe.sync();
            LOGGER.fine("[SessionTracker] Server updated for " + username + ": " + oldServer + " -> " + newServer);
        });
    }

    public long getActiveSessionsCountByIp(String ip) {
        if (ip == null) return 0;
        return executeJedis(jedis -> jedis.scard(getIpSetKey(ip))).orElse(0L);
    }

    public Optional<String> getSessionField(UUID uuid, String field) {
        if (uuid == null) return Optional.empty();
        return executeJedis(jedis -> jedis.hget(getSessionKey(uuid), field));
    }

    public void setSessionField(UUID uuid, String field, String value) {
        if (uuid == null || value == null) return;
        executeJedisVoid(jedis -> {
            if (jedis.exists(getSessionKey(uuid))) {
                jedis.hset(getSessionKey(uuid), field, value);
            }
        });
    }

    public Set<String> getOnlineUsernames() {
        return executeJedis(jedis -> jedis.smembers(ONLINE_ALL_KEY)).orElse(Collections.emptySet());
    }
}