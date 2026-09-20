package com.sakhtyar.runtime;

import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class RuntimeRoleConfiguration {

    @Bean
    RuntimeRole runtimeRole(Environment environment) {
        var profiles = Arrays.asList(environment.getActiveProfiles());

        if (profiles.contains("worker")) {
            return RuntimeRole.WORKER;
        }
        if (profiles.contains("collector")) {
            return RuntimeRole.COLLECTOR;
        }
        if (profiles.contains("agent")) {
            return RuntimeRole.AGENT;
        }
        return RuntimeRole.API;
    }
}
