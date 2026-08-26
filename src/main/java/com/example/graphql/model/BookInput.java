package com.example.graphql.model;

public record BookInput(
        String title,
        int pages,
        double price,
        int stock,
        String authorId
) {}
