package com.example.graphql.config;

import graphql.language.EnumValue;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Implements the schema-declared {@code @auth(requires: Role)} directive by wrapping the field's
 * data fetcher with a role check. Declaring the policy in the SDL keeps it visible next to the field
 * it protects, unlike an annotation on the resolver method.
 */
public class AuthDirectiveWiring implements SchemaDirectiveWiring {

    private static final Logger log = LoggerFactory.getLogger(AuthDirectiveWiring.class);

    private static final String DIRECTIVE = "auth";
    private static final String REQUIRES = "requires";
    private static final String DEFAULT_ROLE = "USER";

    @Override
    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
        GraphQLAppliedDirective directive = environment.getAppliedDirective(DIRECTIVE);
        if (directive == null) {
            return environment.getElement();
        }

        String requiredRole = requiredRole(directive);
        GraphQLFieldDefinition field = environment.getElement();
        GraphQLFieldsContainer container = environment.getFieldsContainer();
        GraphQLCodeRegistry.Builder codeRegistry = environment.getCodeRegistry();

        FieldCoordinates coordinates = FieldCoordinates.coordinates(container.getName(), field.getName());
        DataFetcher<?> delegate = codeRegistry.getDataFetcher(coordinates, field);
        codeRegistry.dataFetcher(coordinates, authorize(delegate, requiredRole));

        log.debug("Secured field '{}.{}' with @auth(requires: {})",
                container.getName(), field.getName(), requiredRole);
        return field;
    }

    private static DataFetcher<?> authorize(DataFetcher<?> delegate, String requiredRole) {
        return environment -> {
            if (!hasRole(requiredRole)) {
                throw new AccessDeniedException("Requires role " + requiredRole);
            }
            return delegate.get(environment);
        };
    }

    private static boolean hasRole(String requiredRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        String authority = "ROLE_" + requiredRole;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private static String requiredRole(GraphQLAppliedDirective directive) {
        GraphQLAppliedDirectiveArgument argument = directive.getArgument(REQUIRES);
        if (argument == null || !argument.hasSetValue()) {
            return DEFAULT_ROLE;
        }
        Object value = argument.getValue();
        if (value instanceof EnumValue enumValue) {
            return enumValue.getName();
        }
        return value == null ? DEFAULT_ROLE : value.toString();
    }
}
