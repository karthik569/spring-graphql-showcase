package com.example.graphql.model;

public record BookFilter(
        String titleContains,
        String authorId,
        Double minPrice,
        Double maxPrice,
        Boolean inStock
) {}
