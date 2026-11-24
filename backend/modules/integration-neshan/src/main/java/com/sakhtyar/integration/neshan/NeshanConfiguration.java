package com.sakhtyar.integration.neshan;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Network connect/read deadlines are required for map provider failure isolation. */
@Configuration
@EnableConfigurationProperties(NeshanProperties.class)
public class NeshanConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.integrations.neshan", name = "enabled", havingValue = "true")
    RestClient neshanRestClient(NeshanProperties properties) {
        int seconds = properties.timeoutSeconds() <= 0 ? 10 : Math.min(properties.timeoutSeconds(), 30);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(seconds * 1000);
        factory.setReadTimeout(seconds * 1000);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
