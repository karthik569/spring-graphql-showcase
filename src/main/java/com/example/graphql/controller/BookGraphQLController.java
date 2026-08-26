package com.example.graphql.controller;

import com.example.graphql.model.Author;
import com.example.graphql.model.Book;
import com.example.graphql.model.BookInput;
import com.example.graphql.repository.CatalogDataService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller exposing GraphQL Schema operations using Spring for GraphQL.
 * <p>
 * Demonstrates:
 * <ul>
 *   <li>{@link QueryMapping}: Resolves top-level GraphQL queries (e.g. {@code books}, {@code bookById})</li>
 *   <li>{@link MutationMapping}: Resolves data modification operations (e.g. {@code addBook}, {@code updateStock})</li>
 *   <li>{@link BatchMapping}: **Solves the N+1 database problem** by batch loading authors across books in a single DataLoader round-trip</li>
 * </ul>
 *
 * @author Spring Showcase Team
 * @version 1.0
 */
@Controller
public class BookGraphQLController {

    private final CatalogDataService catalogService;

    /**
     * Constructs the GraphQL controller with the catalog data repository.
     *
     * @param catalogService data service providing book and author records
     */
    public BookGraphQLController(CatalogDataService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * Resolves the top-level {@code books} query returning all catalog books.
     *
     * @return list of all available {@link Book} records
     */
    @QueryMapping
    public List<Book> books() {
        return catalogService.getAllBooks();
    }

    /**
     * Resolves the {@code bookById(id: ID!)} query looking up a single book.
     *
     * @param id the book unique identifier
     * @return optional containing the matching {@link Book} if present
     */
    @QueryMapping
    public Optional<Book> bookById(@Argument String id) {
        return catalogService.getBookById(id);
    }

    /**
     * Resolves the {@code authors} query returning all authors.
     *
     * @return list of all {@link Author} entities
     */
    @QueryMapping
    public List<Author> authors() {
        return catalogService.getAllAuthors();
    }

    /**
     * Resolves the {@code authorById(id: ID!)} query.
     *
     * @param id the author unique identifier
     * @return optional containing the matching {@link Author}
     */
    @QueryMapping
    public Optional<Author> authorById(@Argument String id) {
        return catalogService.getAuthorById(id);
    }

    /**
     * Resolves the {@code addBook(input: BookInput!)} GraphQL mutation.
     *
     * @param input the input record containing title, pages, price, stock, and author ID
     * @return the created {@link Book} entity
     */
    @MutationMapping
    public Book addBook(@Argument BookInput input) {
        return catalogService.saveBook(
                input.title(),
                input.pages(),
                input.price(),
                input.stock(),
                input.authorId()
        );
    }

    /**
     * Resolves the {@code updateStock(id: ID!, stock: Int!)} GraphQL mutation.
     *
     * @param id    the book ID
     * @param stock the new stock inventory count
     * @return the updated {@link Book} entity
     */
    @MutationMapping
    public Book updateStock(@Argument String id, @Argument int stock) {
        return catalogService.updateStock(id, stock);
    }

    /**
     * Batch loader resolving author relationships for a collection of books in a single pass.
     * <p>
     * Instead of executing $N$ separate queries for $N$ books, Spring for GraphQL batches
     * all book instances and calls this method once, completely eliminating $N+1$ latency.
     *
     * @param books the list of parent {@link Book} items requested by the GraphQL client
     * @return a {@link Map} mapping each {@link Book} to its resolved {@link Author}
     */
    @BatchMapping(typeName = "Book", field = "author")
    public Map<Book, Author> author(List<Book> books) {
        return catalogService.getAuthorsForBooks(books);
    }
}
