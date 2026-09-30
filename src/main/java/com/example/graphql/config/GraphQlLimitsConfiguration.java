package com.example.graphql.config;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the query-shape guards as {@link Instrumentation} beans, which Spring Boot's GraphQL
 * auto-configuration collects onto the {@code GraphQlSource}.
 */
@Configuration
@EnableConfigurationProperties(GraphQlLimitsProperties.class)
public class GraphQlLimitsConfiguration {

    @Bean
    public Instrumentation maxQueryDepthInstrumentation(GraphQlLimitsProperties properties) {
        return new MaxQueryDepthInstrumentation(properties.getMaxDepth());
    }

    @Bean
    public Instrumentation maxQueryComplexityInstrumentation(GraphQlLimitsProperties properties) {
        return new MaxQueryComplexityInstrumentation(properties.getMaxComplexity());
    }

    @Bean
    public Instrumentation maxQueryLengthInstrumentation(GraphQlLimitsProperties properties) {
        return new MaxQueryLengthInstrumentation(properties.getMaxQueryLength());
    }
}
