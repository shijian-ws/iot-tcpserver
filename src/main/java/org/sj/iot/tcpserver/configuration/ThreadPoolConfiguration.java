package org.sj.iot.tcpserver.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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
    public Executor executor() {
        return new ThreadPoolTaskExecutor();
    }
}
