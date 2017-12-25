package org.sj.iot.tcpserver.service;

/**
 * 消息业务接口
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-10-12
 */
public interface IMessageService {
    /**
     * 注册消息接收
     */
    void register(String id);

    /**
     * 注册消息接收
     */
    void register(String id, String description);

    /**
     * 取消消息接收
     */
    void cancel(String id);

    /**
     * 取消所有消息接收
     */
    void cancel();
}
