package com.example.graphql.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebSocketAuthInterceptorTest {

    @Test
    void readsTokenFromTopLevelAuthorization() {
        assertEquals("abc", WebSocketAuthInterceptor.tokenFromPayload(Map.of("Authorization", "Bearer abc")));
    }

    @Test
    void readsTokenFromNestedHeadersCaseInsensitively() {
        assertEquals("abc", WebSocketAuthInterceptor.tokenFromPayload(
                Map.of("headers", Map.of("authorization", "Bearer abc"))));
    }

    @Test
    void ignoresMissingOrNonBearerValues() {
        assertNull(WebSocketAuthInterceptor.tokenFromPayload(Map.of()));
        assertNull(WebSocketAuthInterceptor.tokenFromPayload(Map.of("Authorization", "Basic abc")));
        assertNull(WebSocketAuthInterceptor.tokenFromPayload(Map.of("Authorization", "Bearer  ")));
    }

    @Test
    void bearerTokenRequiresThePrefix() {
        assertEquals("abc", WebSocketAuthInterceptor.bearerToken("Bearer abc"));
        assertNull(WebSocketAuthInterceptor.bearerToken("abc"));
        assertNull(WebSocketAuthInterceptor.bearerToken(null));
    }
}
