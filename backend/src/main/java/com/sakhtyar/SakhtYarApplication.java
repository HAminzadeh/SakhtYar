package com.sakhtyar;

import com.sakhtyar.shared.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class SakhtYarApplication {

    public static void main(String[] args) {
        SpringApplication.run(SakhtYarApplication.class, args);
    }
}
