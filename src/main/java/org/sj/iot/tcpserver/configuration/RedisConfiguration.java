package org.sj.iot.tcpserver.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Redis配置
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2018-01-01
 */
@Configuration
public class RedisConfiguration {
    @Bean
    public JedisPoolConfig jedisPoolConfig() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(100); // 最大连接数
        config.setMaxIdle(10); // 最大空闲连接数
        config.setMinEvictableIdleTimeMillis(1800000); // 连接最小空闲时间
        config.setNumTestsPerEvictionRun(10); // 每次释放连接的数量
        config.setTimeBetweenEvictionRunsMillis(30000); // 释放连接的扫描间隔
        config.setSoftMinEvictableIdleTimeMillis(10000); // 连接空闲多久后释放, 当空闲时间>该值 且 空闲连接>最大空闲连接数 时直接释放
        config.setMaxWaitMillis(1500); // 获取连接时的最大等待毫秒数,小于零:阻塞不确定的时间,默认-1
        config.setTestOnBorrow(true); // 在获取连接的时候检查有效性, 默认false
        config.setTestWhileIdle(true); // 在空闲时检查有效性, 默认false
        config.setBlockWhenExhausted(false); // 连接耗尽时是否阻塞, false报异常,true阻塞直到超时, 默认true
        return config;
    }

    @Bean
    public JedisPool jedisPool(JedisPoolConfig config, RedisProperties properties) {
        String host = properties.getHost();
        if (host == null) {
            host = "127.0.0.1";
        }
        Integer port = properties.getPort();
        if (port == null) {
            port = 6379;
        }
        return new JedisPool(config, host, port);
    }
}
