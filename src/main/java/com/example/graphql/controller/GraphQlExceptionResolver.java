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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Maps domain and security exceptions onto typed GraphQL errors so clients receive a meaningful
 * {@code classification} (NOT_FOUND / BAD_REQUEST / UNAUTHORIZED / FORBIDDEN) instead of an opaque
 * INTERNAL_ERROR.
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphQlExceptionResolver.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof BookNotFoundException || ex instanceof AuthorNotFoundException) {
            return toError(ex.getMessage(), ErrorType.NOT_FOUND, env);
        }
        if (ex instanceof InvalidBookInputException) {
            return toError(ex.getMessage(), ErrorType.BAD_REQUEST, env);
        }
        if (ex instanceof AuthenticationException) {
            return toError("Invalid credentials", ErrorType.UNAUTHORIZED, env);
        }
        if (ex instanceof AccessDeniedException) {
            boolean anonymous = isAnonymous();
            return toError(anonymous ? "Authentication required" : "Access denied",
                    anonymous ? ErrorType.UNAUTHORIZED : ErrorType.FORBIDDEN, env);
        }
        return null;
    }

    private static boolean isAnonymous() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    private GraphQLError toError(String message, ErrorType type, DataFetchingEnvironment env) {
        log.warn("GraphQL request failed [{}]: {}", type, message);
        return GraphqlErrorBuilder.newError(env)
                .message(message)
                .errorType(type)
                .build();
    }
}
