package com.example.graphql.model;

public record BookUpdateInput(
        String title,
        Integer pages,
        Double price,
        Integer stock,
        String authorId
) {}
