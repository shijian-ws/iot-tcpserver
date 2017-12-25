package org.sj.iot.tcpserver.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 连接配置类
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-09-12
 */
@ConfigurationProperties(prefix = "connection")
public class ConnectionProperties {
    private String id; // 服务端标识
    private Integer port; // 端口
    private Boolean ssl = false; // 默认关闭SSL
    private Long heartbeatTime = 30L; // 心跳包间隔时间

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Boolean getSsl() {
        return ssl;
    }

    public void setSsl(Boolean ssl) {
        this.ssl = ssl;
    }

    public Long getHeartbeatTime() {
        return heartbeatTime;
    }

    public void setHeartbeatTime(Long heartbeatTime) {
        this.heartbeatTime = heartbeatTime;
    }
}
