package org.sj.iot.tcpserver.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 连接配置类
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-09-12
 */
@ConfigurationProperties(prefix = "zk")
public class ZooKeeperProperties {
    private String host; // 主机
    private Integer port; // 端口
    private Integer timeout; // 超时时间
    private Integer retry; // 重连间隔

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

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getRetry() {
        return retry;
    }

    public void setRetry(Integer retry) {
        this.retry = retry;
    }
}
