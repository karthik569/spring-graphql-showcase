package com.example.graphql.controller;

import com.example.graphql.events.BookEventPublisher;
import com.example.graphql.model.Author;
import com.example.graphql.model.AuthorConnection;
import com.example.graphql.model.Book;
import com.example.graphql.model.BookConnection;
import com.example.graphql.model.BookFilter;
import com.example.graphql.model.BookInput;
import com.example.graphql.model.BookSort;
import com.example.graphql.model.BookUpdateInput;
import com.example.graphql.model.Magazine;
import com.example.graphql.model.Publication;
import com.example.graphql.model.PublicationConnection;
import com.example.graphql.repository.CatalogDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Controller exposing GraphQL Schema operations using Spring for GraphQL.
 * <p>
 * Demonstrates:
 * <ul>
 *   <li>{@link QueryMapping}: Resolves top-level GraphQL queries (e.g. {@code books}, {@code bookById})</li>
 *   <li>{@link MutationMapping}: Resolves data modification operations (e.g. {@code addBook}, {@code updateStock})</li>
 *   <li>{@link BatchMapping}: **Solves the N+1 database problem** by batch loading relationships in a single DataLoader round-trip, in both directions ({@code Book.author} and {@code Author.books})</li>
 *   <li>{@link SubscriptionMapping}: Streams new books to subscribed clients over WebSocket</li>
 * </ul>
 *
 * @author Spring Showcase Team
 * @version 1.0
 */
@Controller
public class BookGraphQLController {

    private static final Logger log = LoggerFactory.getLogger(BookGraphQLController.class);

    private final CatalogDataService catalogService;
    private final BookEventPublisher publisher;

    /**
     * Constructs the GraphQL controller with the catalog data repository and the book event publisher.
     *
     * @param catalogService data service providing book and author records
     * @param publisher      publisher emitting newly created books to subscribers
     */
    public BookGraphQLController(CatalogDataService catalogService, BookEventPublisher publisher) {
        this.catalogService = catalogService;
        this.publisher = publisher;
    }

    /**
     * Resolves the top-level {@code books} query, optionally filtering, sorting, and paging the catalog.
     *
     * @param filter optional filter on title, author, price, and stock
     * @param sort   optional sort order (defaults to title ascending)
     * @param limit  optional maximum number of books to return
     * @param offset optional number of books to skip
     * @return the matching {@link Book} records
     */
    @QueryMapping
    public List<Book> books(@Argument BookFilter filter, @Argument BookSort sort,
                            @Argument Integer limit, @Argument Integer offset) {
        List<Book> books = catalogService.findBooks(filter, sort, limit, offset);
        log.info("Query 'books' filter={} sort={} limit={} offset={} -> returning {} book(s)",
                filter, sort, limit, offset, books.size());
        return books;
    }

    /**
     * Resolves the {@code bookCount(filter: BookFilter)} query returning the number of matching books.
     *
     * @param filter optional filter on title, author, price, and stock
     * @return the number of books matching the filter
     */
    @QueryMapping
    public int bookCount(@Argument BookFilter filter) {
        int count = catalogService.countBooks(filter);
        log.info("Query 'bookCount' filter={} -> {}", filter, count);
        return count;
    }

    /**
     * Resolves the Relay-style {@code bookConnection(first: Int, after: String, ...)} query.
     *
     * @param first  optional page size (defaults to 20)
     * @param after  optional opaque cursor to resume from
     * @param filter optional filter on title, author, price, and stock
     * @param sort   optional sort order (defaults to title ascending)
     * @return a {@link BookConnection} of edges with cursors plus page information
     */
    @QueryMapping
    public BookConnection bookConnection(@Argument Integer first, @Argument String after,
                                         @Argument BookFilter filter, @Argument BookSort sort) {
        BookConnection connection = catalogService.bookConnection(first, after, filter, sort);
        log.info("Query 'bookConnection' first={} after={} -> {} edge(s) of {} total",
                first, after, connection.edges().size(), connection.totalCount());
        return connection;
    }

