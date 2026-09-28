package com.sakhtyar.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class LocalStartupReporter {

    private static final Logger log = LoggerFactory.getLogger(LocalStartupReporter.class);

    private final Environment environment;

    public LocalStartupReporter(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!environment.matchesProfiles("local")) {
            return;
        }

        String port = environment.getProperty("server.port", "8080");

        log.info("============================================================");
        log.info("SakhtYar is READY");
        log.info("Backend: http://localhost:{}", port);
        log.info("UI: http://localhost:5173");
        log.info("MinIO API: http://localhost:9000");
        log.info("MinIO Console: http://localhost:9001");
        log.info("============================================================");
    }
}