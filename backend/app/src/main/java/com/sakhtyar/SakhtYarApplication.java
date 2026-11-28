package com.sakhtyar;

import com.sakhtyar.bootstrap.LocalInfrastructureInitializer;
import com.sakhtyar.bootstrap.DatabaseMigrationStartupInitializer;
import com.sakhtyar.bootstrap.DatabaseMigrationPreflightInitializer;
import com.sakhtyar.bootstrap.Phase09ProfileSafetyInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SakhtYarApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SakhtYarApplication.class);
        application.addInitializers(new Phase09ProfileSafetyInitializer());
        application.addInitializers(new LocalInfrastructureInitializer());
        application.addInitializers(new DatabaseMigrationStartupInitializer());
        application.addInitializers(new DatabaseMigrationPreflightInitializer());
        application.run(args);
    }
}
