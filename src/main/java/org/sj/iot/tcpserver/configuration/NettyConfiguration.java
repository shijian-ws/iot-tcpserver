package org.sj.iot.tcpserver.configuration;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置Netty
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-09-06
 */
@Configuration
public class NettyConfiguration {
    /**
     * 主服务线程池
     */
    @Bean
    public EventLoopGroup boss() {
        return new NioEventLoopGroup();
    }

    /**
     * 任务线程池
     */
    @Bean
    public EventLoopGroup worker() {
        return new NioEventLoopGroup();
    }

    /**
     * 配置服务端
     */
    @Bean
    @ConditionalOnBean(name = {"boss", "worker"})
    public ServerBootstrap server(EventLoopGroup boss, EventLoopGroup worker) {
        return new ServerBootstrap()
                .group(boss, worker) // 设置主服务线程池与任务线程池
                .channel(NioServerSocketChannel.class) // 设置通道类型
                .option(ChannelOption.SO_KEEPALIVE, true) // 与客户端保持TCP连接
                .option(ChannelOption.SO_BACKLOG, 128) // 设置端口排队的最大连接数
                .option(ChannelOption.TCP_NODELAY, true) // 立即向客户端发送数据, 不用等一定数量才发送
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark.DEFAULT) // 设置流出数据的高低水位
                .handler(new LoggingHandler(LogLevel.DEBUG)); // 设置Netty输出的日志级别
    }
}
