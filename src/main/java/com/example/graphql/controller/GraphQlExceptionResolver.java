package com.example.graphql.controller;

import com.example.graphql.exception.AuthorNotFoundException;
import com.example.graphql.exception.BookNotFoundException;
import com.example.graphql.exception.InvalidBookInputException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Maps domain exceptions onto typed GraphQL errors so clients receive a meaningful
 * {@code classification} (NOT_FOUND / BAD_REQUEST) instead of an opaque INTERNAL_ERROR.
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphQlExceptionResolver.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof BookNotFoundException || ex instanceof AuthorNotFoundException) {
            return toError(ex, ErrorType.NOT_FOUND, env);
        }
        if (ex instanceof InvalidBookInputException) {
            return toError(ex, ErrorType.BAD_REQUEST, env);
        }
        return null;
    }

    private GraphQLError toError(Throwable ex, ErrorType type, DataFetchingEnvironment env) {
        log.warn("GraphQL request failed [{}]: {}", type, ex.getMessage());
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .errorType(type)
                .build();
    }
}
