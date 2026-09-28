package com.sakhtyar;

import com.sakhtyar.bootstrap.LocalInfrastructureInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SakhtYarApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SakhtYarApplication.class);
        application.addInitializers(new LocalInfrastructureInitializer());
        application.run(args);
    }
}
