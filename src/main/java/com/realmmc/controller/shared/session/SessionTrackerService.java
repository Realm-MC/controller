package com.realmmc.controller.shared.session;

import com.realmmc.controller.shared.profile.Profile;
import com.realmmc.controller.shared.profile.ProfileService;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.storage.redis.RedisManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.Pipeline;

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
                .orElseThrow(() -> new IllegalStateException("ProfileService não encontrado para SessionTrackerService!"));
    }

    private String getSessionKey(UUID uuid) { return SESSION_PREFIX + uuid.toString(); }
    private String getProxySetKey(String proxyId) { return ONLINE_PROXY_PREFIX + proxyId; }
    private String getServerSetKey(String serverName) { return ONLINE_SERVER_PREFIX + serverName; }
    private String getIpSetKey(String ipAddress) { return IP_TRACKING_PREFIX + ipAddress; }

    private <T> Optional<T> executeJedis(JedisOperation<T> operation) {
        try (Jedis jedis = RedisManager.getResource()) {
            return Optional.ofNullable(operation.apply(jedis));
        } catch (JedisConnectionException e) {
            LOGGER.log(Level.SEVERE, "Erro de conexão Redis ao executar operação", e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro inesperado ao executar operação Jedis", e);
            return Optional.empty();
        }
    }

    private void executeJedisVoid(JedisVoidOperation operation) {
        try (Jedis jedis = RedisManager.getResource()) {
            operation.apply(jedis);
        } catch (JedisConnectionException e) {
            LOGGER.log(Level.SEVERE, "Erro de conexão Redis ao executar operação void", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro inesperado ao executar operação Jedis void", e);
        }
    }

    @FunctionalInterface
    private interface JedisOperation<T> {
        T apply(Jedis jedis);
    }

    @FunctionalInterface
    private interface JedisVoidOperation {
        void apply(Jedis jedis);
    }


    public void startSession(UUID uuid, String username, String proxyId, String initialServer, int protocol, int ping,
                             String ip, String clientVersion, String clientType, boolean isPremium) {
        if (uuid == null || username == null || proxyId == null) {
            LOGGER.warning("startSession chamado com parâmetros nulos.");
            return;
        }
        String sessionKey = getSessionKey(uuid);
        String proxySetKey = getProxySetKey(proxyId);
        String serverSetKey = (initialServer != null && !initialServer.isEmpty()) ? getServerSetKey(initialServer) : null;
        long now = System.currentTimeMillis();

        executeJedisVoid(jedis -> {
            Map<String, String> sessionData = new HashMap<>();
            sessionData.put("proxyId", proxyId);
            if (initialServer != null && !initialServer.isEmpty()) sessionData.put("currentServer", initialServer);
            sessionData.put("state", "CONNECTING");
            if(protocol != -1) sessionData.put("protocol", String.valueOf(protocol));
            sessionData.put("ping", String.valueOf(ping));
            sessionData.put("lastHeartbeat", String.valueOf(now));
            sessionData.put("username", username);

            if (ip != null) sessionData.put("ip", ip);
            if (clientVersion != null) sessionData.put("clientVersion", clientVersion);
            if (clientType != null) sessionData.put("clientType", clientType);
            sessionData.put("isPremium", String.valueOf(isPremium));

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
            LOGGER.log(Level.INFO, "Sessão iniciada no Redis para {0} (UUID: {1}) no proxy {2}", new Object[]{username, uuid, proxyId});
        });
    }

    public void updateServer(UUID uuid, String username, String oldServer, String newServer) {
        if (uuid == null || username == null) {
            LOGGER.warning("updateServer chamado com UUID ou Username nulo.");
            return;
        }
        String sessionKey = getSessionKey(uuid);
        String oldServerSetKey = (oldServer != null && !oldServer.isEmpty()) ? getServerSetKey(oldServer) : null;
        String newServerSetKey = (newServer != null && !newServer.isEmpty()) ? getServerSetKey(newServer) : null;
        long now = System.currentTimeMillis();

        executeJedisVoid(jedis -> {
            Pipeline pipe = jedis.pipelined();
            Map<String, String> updates = new HashMap<>();
            updates.put("lastHeartbeat", String.valueOf(now));
            if (newServer != null && !newServer.isEmpty()) {
                updates.put("currentServer", newServer);
            } else {
                pipe.hdel(sessionKey, "currentServer");
            }
            if (!updates.isEmpty()) pipe.hmset(sessionKey, updates);
            pipe.expire(sessionKey, SESSION_HASH_TTL_SECONDS);

            if (oldServerSetKey != null) {
                pipe.srem(oldServerSetKey, username);
            }
            if (newServerSetKey != null) {
                pipe.sadd(newServerSetKey, username);
                pipe.expire(newServerSetKey, ONLINE_SET_TTL_SECONDS);
            }
            pipe.sync();
            LOGGER.log(Level.FINE, "Servidor atualizado para {0} ({1}): {2} -> {3}", new Object[]{username, uuid, oldServer == null ? "N/A" : oldServer, newServer == null ? "N/A" : newServer});
        });
    }

    public void updateHeartbeat(UUID uuid, String currentServer, int ping, int protocol) {
        if (uuid == null) {
            LOGGER.warning("updateHeartbeat chamado com UUID nulo.");
            return;
        }
        String sessionKey = getSessionKey(uuid);
        long now = System.currentTimeMillis();

        executeJedisVoid(jedis -> {
            String existingServer = null;
            String username = null;
            String proxyId = null;
            String ip = null;

            if (jedis.exists(sessionKey)) {
                Map<String, String> sessionData = jedis.hgetAll(sessionKey);
                existingServer = sessionData.get("currentServer");
                username = sessionData.get("username");
                proxyId = sessionData.get("proxyId");
                ip = sessionData.get("ip");
            } else {
                LOGGER.log(Level.FINER, "Heartbeat ignorado para UUID {0}: sessão não existe mais no Redis.", uuid);
                return;
            }

            Map<String, String> updates = new HashMap<>();
            updates.put("lastHeartbeat", String.valueOf(now));
            updates.put("ping", String.valueOf(ping));
            if(protocol != -1) updates.put("protocol", String.valueOf(protocol));

            boolean removeCurrentServer = false;
            if (currentServer != null && !currentServer.equals(existingServer)) {
                updates.put("currentServer", currentServer);
            } else if (currentServer == null && existingServer != null) {
                removeCurrentServer = true;
            }

            Pipeline pipe = jedis.pipelined();
            if (!updates.isEmpty()) pipe.hmset(sessionKey, updates);
            if (removeCurrentServer) pipe.hdel(sessionKey, "currentServer");
            pipe.expire(sessionKey, SESSION_HASH_TTL_SECONDS);

            if (ip != null && !ip.isEmpty()) {
                pipe.expire(getIpSetKey(ip), SESSION_HASH_TTL_SECONDS);
            }

            if (username != null && !username.isEmpty()) {
                pipe.sadd(ONLINE_ALL_KEY, username);
                if (proxyId != null && !proxyId.isEmpty()) {
                    String proxySetKey = getProxySetKey(proxyId);
                    pipe.sadd(proxySetKey, username);
                    pipe.expire(proxySetKey, ONLINE_SET_TTL_SECONDS);
                }
                String serverToUse = updates.getOrDefault("currentServer", existingServer);
                if (serverToUse != null && !serverToUse.isEmpty()) {
                    String serverSetKey = getServerSetKey(serverToUse);
                    pipe.sadd(serverSetKey, username);
                    pipe.expire(serverSetKey, ONLINE_SET_TTL_SECONDS);
                }
            }
            pipe.sync();
            LOGGER.log(Level.FINEST, "Heartbeat atualizado para UUID: {0}", uuid);
        });
    }

    public void setSessionState(UUID uuid, String state) {
        if (uuid == null || state == null || (!state.equals("CONNECTING") && !state.equals("ONLINE"))) {
            LOGGER.warning("setSessionState chamado com UUID ou estado inválido: " + state);
            return;
        }
        String sessionKey = getSessionKey(uuid);
        long now = System.currentTimeMillis();

        executeJedisVoid(jedis -> {
            if (jedis.exists(sessionKey)) {
                Pipeline pipe = jedis.pipelined();
                pipe.hset(sessionKey, "state", state);
                pipe.hset(sessionKey, "lastHeartbeat", String.valueOf(now));
                pipe.expire(sessionKey, SESSION_HASH_TTL_SECONDS);
                pipe.sync();
                LOGGER.log(Level.FINE, "Estado da sessão definido para {0} para UUID: {1}", new Object[]{state, uuid});
            } else {
                LOGGER.log(Level.FINER, "setSessionState ignorado para UUID {0}: sessão não existe no Redis.", uuid);
            }
        });
    }

    public void endSession(UUID uuid, String username) {
        if (uuid == null) {
            LOGGER.warning("endSession chamado com UUID nulo.");
            return;
        }
        String sessionKey = getSessionKey(uuid);

        executeJedisVoid(jedis -> {
            Map<String, String> sessionData = jedis.hgetAll(sessionKey);
            String proxyId = sessionData.get("proxyId");
            String currentServer = sessionData.get("currentServer");
            String ip = sessionData.get("ip");

            String finalUsername = (username != null && !username.isEmpty()) ? username : sessionData.get("username");

            Pipeline pipe = jedis.pipelined();
            pipe.del(sessionKey);

            if (finalUsername != null && !finalUsername.isEmpty()) {
                pipe.srem(ONLINE_ALL_KEY, finalUsername);
                if (proxyId != null && !proxyId.isEmpty()) {
                    pipe.srem(getProxySetKey(proxyId), finalUsername);
                }
                if (currentServer != null && !currentServer.isEmpty()) {
                    pipe.srem(getServerSetKey(currentServer), finalUsername);
                }
                LOGGER.log(Level.INFO, "Sessão finalizada no Redis para {0} (UUID: {1})", new Object[]{finalUsername, uuid});
            } else {
                LOGGER.log(Level.INFO, "Sessão finalizada no Redis para UUID: {0} (username não encontrado)", uuid);
            }

            if (ip != null && !ip.isEmpty()) {
                pipe.srem(getIpSetKey(ip), uuid.toString());
            }

            pipe.sync();
        });
    }

    public void endSession(UUID uuid) {
        if (uuid == null) {
            LOGGER.warning("endSession (1 arg) chamado com UUID nulo.");
            return;
        }
        String sessionKey = getSessionKey(uuid);

        String username = null;
        try (Jedis jedis = RedisManager.getResource()) {
            username = jedis.hget(sessionKey, "username");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao buscar username em endSession(1 arg) para UUID: " + uuid, e);
        }

        endSession(uuid, username);
    }

    public long getActiveSessionsCountByIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return 0;
        }

        String ipSetKey = getIpSetKey(ipAddress);
        return executeJedis(jedis -> jedis.scard(ipSetKey)).orElse(0L);
    }


    public Optional<String> getSessionField(UUID uuid, String fieldName) {
        if (uuid == null || fieldName == null || fieldName.isEmpty()) {
            return Optional.empty();
        }
        String sessionKey = getSessionKey(uuid);
        return executeJedis(jedis -> jedis.hget(sessionKey, fieldName));
    }

    public void setSessionField(UUID uuid, String fieldName, String value) {
        if (uuid == null || fieldName == null || fieldName.isEmpty() || value == null) {
            LOGGER.warning("setSessionField chamado com parâmetros inválidos.");
            return;
        }
        String sessionKey = getSessionKey(uuid);
        long now = System.currentTimeMillis();

        executeJedisVoid(jedis -> {
            if(jedis.exists(sessionKey)){
                Pipeline pipe = jedis.pipelined();
                pipe.hset(sessionKey, fieldName, value);
                pipe.hset(sessionKey, "lastHeartbeat", String.valueOf(now));
                pipe.expire(sessionKey, SESSION_HASH_TTL_SECONDS);
                pipe.sync();
                LOGGER.log(Level.FINEST, "Campo de sessão '{0}' definido para '{1}' para UUID: {2}", new Object[]{fieldName, value, uuid});
            } else {
                LOGGER.log(Level.FINER, "setSessionField ignorado para UUID {0}: sessão não existe no Redis.", uuid);
            }
        });
    }

    public long getTotalOnlineCount() {
        return executeJedis(jedis -> jedis.scard(ONLINE_ALL_KEY)).orElse(0L);
    }

    public Set<String> getOnlineUsernames() {
        return executeJedis(jedis -> jedis.smembers(ONLINE_ALL_KEY)).orElse(Collections.emptySet());
    }

    public Set<String> getOnlineUsernamesOnProxy(String proxyId) {
        if (proxyId == null || proxyId.isEmpty()) return Collections.emptySet();
        String proxySetKey = getProxySetKey(proxyId);
        return executeJedis(jedis -> jedis.smembers(proxySetKey)).orElse(Collections.emptySet());
    }

    public Set<String> getOnlineUsernamesOnServer(String serverName) {
        if (serverName == null || serverName.isEmpty()) return Collections.emptySet();
        String serverSetKey = getServerSetKey(serverName);
        return executeJedis(jedis -> jedis.smembers(serverSetKey)).orElse(Collections.emptySet());
    }

    public Map<String, String> getAllSessionData(UUID uuid) {
        if (uuid == null) return Collections.emptyMap();
        String sessionKey = getSessionKey(uuid);
        return executeJedis(jedis -> jedis.hgetAll(sessionKey)).orElse(Collections.emptyMap());
    }
}