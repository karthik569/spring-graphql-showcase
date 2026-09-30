package com.example.graphql.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of persisted query documents keyed by their SHA-256 hash.
 * <p>
 * Per-instance and bounded: fine for a single-node showcase, but a real deployment needs a shared,
 * evicting store.
 */
@Component
public class PersistedQueryStore {

    private static final int MAX_ENTRIES = 500;

    private final Map<String, String> documents = new ConcurrentHashMap<>();

    public void register(String hash, String document) {
        if (documents.size() >= MAX_ENTRIES && !documents.containsKey(hash)) {
            return;
        }
        documents.put(hash, document);
    }

    public String lookup(String hash) {
        return documents.get(hash);
    }
}
