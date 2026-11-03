package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 配置。
 * 连接地址与密码均来自配置文件/环境变量，不在代码中硬编码任何敏感信息。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.redis.port:6379}")
    private int port;

    @Value("${spring.redis.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        //配置
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        SingleServerConfig server = config.useSingleServer().setAddress(address);
        //密码为空表示 Redis 未开启鉴权，此时不要调用 setPassword
        if (StringUtils.hasText(password)) {
            server.setPassword(password);
        }
        //创建RedissonClient对象
        return Redisson.create(config);
    }

}
