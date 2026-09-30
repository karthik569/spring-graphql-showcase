package com.example.graphql.repository;

import com.example.graphql.model.Magazine;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MagazineRepository {

    static final RowMapper<Magazine> ROW_MAPPER = (rs, rowNum) -> {
        String website = rs.getString("website");
        return new Magazine(
                rs.getString("id"),
                rs.getString("title"),
                rs.getInt("issue_number"),
                rs.getString("publisher"),
                rs.getObject("published_on", OffsetDateTime.class).toInstant(),
                website == null ? null : URI.create(website));
    };

    private static final String SELECT =
            "select id, title, issue_number, publisher, published_on, website from magazines";

    private final NamedParameterJdbcTemplate jdbc;

    public MagazineRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Magazine> findAll() {
        return jdbc.query(SELECT + " order by title", Map.of(), ROW_MAPPER);
    }

    public Optional<Magazine> findById(String id) {
        return jdbc.query(SELECT + " where id = :id", Map.of("id", id), ROW_MAPPER).stream().findFirst();
    }

    public List<Magazine> search(String needle) {
        return jdbc.query(SELECT + " where lower(title) like :needle order by lower(title)",
                Map.of("needle", "%" + needle + "%"), ROW_MAPPER);
    }

    public List<Magazine> publishedAfter(Instant since) {
        return jdbc.query(SELECT + " where published_on >= :since order by published_on, id",
                Map.of("since", OffsetDateTime.ofInstant(since, java.time.ZoneOffset.UTC)), ROW_MAPPER);
    }
}
