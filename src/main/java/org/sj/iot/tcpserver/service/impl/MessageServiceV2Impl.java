package org.sj.iot.tcpserver.service.impl;

import io.netty.channel.Channel;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.framework.api.transaction.CuratorTransactionResult;
import org.apache.curator.framework.api.transaction.TransactionCreateBuilder;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent.Type;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.sj.iot.model.Cmd;
import org.sj.iot.model.Message;
import org.sj.iot.tcpserver.connection.util.ChannelUtil;
import org.sj.iot.tcpserver.service.IMessageService;
import org.sj.iot.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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
    private CuratorFramework client;

    @Autowired
    private Executor executor;

    @PostConstruct
    public void init() {
        // 注册重连监听器
        client.getConnectionStateListenable().addListener((client, state) -> {
            if (state.isConnected()) {
                // 连接成功 初始化操作
                if (ConnectionState.CONNECTED == state) {
                    registerRoot();
                }
                addUnicastMQListener();
                addBroadcastMQListener();
                if (ConnectionState.RECONNECTED == state) {
                    // 重连, 重新注册通道
                    ChannelUtil.getAllMac(this::register);
                }
            }
        }, executor);
        // 启动客户端连接
        executor.execute(client::start);
    }

    private static boolean existsNode(CuratorFramework client, String path) {
        try {
            return client.checkExists().forPath(path) != null;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * 添加路径, 如果父路径不存在则追加
     */
    private void addNode(String path, List<String> paths) {
        if (!paths.contains(path) && !existsNode(client, path)) {
            // 路径集合中不存在路径并且zookeeper中不存在
            int pos = path.lastIndexOf('/');
            if (pos > 0) {
                // 添加父路径
                addNode(path.substring(0, pos), paths);
            }
            paths.add(path);
        }
    }

    private void registerRoot() {
        List<String> paths = new ArrayList<>();
        addNode(Constants.TCP_SERVER_PROVIDER_PATH, paths);
        addNode(Constants.DEVICE_GATEWAY_PATH, paths);
        addNode(Constants.GATEWAY_FIRMWARE_PATH, paths);
        addNode(Constants.TCP_SERVER_UNICAST_MQ_PATH, paths);
        addNode(Constants.TCP_SERVER_BROADCAST_MQ_PATH, paths);
        addNode(Constants.CLOUD_SERVER_BROADCAST_MQ_PATH, paths);
        if (paths.isEmpty()) {
            return;
        }
        Collection<CuratorTransactionResult> results = null;
        try {
            TransactionCreateBuilder builder = client.inTransaction().create();
            for (int i = 0, y = paths.size(); i < y; i++) {
                String path = paths.get(i);
                CuratorTransactionFinal tx = builder.withMode(CreateMode.PERSISTENT).forPath(path).and();
                if (y - i > 1) {
                    builder = tx.create();
                    continue;
                }
                results = tx.commit();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        if (results != null) {
            for (CuratorTransactionResult result : results) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("{}->{}", result.getForPath(), result.getType());
                }
            }
        }
    }

    private static final Map<String, TreeCache> cacheListener = new ConcurrentHashMap<>();

    /**
     * 关闭监听器
     */
    private void cancelListener() {
        if (cacheListener.isEmpty()) {
            return;
        }
        ArrayList<TreeCache> list = new ArrayList<>(cacheListener.values());
        cacheListener.clear();
        list.parallelStream().forEach(TreeCache::close);
    }

    /**
     * 注册监听器
     *
     * @param nodePath 监听路径
     * @param func     监听回调
     * @param types    监听状态类型,如果为空则监听全部类型否则监听指定类型
     */
    private void addListener(String nodePath, Consumer<TreeCacheEvent> func, Type... types) {
        if (!existsNode(client, nodePath) || func == null) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("创建监听器失败: 节点不存在或未找到回调函数!");
            }
            return;
        }
        TreeCache listener = TreeCache.newBuilder(client, nodePath).build(); // 创建监听器对象
        listener.getListenable().addListener((client, event) -> {
            // 添加监听事件
            Type current = event.getType(); // 当前触发事件
            if (Type.NODE_ADDED == current || Type.NODE_REMOVED == current || Type.NODE_UPDATED == current) {
                // 只监听增、删、改三种事件
                ChildData node = event.getData();
                String path = node.getPath();
                if (Type.NODE_ADDED == current && nodePath.equals(path)) {
                    // 跳过监听节点本身创建事件
                    return;
                }
                boolean execFlag = true; // 是否执行标识
                if (types != null && types.length > 0) {
                    execFlag = false; // 存在指定监听事件, 将执行标识设置为false
                    for (Type type : types) {
                        if (current == type) {
                            execFlag = true;
                            break;
                        }
                    }
                }
                if (execFlag) {
                    func.accept(event);
                }
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{}: 当前事件{},期望事件{},事件被跳过!", nodePath, current, Arrays.toString(types));
            }
        }, executor);
        try {
            listener.start();
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        TreeCache old = cacheListener.put(nodePath, listener); // 缓存
        if (old != null) {
            // 存在旧监听器
            old.close();
            try {
                old.close();
            } catch (Exception e) {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("关闭旧监听器失败: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 发送消息
     */
    private static void sendCmd(CuratorFramework client, String mac, Channel channel, Cmd cmd) {
        // 设置回调函数
        cmd.setCallback(() -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("命令回调: {}", cmd);
            }
            long currentTimeMillis = Tools.getCurrentTimeMillis();
            if (Constants.BROADCAST_ID.equals(cmd.getDevice())) {
                // 广播消息
                cmd.getAcks().put(mac, currentTimeMillis);
            } else {
                cmd.setAckTime(currentTimeMillis);
            }
            byte[] data = JsonUtil.toJsonByte(cmd);
            String path = null;
            if (Constants.BROADCAST_ID.equals(mac)) {
                path = Constants.TCP_SERVER_BROADCAST_MQ_PATH;
            } else {
                path = Constants.TCP_SERVER_UNICAST_MQ_PATH;
            }
            String nodePath = String.format("%s/%s", path, cmd.getUuid());
            updateNode(client, nodePath, data);
        });
        byte messageId = MessageV2Util.createMessageId(mac); // 生成消息ID
        ProcessUtil.set(mac, messageId, cmd::callback); // 设置网关响应处理函数
        Message message = MessageV2Util.createMessage(mac, messageId, cmd.getDataBodyString()); // 生成消息报文对象
        ChannelUtil.writeMessage(channel, message); // 发送消息报文
    }

    /**
     * 添加命令消息监听器
     */
    private void addMQListener(String nodePath) {
        addListener(nodePath, event -> {
            ChildData node = event.getData();
            byte[] data = node.getData();
            Cmd cmd = JsonUtil.toObject(data, Cmd.class);
            if (cmd == null) {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("消息命令解析错误: {}", new String(data));
                }
                return;
            }
            if (cmd.isExpired()) {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("消息命令已过期: {}", cmd);
                }
                return;
            }
            String device = cmd.getDevice();
            if (Constants.BROADCAST_ID.equals(device)) {
                // 广播命令
                Set<String> keySet = cmd.getAcks().keySet();// 已响应MAC
                ChannelUtil.getAllChannel((mac, channel) -> {
                    if (!keySet.contains(mac)) {
                        sendCmd(client, mac, channel, cmd);
                    }
                });
                return;
            }
            // 单一命令
            if (cmd.getAckTime() != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("消息命令已被应答: {}", cmd);
                }
                return;
            }
            if (ChannelUtil.getChannel(device) == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("当前TCP服务端不存在该设备{}通道: {}", device, cmd);
                }
                return;
            }
            sendCmd(client, device, ChannelUtil.getChannel(device), cmd);
        }, Type.NODE_ADDED);
    }

    /**
     * 添加单一命令消息监听器
     */
    private void addUnicastMQListener() {
        addMQListener(Constants.TCP_SERVER_UNICAST_MQ_PATH);
    }

    /**
     * 添加广播命令消息监听器
     */
    private void addBroadcastMQListener() {
        addMQListener(Constants.TCP_SERVER_BROADCAST_MQ_PATH);
    }

    private static final byte[] DEFAULT_DATA = new byte[0];

    private static byte[] getOrDefault(byte[] data) {
        if (data == null) {
            return DEFAULT_DATA;
        }
        return data;
    }

    private void createNode(String nodePath, byte[] data, CreateMode mode) {
        Stat stat = null;
        try {
            stat = client.checkExists().forPath(nodePath);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        if (stat == null) {
            try {
                client.create().withMode(mode).forPath(nodePath, getOrDefault(data));
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            return;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[{}]已存在,将被移除重新创建!", nodePath);
        }
        try {
            Collection<CuratorTransactionResult> results = client.inTransaction().delete().forPath(nodePath).and().create().withMode(mode).forPath(nodePath, data).and().commit();
            for (CuratorTransactionResult result : results) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("{} -> {}", result.getForPath(), result.getType());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static void updateNode(CuratorFramework client, String nodePath, byte[] data) {
        Stat stat = null;
        try {
            stat = client.checkExists().forPath(nodePath);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        if (stat == null) {
            throw new IllegalArgumentException("更新节点数据失败: 节点不存在!");
        }
        try {
            client.setData().forPath(nodePath, getOrDefault(data));
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void removeNode(String nodePath) {
        try {
            client.delete().forPath(nodePath);
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(e.getMessage());
            }
        }
    }

    @Override
    public void register(String id) {
        this.register(id, null);
    }

    @Override
    public void register(String id, String description) {
        String nodePath = String.format("%s/%s", Constants.DEVICE_GATEWAY_PATH, id);
        try {
            this.createNode(nodePath, description == null ? null : description.getBytes(Constants.UTF8_CHARSET), CreateMode.EPHEMERAL);
        } catch (Exception e) {
            LOGGER.error("网关设备节点注册失败: {}", e.getMessage());
        }
    }

    @Override
    public void cancel(String id) {
        String nodePath = String.format("%s/%s", Constants.DEVICE_GATEWAY_PATH, id);
        try {
            this.removeNode(nodePath);
        } catch (Exception e) {
            LOGGER.error("网关设备节点移除失败: {}", e.getMessage());
        }
    }

    @Override
    public void cancel() {

    }
}
