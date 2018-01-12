package org.sj.iot.tcpserver.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * redis参数
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2018-01-01
 */
@ConfigurationProperties(prefix = "redis")
public class RedisProperties {
    private String host; // 主机
    private Integer port; // 端口

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }
}
