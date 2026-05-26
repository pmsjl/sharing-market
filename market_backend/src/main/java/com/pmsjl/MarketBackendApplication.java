package com.pmsjl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = { SessionAutoConfiguration.class })
public class MarketBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketBackendApplication.class, args);
    }

}
