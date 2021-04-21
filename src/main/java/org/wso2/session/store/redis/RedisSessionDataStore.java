/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.session.store.redis;

import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.wso2.carbon.identity.application.authentication.framework.store.SessionContextDO;
import org.wso2.carbon.identity.application.authentication.framework.store.SessionDataPersistTask;
import org.wso2.carbon.identity.application.authentication.framework.store.SessionDataStore;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants;
import org.wso2.carbon.identity.application.common.cache.CacheEntry;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RedisSessionDataStore extends SessionDataStore {

    public static final String REDIS_SESSION_DATA_STORE = "RedisSessionDataStore";
    private static final Log log = LogFactory.getLog(SessionDataStore.class);
    private static int maxSessionDataPoolSize;
    private static JedisPool pool;
    private static String PASSWORD = "Password";
    private static String HOST = "Host";
    private static String PORT = "Port";
    private static String password;
    private static String host;
    private static Integer port;
    private int maxTotal;
    private int maxIdle;
    private int minIdle;
    private int maxWaitMillis;
    private boolean enablePersist;
    private boolean tempDataCleanupEnabled;

    {
        try {
            maxSessionDataPoolSize = getIntegerPropertyFromIdentityUtil(RedisConstants.GET_POOL_SIZE,
                    RedisConstants.DEFAULT_MAX_SESSION_DATA_POOLSIZE);
            tempDataCleanupEnabled = getBooleanPropertyFromIdentityUtil(RedisConstants.GET_TEMP_DATA_CLEANUP_ENABLE,
                    RedisConstants.DEFAULT_TEMPDATA_CLEANUP_ENABLED);
            maxTotal = getIntegerPropertyFromIdentityUtil(RedisConstants.GET_REDIS_POOL_MAX_TOTAL,
                    RedisConstants.DEFAULT_MAX_TOTAL);
            maxIdle = getIntegerPropertyFromIdentityUtil(RedisConstants.GET_REDIS_POOL_MAX_IDLE,
                    RedisConstants.DEFAULT_MAX_IDLE);
            minIdle = getIntegerPropertyFromIdentityUtil(RedisConstants.GET_REDIS_POOL_MIN_IDLE,
                    RedisConstants.DEFAULT_MIN_IDLE);
            maxWaitMillis = getIntegerPropertyFromIdentityUtil(RedisConstants.GET_REDIS_POOL_MAX_WAIT,
                    RedisConstants.DEFAULT_MAX_WAIT_MILLIS);
            port = Integer.valueOf(getAuthConfig(PORT));
            host = getAuthConfig(HOST);
            password = getAuthConfig(PASSWORD);
        } catch (NumberFormatException e) {
            if (log.isDebugEnabled()) {
                log.debug("Exception ignored : ", e);
            }
            log.warn("One or more pool size configurations cause NumberFormatException. Default values would be used.");
        }

        if (maxSessionDataPoolSize > 0) {
            log.info("Thread pool size for session persistent consumer : " + maxSessionDataPoolSize);
            ExecutorService threadPool = Executors.newFixedThreadPool(maxSessionDataPoolSize);
            for (int i = 0; i < maxSessionDataPoolSize; i++) {
                threadPool.execute(new SessionDataPersistTask(super.getSessionContextQueue()));
            }
        }
    }

    public RedisSessionDataStore() {

        enablePersist = getBooleanPropertyFromIdentityUtil(FrameworkConstants.SessionDataStoreConstants.PERSIST_ENABLE,
                FrameworkConstants.SessionDataStoreConstants.DEFAULT_ENABLE_PERSIST);
        if (!enablePersist) {
            log.info("Session Data Persistence of Authentication framework is not enabled.");
        }
    }

    // Serializes an Object to byte array.
    private static byte[] serialize(Serializable obj) {

        return SerializationUtils.serialize(obj);
    }

    // Deserializes a byte array back to Object.
    private static Object deserialize(byte[] bytes) {

        return SerializationUtils.deserialize(bytes);
    }

    private JedisPool getJedisInstance() throws JedisConnectionException {

        try {
            if (pool == null) {
                synchronized (RedisSessionDataStore.class) {
                    if (pool == null) {
                        pool = new JedisPool(getPoolConfig(), host, port);
                    }
                }
            }
            return pool;
        } catch (JedisConnectionException e) {
            log.error(e);
        }
        return null;
    }

    private GenericObjectPoolConfig getPoolConfig() {

        GenericObjectPoolConfig jedisPoolConfig = new GenericObjectPoolConfig();
        jedisPoolConfig.setMaxTotal(maxTotal);
        jedisPoolConfig.setMaxIdle(maxIdle);
        jedisPoolConfig.setMinIdle(minIdle);
        jedisPoolConfig.setMaxWaitMillis(maxWaitMillis);

        return jedisPoolConfig;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public void removeTempAuthnContextData(String key, String type) {
        // Empty method.
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public void removeExpiredSessionData() {
        // Empty method.
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public void removeSessionData(String key, String type, long nanoTime) {

        Jedis jedis;
        if (!enablePersist) {
            return;
        }
        String redisKey = key + RedisConstants.DIVIDER + type;
        boolean tempStore = getSessionStoreType(type);
        if (tempStore) {
            redisKey = redisKey + RedisConstants.DIVIDER +
                    RedisConstants.TEMPSTORE;
        }

        try {
            jedis = getJedisInstance().getResource();
            if (!(password == null)) {
                jedis.auth(password);
            }
            if (jedis.exists(redisKey)) {
                jedis.del(redisKey);
            }
            String objectKey = redisKey + RedisConstants.DIVIDER +
                    RedisConstants.OBJECT;
            if (jedis.exists(objectKey)) {
                jedis.del(objectKey);
            }
            jedis.close();

        } catch (JedisException e) {
            log.error("Error while storing DELETE operation session data.", e);
        }
    }

    @Override
    /**
     * @deprecated This is now run as a part of the {@link #removeExpiredSessionData()} due to a possible deadlock as
     * mentioned in IDENTITY-5131.
     */
    public void removeExpiredOperationData() {
        // Empty method.
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public SessionContextDO getSessionContextData(String key, String type) {

        Jedis jedis;
        String redisKey = key + RedisConstants.DIVIDER + type;
        if (!enablePersist) {
            return null;
        }
        boolean tempStore = getSessionStoreType(type);
        if (tempStore) {
            redisKey = redisKey + RedisConstants.DIVIDER +
                    RedisConstants.TEMPSTORE;
        }
        try {
            jedis = getJedisInstance().getResource();
            if (!(password == null)) {
                jedis.auth(password);
            }
            if (jedis.exists(redisKey)) {
                long nanoTime = Long.parseLong(jedis.hget(redisKey, RedisConstants.NANO_TIME));
                String objectKey = redisKey + RedisConstants.DIVIDER +
                        RedisConstants.OBJECT;
                Object blobObject = deserialize(jedis.get(objectKey.getBytes()));
                jedis.close();
                return new SessionContextDO(key, type, blobObject, nanoTime);
            }
            return null;
        } catch (JedisException e) {
            log.error("Error while retrieving session data.", e);
        }
        return null;
    }

    @Override
    public String getStoreName() {

        return REDIS_SESSION_DATA_STORE;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public void persistSessionData(String key, String type, Object entry, long nanoTime, int tenantId) {

        long validityPeriodNano = 0L;
        String objectKey;
        Long expireTime;
        String redisKey;
        boolean tempStore = getSessionStoreType(type);
        redisKey = key + RedisConstants.DIVIDER + type;
        if (!enablePersist) {
            return;
        }
        if (entry instanceof CacheEntry) {
            validityPeriodNano = ((CacheEntry) entry).getValidityPeriod();
        }
        if (validityPeriodNano == 0L) {
            validityPeriodNano = getCleanupTimeout(type, tenantId);
        }
        if (tempStore) {
            redisKey = redisKey + RedisConstants.DIVIDER +
                    RedisConstants.TEMPSTORE;
        }
        try {
            Jedis jedis;
            jedis = getJedisInstance().getResource();
            if (!(password == null)) {
                jedis.auth(password);
            }
            addRedisHash(jedis, redisKey, RedisConstants.NANO_TIME, String.valueOf(nanoTime));
            addRedisHash(jedis, redisKey, RedisConstants.EXPIRY_TIME,
                    String.valueOf(nanoTime + validityPeriodNano));
            addRedisHash(jedis, redisKey, RedisConstants.TENANT_ID, String.valueOf(tenantId));

            objectKey = redisKey + RedisConstants.DIVIDER +
                    RedisConstants.OBJECT;
            byte[] serializedSessionObject = serialize((Serializable) entry);
            jedis.set(objectKey.getBytes(), serializedSessionObject);

            expireTime = TimeUnit.SECONDS.convert(validityPeriodNano, TimeUnit.NANOSECONDS);
            jedis.expire(redisKey, expireTime.intValue());
            jedis.expire(objectKey, expireTime.intValue());
            jedis.close();

        } catch (JedisException e) {
            log.error("Error while storing session data.", e);
        }
    }

    private boolean getSessionStoreType(String type) {

        if (tempDataCleanupEnabled && super.isTempCache(type)) {
            return true;
        }
        return false;
    }

    private void addRedisHash(Jedis jedis, String key, String field, String value) {

        if (!(password == null)) {
            jedis.auth(password);
        }
        jedis.hset(key, field, value);
    }

    private String getAuthConfig(String config) {

        String stringVal;
        stringVal = IdentityUtil.getProperty("JDBCPersistenceManager.SessionDataPersist.RedisAuthConfig." + config);
        if (StringUtils.isNotBlank(stringVal)) {
            return stringVal;
        }
        return stringVal;
    }
}
