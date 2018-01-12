package org.sj.iot.tcpserver.service.impl;

import org.sj.iot.model.Event;
import org.sj.iot.model.Event.Type;
import org.sj.iot.model.EventListener;
import org.sj.iot.tcpserver.service.IMessageService;
import org.sj.iot.util.Constants;
import org.sj.iot.util.JsonUtil;
import org.sj.iot.util.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import javax.annotation.PostConstruct;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

/**
 * 消息服务接口实现类
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-10-16
 */
@Service
public class MessageServiceV2Impl implements IMessageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageServiceV2Impl.class);

    @Autowired
    private JedisPool pool;

    @Autowired
    private ThreadPoolExecutor executor;

    private static final EventListener listener = new EventListener(); // 监听器

    @PostConstruct
    public void init() {
        // 注册监听器
        executor.execute(() -> {
            open(jedis -> {
                // 监听通道, 同步阻塞
                jedis.psubscribe(new JedisPubSub() {
                    @Override
                    public void onPMessage(String pattern, String channel, String message) {
                        listener.onData(channel, message);
                    }
                }, "*");
            });
        });
        // 添加消息监听器
        addMQListener();
    }

    private void open(Consumer<Jedis> func) {
        Objects.requireNonNull(func, "未找到处理函数!");
        try (Jedis resource = pool.getResource()) {
            func.accept(resource);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static void addMQListener() {
        Consumer<Event> callback = event -> {
            String path = event.getPath();
            String mqPath = null;
            if (path.startsWith(Constants.TCP_SERVER_UNICAST_MQ_PATH)) {
                mqPath = Constants.TCP_SERVER_UNICAST_MQ_PATH;
            } else if (path.startsWith(Constants.TCP_SERVER_BROADCAST_MQ_PATH)) {
                mqPath = Constants.TCP_SERVER_BROADCAST_MQ_PATH;
            }
            // TODO
        };
        addListener(callback, String.format("%s/*", Constants.TCP_SERVER_UNICAST_MQ_PATH), Type.ADDED); // 添加单播监听器
        addListener(callback, String.format("%s/*", Constants.TCP_SERVER_BROADCAST_MQ_PATH), Type.ADDED); // 添加广播监听器
    }

    /**
     * 添加监听
     *
     * @param callback 回调函数
     * @param nodePath 监听路径
     * @param types    监听事件
     */
    private static void addListener(Consumer<Event> callback, String nodePath, Type... types) {
        Objects.requireNonNull(callback, "监听回调函数不能为空!");
        listener.addListener(callback, nodePath, types);
    }

    @Override
    public void register(String id) {
        this.register(id, null);
    }

    @Override
    public void register(String id, String description) {
        open(jedis -> {
            jedis.hset(Constants.DEVICE_GATEWAY_PATH, id, String.valueOf(Tools.getCurrentTimeMillis())); // 保存网关到在线列表
            String key = String.format("%s/%s", Constants.DEVICE_GATEWAY_PATH, id);
            Long count = jedis.publish(key, JsonUtil.toJsonString(new Event(Type.ADDED, description)));// 给指定监听网关信息的频道推送信息
            if (count == 0) {
                LOGGER.warn("当前未有云平台监听网关设备{}上线信息!", id);
            }
        });
    }

    @Override
    public void cancel(String id) {
        open(jedis -> {
            jedis.hdel(Constants.DEVICE_GATEWAY_PATH, id); // 从在线列表移除
            String key = String.format("%s/%s", Constants.DEVICE_GATEWAY_PATH, id);
            jedis.publish(key, JsonUtil.toJsonString(new Event(Type.REMOVED, (byte[]) null))); // 给指定监听网关信息的频道推送关闭指令
        });
    }

    @Override
    public void cancel() {

    }
}
