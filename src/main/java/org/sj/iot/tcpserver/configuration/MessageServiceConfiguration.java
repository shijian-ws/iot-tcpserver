package org.sj.iot.tcpserver.configuration;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消息服务配置
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-10-16
 */
@Configuration
public class MessageServiceConfiguration {
    @Bean
    public CuratorFramework curatorFramework(ZooKeeperProperties props) {
        String host = props.getHost();
        Integer port = props.getPort();
        Integer timeout = props.getTimeout();
        Integer retry = props.getRetry();
        return CuratorFrameworkFactory.builder()
                .connectString(String.format("%s:%s", host, port))
                .connectionTimeoutMs(timeout) // 连接超时时间
                .sessionTimeoutMs(timeout) // 会话超时时间
                .defaultData(new byte[0]) // 节点默认数据
                .retryPolicy(new ExponentialBackoffRetry(retry, Integer.MAX_VALUE)) // 重连策略
                .build();
    }
}
