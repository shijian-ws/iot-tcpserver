package org.sj.iot.tcpserver;

import org.sj.iot.tcpserver.configuration.ConnectionProperties;
import org.sj.iot.tcpserver.configuration.RedisProperties;
import org.sj.iot.tcpserver.connection.ITcpServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;

/**
 * 网关设备服务端启动类
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-09-12
 */
@SpringBootApplication
@EnableConfigurationProperties({ConnectionProperties.class, RedisProperties.class}) // 加载配置文件参数
public class Application {
    public static void main(String[] args) {
        ApplicationContext content = SpringApplication.run(Application.class, args);
        ITcpServer tcpClient = content.getBean(ITcpServer.class);
        tcpClient.start();
    }
}