    /**
     * Resolves the Relay-style {@code authorConnection(first: Int, after: String)} query.
     *
     * @param first optional page size (defaults to 20)
     * @param after optional opaque cursor to resume from
     * @return a connection of authors with cursors and page information
     */
    @QueryMapping
    public AuthorConnection authorConnection(@Argument Integer first, @Argument String after) {
        AuthorConnection connection = catalogService.authorConnection(first, after);
        log.info("Query 'authorConnection' first={} after={} -> {} edge(s) of {} total",
                first, after, connection.edges().size(), connection.totalCount());
        return connection;
    }

    /**
     * Resolves the Relay-style {@code publicationConnection(first: Int, after: String)} query, whose
     * edges carry the {@code Publication} interface.
     *
     * @param first optional page size (defaults to 20)
     * @param after optional opaque cursor to resume from
     * @return a connection of publications with cursors and page information
     */
    @QueryMapping
    public PublicationConnection publicationConnection(@Argument Integer first, @Argument String after) {
        PublicationConnection connection = catalogService.publicationConnection(first, after);
        log.info("Query 'publicationConnection' first={} after={} -> {} edge(s) of {} total",
                first, after, connection.edges().size(), connection.totalCount());
        return connection;
    }

    /**
     * Resolves {@code magazinesPublishedAfter(since: DateTime!)}, exercising the custom scalar as an argument.
     *
     * @param since inclusive lower bound on the publication date
     * @return matching magazines ordered by publication date
     */
    @QueryMapping
    public List<Magazine> magazinesPublishedAfter(@Argument Instant since) {
        List<Magazine> magazines = catalogService.magazinesPublishedAfter(since);
        log.info("Query 'magazinesPublishedAfter' since={} -> {} magazine(s)", since, magazines.size());
        return magazines;
    }

    /**
     * Resolves the {@code bookById(id: ID!)} query looking up a single book.
     *
     * @param id the book unique identifier
     * @return optional containing the matching {@link Book} if present
     */
    @QueryMapping
    public Optional<Book> bookById(@Argument String id) {
        Optional<Book> book = catalogService.getBookById(id);
        log.info("Query 'bookById' id='{}' -> {}", id, book.map(Book::title).orElse("not found"));
        return book;
    }

    /**
     * Resolves the {@code authors} query returning all authors.
     *
     * @return list of all {@link Author} entities
     */
    @QueryMapping
    public List<Author> authors() {
        List<Author> authors = catalogService.getAllAuthors();
        log.info("Query 'authors' -> returning {} author(s)", authors.size());
        return authors;
    }

    /**
     * Resolves the {@code authorById(id: ID!)} query.
     *
     * @param id the author unique identifier
     * @return optional containing the matching {@link Author}
     */
    @QueryMapping
    public Optional<Author> authorById(@Argument String id) {
        Optional<Author> author = catalogService.getAuthorById(id);
        log.info("Query 'authorById' id='{}' -> {}", id, author.map(Author::name).orElse("not found"));
        return author;
    }

    /**
     * Resolves the {@code publications} query returning books and magazines through the
     * {@code Publication} interface.
     *
     * @return every publication, of mixed concrete types
     */
    @QueryMapping
    public List<Publication> publications() {
        List<Publication> publications = catalogService.getPublications();
        log.info("Query 'publications' -> returning {} publication(s)", publications.size());
        return publications;
    }

    /**
     * Resolves the {@code magazineById(id: ID!)} query.
     *
     * @param id the magazine unique identifier
     * @return optional containing the matching {@link Magazine}
     */
    @QueryMapping
    public Optional<Magazine> magazineById(@Argument String id) {
        Optional<Magazine> magazine = catalogService.getMagazineById(id);
        log.info("Query 'magazineById' id='{}' -> {}", id, magazine.map(Magazine::title).orElse("not found"));
        return magazine;
    }

    /**
     * Resolves the {@code search(text: String!)} query returning the {@code SearchResult} union.
     *
     * @param text case-insensitive text matched against book and magazine titles and author names
     * @return heterogeneous results resolved to their concrete types
     */
    @QueryMapping
    public List<Object> search(@Argument String text) {
        List<Object> results = catalogService.search(text);
        log.info("Query 'search' text='{}' -> {} result(s)", text, results.size());
        return results;
    }

