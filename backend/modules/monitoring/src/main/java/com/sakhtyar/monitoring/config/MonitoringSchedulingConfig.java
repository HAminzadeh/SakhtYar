package com.sakhtyar.monitoring.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name="app.monitoring.scheduling-enabled",
    havingValue="true",
    matchIfMissing=false
)
public class MonitoringSchedulingConfig {}
