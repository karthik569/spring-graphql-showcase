package com.example.graphql.repository;

import com.example.graphql.model.Book;
import com.example.graphql.model.BookFilter;
import com.example.graphql.model.BookSort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class BookRepository {

    static final RowMapper<Book> ROW_MAPPER = (rs, rowNum) -> new Book(
            rs.getString("id"),
            rs.getString("title"),
            rs.getInt("pages"),
            rs.getDouble("price"),
            rs.getInt("stock"),
            rs.getString("author_id"));

    private static final String SELECT = "select id, title, pages, price, stock, author_id from books";

    private final NamedParameterJdbcTemplate jdbc;

    public BookRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Book> findAll(BookFilter filter, BookSort sort, Integer limit, Integer offset) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder sql = new StringBuilder(SELECT)
                .append(where(filter, params))
                .append(orderBy(sort));
        if (limit != null) {
            sql.append(" limit :limit");
            params.put("limit", limit);
        }
        if (offset != null && offset > 0) {
            sql.append(" offset :offset");
            params.put("offset", offset);
        }
        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    public int count(BookFilter filter) {
        Map<String, Object> params = new HashMap<>();
        Integer count = jdbc.queryForObject("select count(*) from books" + where(filter, params),
                params, Integer.class);
        return count == null ? 0 : count;
    }

    public Optional<Book> findById(String id) {
        return jdbc.query(SELECT + " where id = :id", Map.of("id", id), ROW_MAPPER).stream().findFirst();
    }

    /**
     * The reverse batch lookup: every book belonging to any of the given authors, in one statement.
     */
    public List<Book> findByAuthorIds(Collection<String> authorIds) {
        if (authorIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(SELECT + " where author_id in (:authorIds) order by title",
                Map.of("authorIds", authorIds), ROW_MAPPER);
    }

    public List<Book> search(String needle) {
        return jdbc.query(SELECT + " where lower(title) like :needle order by lower(title)",
                Map.of("needle", "%" + needle + "%"), ROW_MAPPER);
    }

    public Book insert(String title, int pages, double price, int stock, String authorId) {
        String id = jdbc.queryForObject("select 'book-' || nextval('book_seq')", Map.of(), String.class);
        jdbc.update("insert into books (id, title, pages, price, stock, author_id)"
                        + " values (:id, :title, :pages, :price, :stock, :authorId)",
                Map.of("id", id, "title", title, "pages", pages, "price", price, "stock", stock,
                        "authorId", authorId));
        return new Book(id, title, pages, price, stock, authorId);
    }

    public int update(Book book) {
        return jdbc.update("update books set title = :title, pages = :pages, price = :price,"
                        + " stock = :stock, author_id = :authorId where id = :id",
                Map.of("id", book.id(), "title", book.title(), "pages", book.pages(),
                        "price", book.price(), "stock", book.stock(), "authorId", book.authorId()));
    }

    public int updateStock(String id, int stock) {
        return jdbc.update("update books set stock = :stock where id = :id", Map.of("id", id, "stock", stock));
    }

    public int delete(String id) {
        return jdbc.update("delete from books where id = :id", Map.of("id", id));
    }

    private static String where(BookFilter filter, Map<String, Object> params) {
        if (filter == null) {
            return "";
        }
        List<String> conditions = new ArrayList<>();
        if (filter.titleContains() != null && !filter.titleContains().isBlank()) {
            conditions.add("lower(title) like :titleContains");
            params.put("titleContains", "%" + filter.titleContains().toLowerCase(Locale.ROOT) + "%");
        }
        if (filter.authorId() != null) {
            conditions.add("author_id = :authorId");
            params.put("authorId", filter.authorId());
        }
        if (filter.minPrice() != null) {
            conditions.add("price >= :minPrice");
            params.put("minPrice", filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            conditions.add("price <= :maxPrice");
            params.put("maxPrice", filter.maxPrice());
        }
        if (filter.inStock() != null) {
            conditions.add(filter.inStock() ? "stock > 0" : "stock = 0");
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    private static String orderBy(BookSort sort) {
        BookSort effective = sort == null ? BookSort.TITLE_ASC : sort;
        return switch (effective) {
            case TITLE_ASC -> " order by lower(title) asc, id asc";
            case TITLE_DESC -> " order by lower(title) desc, id asc";
            case PRICE_ASC -> " order by price asc, id asc";
            case PRICE_DESC -> " order by price desc, id asc";
            case STOCK_ASC -> " order by stock asc, id asc";
            case STOCK_DESC -> " order by stock desc, id asc";
        };
    }
}
