package com.example.graphql.config;

import graphql.GraphQLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Authenticates GraphQL over WebSocket connections.
 * <p>
 * HTTP requests are authorized by {@code @PreAuthorize}, but subscriptions run outside the servlet
 * thread's security context, so this interceptor validates the bearer token from the handshake (or a
 * {@code connection_init} payload carrying an {@code Authorization} entry) and publishes the
 * resulting {@link org.springframework.security.core.Authentication} into the GraphQL context under
 * {@link #AUTHENTICATION_KEY}. Subscription resolvers then require it explicitly.
 */
@Component
public class WebSocketAuthInterceptor implements WebSocketGraphQlInterceptor {

    public static final String AUTHENTICATION_KEY = "authentication";

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtDecoder jwtDecoder;

    public WebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, WebGraphQlInterceptor.Chain chain) {
        if (!(request instanceof WebSocketGraphQlRequest)) {
            return chain.next(request);
        }
        JwtAuthenticationToken authentication = authenticate(request.getHeaders());
        if (authentication != null) {
            request.configureExecutionInput((input, builder) -> builder
                    .graphQLContext(context -> context.put(AUTHENTICATION_KEY, authentication))
                    .build());
        }
        return chain.next(request);
    }

    private JwtAuthenticationToken authenticate(HttpHeaders headers) {
        String header = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()));
            List<String> roles = jwt.getClaimAsStringList("roles");
            List<GrantedAuthority> authorities = (roles == null ? List.<String>of() : roles).stream()
                    .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            log.info("Authenticated WebSocket subscription for '{}' with roles {}", jwt.getSubject(), roles);
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        } catch (JwtException ex) {
            log.warn("Rejected WebSocket bearer token: {}", ex.getMessage());
            return null;
        }
    }
}
