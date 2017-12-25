package org.sj.iot.tcpserver.connection.handler;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.sj.iot.model.DataBody;
import org.sj.iot.model.Message;
import org.sj.iot.model.Processable;
import org.sj.iot.util.ProcessUtil;
import org.sj.iot.util.ThreadLocalUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 任务回调处理
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-09-18
 */
@Sharable
@Component
public class ProcessHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessHandler.class);

    @Autowired
    private Executor executor;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Message message = ThreadLocalUtil.get(Message.class); // MessageV2Util.read((ByteBuf) msg); // 从容器中读取消息对象
        String mac = message.getMacHex();
        byte messageId = message.getMessageId();
        Processable<DataBody> call = ProcessUtil.remove(mac, messageId);
        if (call != null) {
            // 远程设备响应服务端请求
            executor.execute(() -> call.process(message.getDataBody()));
            return;
        }
        // 非任务回调处理, 放行
        ctx.fireChannelRead(msg);
    }
}
