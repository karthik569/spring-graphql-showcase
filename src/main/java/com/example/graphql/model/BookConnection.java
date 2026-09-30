package com.example.graphql.model;

import java.util.List;

public record BookConnection(
        List<BookEdge> edges,
        PageInfo pageInfo,
        int totalCount
) {}
