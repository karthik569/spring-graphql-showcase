package com.example.graphql.model;

public record PublicationEdge(
        Publication node,
        String cursor
) {}
