package com.example.graphql.config;

import graphql.schema.idl.RuntimeWiring;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers the {@link AuthDirectiveWiring} so the schema's {@code @auth} directive is applied when
 * the executable schema is built.
 */
@Configuration
public class AuthDirectiveConfig implements RuntimeWiringConfigurer {

    @Override
    public void configure(RuntimeWiring.Builder builder) {
        builder.directiveWiring(new AuthDirectiveWiring());
    }
}
