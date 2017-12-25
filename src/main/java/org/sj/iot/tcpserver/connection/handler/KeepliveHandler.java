package org.sj.iot.tcpserver.connection.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 空闲时间心跳检查
 * 
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-09-12
 */
@Sharable
@Component
public class KeepliveHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeepliveHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // 指定时间没有发生读或写则触发
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            IdleState state = event.state();
            if (IdleState.ALL_IDLE.equals(state) || IdleState.READER_IDLE.equals(state)) {
                // 一定时间未收到网关请求包
                if (IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT.equals(event) || IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT.equals(event)) {
                    // 第一次触发跳过
                    return;
                }
                Channel channel = ctx.channel();
                String addrInfo = channel.remoteAddress().toString();
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("{}: 超过2次未收到心跳包, 通道关闭!", addrInfo);
                }
                channel.close(); // 关闭通道
            }
        }
    }
}
