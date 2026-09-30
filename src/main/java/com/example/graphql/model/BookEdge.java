package com.example.graphql.model;

public record BookEdge(
        Book node,
        String cursor
) {}
