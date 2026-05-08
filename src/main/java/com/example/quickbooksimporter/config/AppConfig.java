package com.example.quickbooksimporter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient.Builder;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({AppSecurityProperties.class, QuickBooksProperties.class, AppPublicProperties.class})
public class AppConfig {

    @Bean
    Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient restClient(Builder builder) {
        return builder.build();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
