package com.example.graphql.model;

public record AuthorEdge(
        Author node,
        String cursor
) {}
