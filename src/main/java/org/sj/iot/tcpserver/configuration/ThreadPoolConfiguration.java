package org.sj.iot.tcpserver.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 配置线程池
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2017-12-25
 */
@Configuration
public class ThreadPoolConfiguration {
    /**
     * 任务线程池
     */
    @Bean
    public ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(10, 100, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }
}
