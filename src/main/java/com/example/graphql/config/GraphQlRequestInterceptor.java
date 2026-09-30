package com.example.graphql.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Observes every GraphQL request: logs the operation and its duration, and records a Micrometer
 * timer. Purely observational — the response is returned unchanged.
 */
@Component
public class GraphQlRequestInterceptor implements WebGraphQlInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GraphQlRequestInterceptor.class);

    private final MeterRegistry meterRegistry;

    public GraphQlRequestInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        long start = System.nanoTime();
        String operation = request.getOperationName() != null ? request.getOperationName() : "(anonymous)";
        return chain.next(request).map(response -> {
            record(operation, response, Duration.ofNanos(System.nanoTime() - start));
            return response;
        });
    }

    private void record(String operation, WebGraphQlResponse response, Duration duration) {
        String outcome = response.getErrors().isEmpty() ? "success" : "error";

        log.info("GraphQL request '{}' completed in {} ms ({})", operation, duration.toMillis(), outcome);

        Timer.builder("graphql.request")
                .description("GraphQL request duration")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
    }
}
