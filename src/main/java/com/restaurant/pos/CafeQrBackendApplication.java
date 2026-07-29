package com.restaurant.pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.retry.annotation.EnableRetry;

@EnableAsync
@EnableRetry(order = 99)
@EnableScheduling
@EntityScan("com.restaurant.pos")
@EnableJpaRepositories("com.restaurant.pos")
@SpringBootApplication(scanBasePackages = "com.restaurant.pos", exclude = {
    org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
})
public class CafeQrBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CafeQrBackendApplication.class, args);
    }

}
