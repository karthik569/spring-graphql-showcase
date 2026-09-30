package com.example.graphql.model;

public record AuthPayload(
        String accessToken,
        String tokenType,
        int expiresIn
) {}
