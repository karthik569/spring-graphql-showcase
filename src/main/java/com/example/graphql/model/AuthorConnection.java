package com.example.graphql.model;

import java.util.List;

public record AuthorConnection(
        List<AuthorEdge> edges,
        PageInfo pageInfo,
        int totalCount
) {}
