package com.smarthome.online.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;

/**
 * Redis工具类：专门连接hadoop103的Redis
 */
public class RedisUtil {
    // 核心配置：hadoop103的Redis地址和密码
    private static final String REDIS_HOST = "hadoop103";
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "123456"; // 替换为你的Redis密码
    private static final int REDIS_TIMEOUT = 2000;

    private static final JedisPool jedisPool;

    // 静态初始化Jedis连接池
    static {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(100);
        poolConfig.setMaxIdle(20);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true); // 借连接时测试是否可用

        // 连接hadoop103的Redis
        jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT, REDIS_TIMEOUT, REDIS_PASSWORD);
    }

    /**
     * 获取Jedis实例（自动归还连接）
     */
    public static Jedis getJedis() {
        return jedisPool.getResource();
    }

    /**
     * 有序集合：添加元素
     */
    public static void zadd(String key, double score, String value) {
        try (Jedis jedis = getJedis()) {
            jedis.zadd(key, score, value);
        } catch (Exception e) {
            throw new RuntimeException("Redis zadd失败：key=" + key, e);
        }
    }

    /**
     * 有序集合：降序查询元素（start到end）
     */
    public static List<String> zrevrange(String key, long start, long end) {
        try (Jedis jedis = getJedis()) {
            return jedis.zrevrange(key, start, end);
        } catch (Exception e) {
            throw new RuntimeException("Redis zrevrange失败：key=" + key, e);
        }
    }

    /**
     * 关闭连接池（程序退出时调用）
     */
    public static void closePool() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}