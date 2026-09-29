package com.sakhtyar.bootstrap;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@ConditionalOnProperty(
        prefix = "app.dev-seed",
        name = "enabled",
        havingValue = "true"
)
public class DevSeedRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DevSeedRunner.class);

    private final DataSource dataSource;

    public DevSeedRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Development seed is enabled; applying db/dev/seed-demo.sql");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(false);
        populator.setSeparator(";");
        populator.addScript(new ClassPathResource("db/dev/seed-demo.sql"));
        populator.execute(dataSource);

        log.info("Development seed completed successfully.");
    }
}
