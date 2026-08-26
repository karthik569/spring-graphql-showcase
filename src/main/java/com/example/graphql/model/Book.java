package com.example.graphql.model;

public record Book(
        String id,
        String title,
        int pages,
        double price,
        int stock,
        String authorId
) {}
