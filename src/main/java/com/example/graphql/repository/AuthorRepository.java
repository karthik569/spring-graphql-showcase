package com.example.graphql.repository;

import com.example.graphql.model.Author;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AuthorRepository {

    static final RowMapper<Author> ROW_MAPPER = (rs, rowNum) ->
            new Author(rs.getString("id"), rs.getString("name"), rs.getString("country"));

    private static final String SELECT = "select id, name, country from authors";

    private final NamedParameterJdbcTemplate jdbc;

    public AuthorRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Author> findAll() {
        return jdbc.query(SELECT + " order by name", Map.of(), ROW_MAPPER);
    }

    public Optional<Author> findById(String id) {
        return jdbc.query(SELECT + " where id = :id", Map.of("id", id), ROW_MAPPER).stream().findFirst();
    }

    /**
     * The batch lookup: every author for a whole list of ids in a single statement.
     */
    public List<Author> findByIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query(SELECT + " where id in (:ids)", Map.of("ids", ids), ROW_MAPPER);
    }

    public List<Author> search(String needle) {
        return jdbc.query(SELECT + " where lower(name) like :needle order by name",
                Map.of("needle", "%" + needle + "%"), ROW_MAPPER);
    }

    public boolean exists(String id) {
        Integer count = jdbc.queryForObject("select count(*) from authors where id = :id",
                Map.of("id", id), Integer.class);
        return count != null && count > 0;
    }
}
