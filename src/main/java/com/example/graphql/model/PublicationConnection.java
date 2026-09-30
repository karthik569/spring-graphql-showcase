package com.example.graphql.model;

import java.util.List;

public record PublicationConnection(
        List<PublicationEdge> edges,
        PageInfo pageInfo,
        int totalCount
) {}
