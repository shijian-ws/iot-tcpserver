package org.sj.iot.tcpserver.connection.impl;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.sj.iot.tcpserver.configuration.ConnectionProperties;
import org.sj.iot.tcpserver.connection.ITcpServer;
import org.sj.iot.tcpserver.connection.handler.*;
import org.sj.iot.tcpserver.connection.util.ChannelUtil;
import org.sj.iot.tcpserver.service.IMessageService;
import org.sj.iot.util.Constants;
import org.sj.iot.util.SslUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

/**
 * TCP服务端实现类
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-09-12
 */
@Service
public class TcpServerImpl extends AbstractImpl implements ITcpServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpServerImpl.class);

    private SslContext sslContext; // SSL上下文

    @Autowired
    private ServerBootstrap server;

    @Autowired
    private KeepliveHandler keepliveHandler;
    @Autowired
    private MessageDecoder messageDecoder;
    @Autowired
    private ProcessHandler processHandler;
    @Autowired
    private RequestHandler requestHandler;
    @Autowired
    private MessageEncoder messageEncoder;
    @Autowired
    private IMessageService messageService;

    @Autowired
    private ConnectionProperties props;

    private long heartbeatTime = 30L; // 心跳包间隔时间

    private Channel serverChannle; // 服务端通道

    /**
     * 加载配置参数
     */
    private void initProperties() {
        String id = props.getId();
        if (id != null) {
            this.setId(id);
        }
        Integer port = props.getPort();
        if (port != null) {
            this.setPort(port);
        }
        Boolean ssl = props.getSsl();
        if (ssl != null) {
            this.setSsl(ssl);
        }
        Long heartbeatTime = props.getHeartbeatTime();
        if (heartbeatTime != null) {
            this.heartbeatTime = heartbeatTime;
        }
    }

    /**
     * 加载SSL
     */
    private void initSslContent() {
        if (super.isSsl() && sslContext == null) {
            // 开启SSL通信
            if (this.isSsl() && sslContext == null) {
                KeyManagerFactory keyFactory = SslUtil.createKeyManagerFactory("server.keystore", "server");
                TrustManagerFactory trustFactory = SslUtil.createTrustManagerFactory("server.truststore", "server");
                try {
                    sslContext = SslContextBuilder.forServer(keyFactory).trustManager(trustFactory).build();
                } catch (SSLException e) {
                    throw new RuntimeException(String.format("TCP服务端SSL证书管理加载是失败: %s", e.getMessage()), e);
                }
            }
        }
    }

    @PostConstruct
    public void init() {
        initProperties();
        initSslContent();
        this.server.handler(new LoggingHandler(LogLevel.DEBUG)) // 定义Netty输出日志的级别
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("IP: {} 连接, 当前连接设备数: {}", ch.remoteAddress(), ChannelUtil.getChannelSize() + 1);
                        }

                        // 通道关闭监听器
                        ch.closeFuture().addListener((feature) -> {
                            // 移除缓存通道
                            String mac = ChannelUtil.removeChannel(ch);
                            if (mac != null) {
                                if (LOGGER.isWarnEnabled()) {
                                    LOGGER.warn("网关{}设备掉线!", mac);
                                }
                                messageService.cancel(mac); // 关闭命令消息服务
                            }
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("IP: {}, 断开连接!", ch.remoteAddress());
                            }
                        });

                        // 管道中回调注册
                        ChannelPipeline pipeline = ch.pipeline();

                        // 注册消息出站解析器 end
                        pipeline.addLast(messageEncoder);

                        if (isSsl() && sslContext != null) {
                            // 使用SSL
                            SSLEngine engine = sslContext.newEngine(ch.alloc()); // 创建SSL引擎
                            engine.setEnabledProtocols(new String[]{"TLSv1.2"});
                            engine.setNeedClientAuth(true); // 开启双向认证
                            // 注册SSL引擎, start
                            pipeline.addLast(new SslHandler(engine));
                        }

                        pipeline
                                // 注册通道空闲时间监听，2
                                .addLast(new IdleStateHandler(0, 0, (int) heartbeatTime))
                                // 注册空闲时间监听的心跳检查，3
                                .addLast(keepliveHandler)
                                // 注册TCP粘包/拆包解码器，数据包长度在TCP包的第5,6个字节位置，4
                                .addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, Constants.V2.MAGIC_NUMBER_LEN, Constants.V2.PACKET_LEN, 0, 0))
                                // 注册远程设备消息接收解析器，5
                                .addLast(messageDecoder)
                                // 注册远程设备响应结果处理器，6
                                .addLast(processHandler)
                                // 注册远程设备发送信息处理器，7
                                .addLast(requestHandler);
                    }
                });
    }

    @Override
    public void start() {
        try {
            ChannelFuture future = server.bind(super.getPort()).sync(); // 同步创建监听端口
            if (future.isSuccess()) {
                serverChannle = future.channel(); // 保存服务端通道
                super.setStop(false);
                super.setStart(true);
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("服务端启动...[SSL={},PORT={}]", super.isSsl(), super.getPort());
                }
                serverChannle.closeFuture().sync(); // 获取回调任务对象并同步阻塞到服务器关闭
            }
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("TCP服务端启动失败: {}", e.getMessage());
            }
            throw new RuntimeException("TCP服务端启动失败!", e);
        } finally {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("TCP服务端被关闭!");
            }
            super.setStop(true);
            super.setStart(false);
        }
    }

    @Override
    public void stop() {
        try {
            serverChannle.close().sync();
        } catch (Exception e) {
            throw new RuntimeException(String.format("关闭TCP服务端失败: %s", e.getMessage()), e);
        }
    }

    @Override
    public void restart() {
        try {
            serverChannle.close().sync();
            serverChannle.eventLoop().execute(this::start);
        } catch (Exception e) {
            throw new RuntimeException(String.format("TCP服务端重启失败: %s", e.getMessage()), e);
        }
    }
}
