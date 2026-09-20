package com.sakhtyar.integration.neshan;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(NeshanProperties.class)
public class NeshanConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "app.integrations.neshan",
            name = "enabled",
            havingValue = "true"
    )
    RestClient neshanRestClient(
            NeshanProperties properties
    ) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
