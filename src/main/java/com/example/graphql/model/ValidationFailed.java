package com.example.graphql.model;

public record ValidationFailed(
        String message,
        String field
) {}
