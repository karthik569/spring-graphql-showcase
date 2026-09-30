package com.example.graphql.config;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Registers the custom scalars used by the schema. Both are implemented with a hand-written
 * {@link Coercing} to demonstrate how GraphQL scalar coercion works.
 */
@Configuration
public class GraphQlScalarConfig implements RuntimeWiringConfigurer {

    private static final GraphQLScalarType DATE_TIME = GraphQLScalarType.newScalar()
            .name("DateTime")
            .description("An ISO-8601 instant, for example 2024-05-01T10:15:30Z")
            .coercing(new Coercing<Instant, String>() {
                @Override
                public String serialize(Object dataFetcherResult, GraphQLContext context, Locale locale) {
                    if (dataFetcherResult instanceof Instant instant) {
                        return DateTimeFormatter.ISO_INSTANT.format(instant);
                    }
                    throw new CoercingSerializeException("Cannot serialize " + dataFetcherResult + " as DateTime");
                }

                @Override
                public Instant parseValue(Object input, GraphQLContext context, Locale locale) {
                    if (input instanceof String text) {
                        try {
                            return Instant.parse(text);
                        } catch (DateTimeParseException ex) {
                            throw new CoercingParseValueException("Invalid DateTime: " + text);
                        }
                    }
                    throw new CoercingParseValueException("DateTime must be a string");
                }

                @Override
                public Instant parseLiteral(Value<?> input, CoercedVariables variables,
                                            GraphQLContext context, Locale locale) {
                    if (input instanceof StringValue stringValue) {
                        return parseValue(stringValue.getValue(), context, locale);
                    }
                    throw new CoercingParseLiteralException("DateTime must be a string literal");
                }
            })
            .build();

    private static final GraphQLScalarType URL = GraphQLScalarType.newScalar()
            .name("URL")
            .description("An absolute URL, for example https://example.com")
            .coercing(new Coercing<URI, String>() {
                @Override
                public String serialize(Object dataFetcherResult, GraphQLContext context, Locale locale) {
                    if (dataFetcherResult instanceof URI uri) {
                        return uri.toString();
                    }
                    throw new CoercingSerializeException("Cannot serialize " + dataFetcherResult + " as URL");
                }

                @Override
                public URI parseValue(Object input, GraphQLContext context, Locale locale) {
                    if (input instanceof String text) {
                        try {
                            return new URI(text);
                        } catch (URISyntaxException ex) {
                            throw new CoercingParseValueException("Invalid URL: " + text);
                        }
                    }
                    throw new CoercingParseValueException("URL must be a string");
                }

                @Override
                public URI parseLiteral(Value<?> input, CoercedVariables variables,
                                        GraphQLContext context, Locale locale) {
                    if (input instanceof StringValue stringValue) {
                        return parseValue(stringValue.getValue(), context, locale);
                    }
                    throw new CoercingParseLiteralException("URL must be a string literal");
                }
            })
            .build();

    @Override
    public void configure(RuntimeWiring.Builder builder) {
        builder.scalar(DATE_TIME).scalar(URL);
    }
}
