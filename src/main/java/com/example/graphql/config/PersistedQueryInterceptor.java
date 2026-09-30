package com.example.graphql.config;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * Automatic persisted queries (APQ), following Apollo's contract.
 * <p>
 * A request may carry {@code extensions.persistedQuery.sha256Hash}. When the document is also
 * present it is registered under that hash; when only the hash is sent the stored document is
 * substituted. An unknown hash gets a {@code PersistedQueryNotFound} error so the client can retry
 * with the full document, and a hash that does not match its document is rejected.
 */
@Component
public class PersistedQueryInterceptor implements WebGraphQlInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PersistedQueryInterceptor.class);

    private static final String PERSISTED_QUERY = "persistedQuery";
    private static final String SHA256_HASH = "sha256Hash";
    private static final String HASH = "hash";
    private static final String NOT_FOUND_CODE = "PERSISTED_QUERY_NOT_FOUND";
    private static final String MISMATCH_CODE = "PERSISTED_QUERY_HASH_MISMATCH";

    /**
     * Spring substitutes this sentinel as the document when a request carries only
     * {@code extensions.persistedQuery}, so it must be treated as "no document".
     */
    private static final String PERSISTED_QUERY_MARKER = "PersistedQueryMarker";

    private final PersistedQueryStore store;

    public PersistedQueryInterceptor(PersistedQueryStore store) {
        this.store = store;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String hash = hash(request);
        if (hash == null) {
            return chain.next(request);
        }

        String document = request.getDocument();
        if (document != null && !document.isBlank() && !PERSISTED_QUERY_MARKER.equals(document)) {
            String computed = sha256(document);
            if (!computed.equalsIgnoreCase(hash)) {
                log.warn("Persisted query hash mismatch: supplied {} but document hashes to {}", hash, computed);
                return chain.next(request).map(response -> response.transform(builder -> builder
                        .errors(List.of(error(MISMATCH_CODE, "Persisted query hash does not match the document")))
                        .build()));
            }
            store.register(hash, document);
            return chain.next(request);
        }

        String stored = store.lookup(hash);
        if (stored == null) {
            log.info("Unknown persisted query hash {}", hash);
            return chain.next(request).map(response -> response.transform(builder -> builder
                    .errors(List.of(error(NOT_FOUND_CODE, "PersistedQueryNotFound")))
                    .build()));
        }

        log.info("Serving persisted query for hash {}", hash);
        request.configureExecutionInput((input, builder) -> builder.query(stored).build());
        return chain.next(request);
    }

    private static GraphQLError error(String code, String message) {
        return GraphqlErrorBuilder.newError()
                .message(message)
                .extensions(Map.of("code", code))
                .build();
    }

    private static String hash(WebGraphQlRequest request) {
        Object persistedQuery = request.getExtensions().get(PERSISTED_QUERY);
        if (persistedQuery instanceof Map<?, ?> map) {
            Object value = map.containsKey(SHA256_HASH) ? map.get(SHA256_HASH) : map.get(HASH);
            return value instanceof String text ? text : null;
        }
        return null;
    }

    private static String sha256(String document) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(document.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16)).append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
