package com.example.graphql.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
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
import java.util.Map;

/**
 * Authenticates GraphQL over WebSocket connections.
 * <p>
 * HTTP requests are authorized by {@code @PreAuthorize}, but subscriptions run outside the servlet
 * thread's security context. This interceptor therefore authenticates the connection itself — from
 * the {@code connection_init} payload (what browsers can actually send) or the handshake
 * {@code Authorization} header — and publishes the result into the GraphQL context under
 * {@link #AUTHENTICATION_KEY}, where the subscription resolver requires it.
 */
@Component
public class WebSocketAuthInterceptor implements WebSocketGraphQlInterceptor {

    public static final String AUTHENTICATION_KEY = "authentication";

    private static final String AUTHORIZATION = "authorization";
    private static final String HEADERS = "headers";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtDecoder jwtDecoder;

    public WebSocketAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * A browser cannot set WebSocket handshake headers, so the token arrives in the
     * {@code connection_init} payload. Store the authentication on the session so every request on
     * the connection can use it. An invalid or missing token leaves the connection anonymous rather
     * than dropping the socket, so the client receives a GraphQL error instead.
     */
    @Override
    public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo sessionInfo,
                                                       Map<String, Object> connectionInitPayload) {
        JwtAuthenticationToken authentication = authenticate(tokenFromPayload(connectionInitPayload));
        if (authentication != null) {
            sessionInfo.getAttributes().put(AUTHENTICATION_KEY, authentication);
        }
        return Mono.empty();
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, WebGraphQlInterceptor.Chain chain) {
        if (!(request instanceof WebSocketGraphQlRequest webSocketRequest)) {
            return chain.next(request);
        }

        JwtAuthenticationToken authentication = fromSession(webSocketRequest);
        if (authentication == null) {
            authentication = authenticate(
                    bearerToken(webSocketRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)));
        }
        if (authentication != null) {
            JwtAuthenticationToken resolved = authentication;
            request.configureExecutionInput((input, builder) -> builder
                    .graphQLContext(context -> context.put(AUTHENTICATION_KEY, resolved))
                    .build());
        }
        return chain.next(request);
    }

    /**
     * Extracts a raw token from a {@code connection_init} payload, accepting both
     * {@code {"Authorization": "Bearer …"}} and {@code {"headers": {"authorization": …}}} shapes,
     * case-insensitively on the header name.
     */
    static String tokenFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        String direct = bearerToken(stringValueIgnoringCase(payload, AUTHORIZATION));
        if (direct != null) {
            return direct;
        }
        Object headers = payload.get(HEADERS);
        if (headers instanceof Map<?, ?> headerMap) {
            for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
                if (entry.getKey() instanceof String key
                        && key.equalsIgnoreCase(AUTHORIZATION)
                        && entry.getValue() instanceof String value) {
                    String token = bearerToken(value);
                    if (token != null) {
                        return token;
                    }
                }
            }
        }
        return null;
    }

    static String bearerToken(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static String stringValueIgnoringCase(Map<String, Object> map, String name) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey() != null
                    && entry.getKey().equalsIgnoreCase(name)
                    && entry.getValue() instanceof String value) {
                return value;
            }
        }
        return null;
    }

    private static JwtAuthenticationToken fromSession(WebSocketGraphQlRequest request) {
        Object stored = request.getSessionInfo().getAttributes().get(AUTHENTICATION_KEY);
        return stored instanceof JwtAuthenticationToken authentication ? authentication : null;
    }

    private JwtAuthenticationToken authenticate(String token) {
        if (token == null) {
            return null;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            List<String> roles = jwt.getClaimAsStringList("roles");
            List<GrantedAuthority> authorities = (roles == null ? List.<String>of() : roles).stream()
                    .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            log.info("Authenticated WebSocket connection for '{}' with roles {}", jwt.getSubject(), roles);
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        } catch (JwtException ex) {
            log.warn("Rejected WebSocket bearer token: {}", ex.getMessage());
            return null;
        }
    }
}
