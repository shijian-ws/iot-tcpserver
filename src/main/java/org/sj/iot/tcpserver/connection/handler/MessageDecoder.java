package org.sj.iot.tcpserver.connection.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.sj.iot.model.Message;
import org.sj.iot.model.MessageV2;
import org.sj.iot.tcpserver.connection.util.ChannelUtil;
import org.sj.iot.util.Constants;
import org.sj.iot.util.MessageV2Util;
import org.sj.iot.util.ThreadLocalUtil;
import org.sj.iot.util.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 消息解码器
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-09-12
 */
@Sharable
@Component
public class MessageDecoder extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDecoder.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel channel = ctx.channel();
        String addrInfo = channel.remoteAddress().toString();
        int magic = msg.readInt(); // 获取魔数头，4个字节
        if (!MessageV2Util.checkMagic(magic)) {
            // 非法魔数
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("非法魔数请求: {}", addrInfo);
            }
            return;
        }
        int packetLength = msg.readInt(); // 数据包从获取从version到checksum的长度, 4个字节
        byte version = msg.readByte(); // 获取版本号，1个字节
        if (Constants.V2.VERSION != version) {
            // 非支持当前版本
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("当前版本[{}], 不支持版本[{}]", Constants.V2.VERSION, version);
            }
            return;
        }
        // long mac = ((long) msg.readShort() << 32) | msg.readInt(); // 客户端MAC地址, 6个字节, 如果不先将short类型转换为long则位运算时将会出现超出位舍弃
        long mac = Tools.macToLong(ChannelUtil.readAsBuffer(msg, 6)); // 从缓冲读取6个字节数据然后转换为long值
        byte messageId = msg.readByte(); // 获取消息ID，1个字节
        int dataLength = MessageV2Util.getDataLength(packetLength); // 业务数据长度
        byte[] data = ChannelUtil.readAsBuffer(msg, dataLength); // 业务数据
        byte status = msg.readByte(); // 获取状态，1个字节
        byte checkSum = msg.readByte(); // 获取校验码，1个字节
        byte validate = MessageV2Util.getCheckSum(magic, packetLength, version, mac, messageId, data, status);
        if (validate != checkSum) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("数据校验失败:{}, {}", messageId, addrInfo);
            }
            return;
        }
        Message message = new MessageV2(magic, packetLength, version, mac, messageId, data, status, checkSum);
        ChannelUtil.cacheChannel(message.getMacHex(), channel); // 缓存设备与通道
        ThreadLocalUtil.set(Message.class, message); // 缓存TCP协议栈消息对象
        ByteBuf buf = ctx.alloc().buffer();
        try {
            ctx.fireChannelRead(buf.asReadOnly()); // 执行下一个管道链中的处理器
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(Tools.formatString("消息处理出错: {}", e.getMessage()), e);
            }
        } finally {
            ThreadLocalUtil.removeAll();
            buf.release(); // 释放消息容器空间
        }
    }
}
