package com.sakhtyar.integration.neshan;

import java.io.IOException;
import java.net.HttpURLConnection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** No outbound call is made when this client bean is constructed. */
@Configuration
@EnableConfigurationProperties(NeshanProperties.class)
public class NeshanConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.integrations.neshan", name = "enabled", havingValue = "true")
    RestClient neshanRestClient(NeshanProperties properties) {
        // Enforce HTTPS + official service host before ever sending a credential.
        if (properties.outboundEnabled()) NeshanDestinationPolicy.requireOfficialHttps(properties);
        int seconds = properties.timeoutSeconds() <= 0 ? 10 : Math.min(properties.timeoutSeconds(), 30);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                // Never forward Api-Key to a redirect destination, even on GET.
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(seconds * 1000);
        factory.setReadTimeout(seconds * 1000);
        return RestClient.builder()
                .baseUrl(properties.baseUrl() == null ? "https://api.neshan.org" : properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
