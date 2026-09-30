package com.example.graphql.model;

import java.net.URI;
import java.time.Instant;

public record Magazine(
        String id,
        String title,
        int issueNumber,
        String publisher,
        Instant publishedOn,
        URI website
) implements Publication {}