    /**
     * Resolves the {@code addBook(input: BookInput!)} GraphQL mutation.
     *
     * @param input the input record containing title, pages, price, stock, and author ID
     * @return the created {@link Book} entity
     */
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public Book addBook(@Argument BookInput input) {
        Book book = catalogService.saveBook(
                input.title(),
                input.pages(),
                input.price(),
                input.stock(),
                input.authorId()
        );
        log.info("Mutation 'addBook' title='{}' authorId='{}' -> created id='{}'",
                book.title(), book.authorId(), book.id());
        return book;
    }

    /**
     * Resolves the {@code updateBook(id: ID!, input: BookUpdateInput!)} GraphQL mutation.
     * Only the fields present in the input are changed.
     *
     * @param id    the book ID to update
     * @param input the partial update containing any of title, pages, price, stock, and author ID
     * @return the updated {@link Book} entity
     */
    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public Book updateBook(@Argument String id, @Argument BookUpdateInput input) {
        Book book = catalogService.updateBook(id, input);
        log.info("Mutation 'updateBook' id='{}' -> title='{}' stock={}", id, book.title(), book.stock());
        return book;
    }

    /**
     * Resolves the {@code deleteBook(id: ID!)} GraphQL mutation.
     *
     * @param id the book ID to delete
     * @return {@code true} when the book was removed
     */
    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public boolean deleteBook(@Argument String id) {
        boolean deleted = catalogService.deleteBook(id);
        log.info("Mutation 'deleteBook' id='{}' -> {}", id, deleted);
        return deleted;
    }

    /**
     * Resolves the {@code updateStock(id: ID!, stock: Int!)} GraphQL mutation.
     *
     * @param id    the book ID
     * @param stock the new stock inventory count
     * @return the updated {@link Book} entity
     * @deprecated use {@code updateBook(id: ID!, input: BookUpdateInput!)} instead
     */
    @PreAuthorize("isAuthenticated()")
    @Deprecated
    @MutationMapping
    public Book updateStock(@Argument String id, @Argument int stock) {
        Book book = catalogService.updateStock(id, stock);
        log.info("Mutation 'updateStock' id='{}' stock={} -> updated", id, stock);
        return book;
    }

    /**
     * Field-level authorization: the internal cost price is visible only to administrators. Because
     * the field is non-null, a denied read also demonstrates non-null error propagation.
     *
     * @param book the parent book
     * @return the derived internal cost price
     */
    @SchemaMapping(typeName = "Book", field = "costPrice")
    @PreAuthorize("hasRole('ADMIN')")
    public double costPrice(Book book) {
        return Math.round(book.price() * 0.6 * 100.0) / 100.0;
    }

    /**
     * Field-level authorization: contact details require an authenticated caller.
     *
     * @param author the parent author
     * @return the derived contact email
     */
    @SchemaMapping(typeName = "Author", field = "email")
    @PreAuthorize("isAuthenticated()")
    public String email(Author author) {
        return author.name().toLowerCase(Locale.ROOT).replace(' ', '.') + "@example.com";
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

    /**
     * Batch loader resolving book relationships for a collection of authors in a single pass.
     * <p>
     * The reverse of {@link #author(List)}: for a query such as {@code authors { books { title } }}
     * the engine collects every parent {@link Author} and calls this method once.
     *
     * @param authors the list of parent {@link Author} items requested by the GraphQL client
     * @return a {@link Map} mapping each {@link Author} to its books (an empty list when none)
     */
    @BatchMapping(typeName = "Author", field = "books")
    public Map<Author, List<Book>> books(List<Author> authors) {
        return catalogService.getBooksForAuthors(authors);
    }

    /**
     * Subscription stream of newly created books, pushed to subscribed clients over WebSocket.
     *
     * @return a hot {@link Flux} emitting every book added after the client subscribes
     */
    @SubscriptionMapping
    public Flux<Book> bookAdded() {
        return publisher.stream();
    }
}
