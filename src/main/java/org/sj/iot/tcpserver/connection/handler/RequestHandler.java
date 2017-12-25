package org.sj.iot.tcpserver.connection.handler;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.sj.iot.model.Cmd;
import org.sj.iot.model.DataBody;
import org.sj.iot.model.DataBody.GatewayInfo;
import org.sj.iot.model.Message;
import org.sj.iot.tcpserver.connection.util.ChannelUtil;
import org.sj.iot.tcpserver.service.IMessageService;
import org.sj.iot.util.JsonUtil;
import org.sj.iot.util.MessageV2Util;
import org.sj.iot.util.ThreadLocalUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 远程设备主动请求信息处理器
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-09-12
 */
@Sharable
@Component
public class RequestHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);

    @Autowired
    private Executor executor;

    @Autowired
    private IMessageService messageService;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Message message = ThreadLocalUtil.get(Message.class); // MessageV2Util.read((ByteBuf) msg); // 从缓冲读取消息对象
        byte messageId = message.getMessageId();
        String mac = message.getMacHex();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("网关设备[{}]请求信息: messageId: {}", mac, messageId);
        }
        // 通用响应远程设备
        ChannelUtil.writeMessage(ctx, MessageV2Util.responseOK(message));
        // 异步远程设备请求业务
        executor.execute(() -> {
            DataBody body = message.getDataBody();
            String type = body.getType();
            if (Cmd.GATEWAY_INFO.equals(type)) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("网关设备[{}]连接!", mac);
                }
                GatewayInfo info = body.getGatewayInfo();
                messageService.register(mac, JsonUtil.toJsonString(info)); // 注册命令消息服务
                return;
            }
        });
    }
}
