package com.example.graphql.repository;

import com.example.graphql.events.BookEventPublisher;
import com.example.graphql.exception.AuthorNotFoundException;
import com.example.graphql.exception.BookNotFoundException;
import com.example.graphql.exception.InvalidBookInputException;
import com.example.graphql.model.Author;
import com.example.graphql.model.AuthorConnection;
import com.example.graphql.model.AuthorEdge;
import com.example.graphql.model.Book;
import com.example.graphql.model.BookConnection;
import com.example.graphql.model.BookEdge;
import com.example.graphql.model.BookFilter;
import com.example.graphql.model.BookSort;
import com.example.graphql.model.BookUpdateInput;
import com.example.graphql.model.Magazine;
import com.example.graphql.model.PageInfo;
import com.example.graphql.model.Publication;
import com.example.graphql.model.PublicationConnection;
import com.example.graphql.model.PublicationEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Domain service over the persistent catalog. Filtering, sorting, and paging for books are pushed
 * into SQL, and the two batch loaders resolve a whole list of relationships with a single
 * {@code IN (…)} query.
 */
@Service
public class CatalogDataService {

    private static final Logger log = LoggerFactory.getLogger(CatalogDataService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String CURSOR_PREFIX = "offset:";

    private final BookRepository books;
    private final AuthorRepository authors;
    private final MagazineRepository magazines;
    private final BookEventPublisher publisher;

    public CatalogDataService(BookRepository books, AuthorRepository authors,
                              MagazineRepository magazines, BookEventPublisher publisher) {
        this.books = books;
        this.authors = authors;
        this.magazines = magazines;
        this.publisher = publisher;
    }

    public List<Book> getAllBooks() {
        return books.findAll(null, BookSort.TITLE_ASC, null, null);
    }

    public Optional<Book> getBookById(String id) {
        return books.findById(id);
    }

    public List<Author> getAllAuthors() {
        return authors.findAll();
    }

    public Optional<Author> getAuthorById(String id) {
        return authors.findById(id);
    }

    public Optional<Magazine> getMagazineById(String id) {
        return magazines.findById(id);
    }

    public List<Magazine> magazinesPublishedAfter(Instant since) {
        return magazines.publishedAfter(since);
    }

    /**
     * Every publication regardless of concrete type: books and magazines grouped and ordered by title.
     */
    public List<Publication> getPublications() {
        List<Publication> publications = new ArrayList<>(getAllBooks());
        publications.addAll(magazines.findAll());
        publications.sort(Comparator.comparing(Publication::title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Publication::id));
        return publications;
    }

    /**
     * Case-insensitive search across book titles, magazine titles, and author names, backing the
     * {@code SearchResult} union.
     */
    public List<Object> search(String text) {
        String needle = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<Object> results = new ArrayList<>();
        results.addAll(books.search(needle));
        results.addAll(magazines.search(needle));
        results.addAll(authors.search(needle));
        return results;
    }

    public List<Book> findBooks(BookFilter filter, BookSort sort, Integer limit, Integer offset) {
        return books.findAll(filter, sort, limit, offset);
    }

    public int countBooks(BookFilter filter) {
        return books.count(filter);
    }

    /**
     * Cursor pagination over books. The count and the slice both come from SQL; cursors keep the
     * same opaque Base64 form.
     */
    public BookConnection bookConnection(Integer first, String after, BookFilter filter, BookSort sort) {
        int total = books.count(filter);
        Page page = page(total, first, after);
        List<Book> slice = books.findAll(filter, sort, page.end() - page.offset(), page.offset());

        List<BookEdge> edges = new ArrayList<>();
        for (int i = 0; i < slice.size(); i++) {
            edges.add(new BookEdge(slice.get(i), encodeCursor(page.offset() + i + 1)));
        }
        String startCursor = edges.isEmpty() ? null : edges.get(0).cursor();
        String endCursor = edges.isEmpty() ? null : edges.get(edges.size() - 1).cursor();
        return new BookConnection(edges, pageInfo(startCursor, endCursor, page, total), total);
    }

    public AuthorConnection authorConnection(Integer first, String after) {
        List<Author> all = authors.findAll();
        Page page = page(all.size(), first, after);

        List<AuthorEdge> edges = new ArrayList<>();
        for (int i = page.offset(); i < page.end(); i++) {
            edges.add(new AuthorEdge(all.get(i), encodeCursor(i + 1)));
        }
        String startCursor = edges.isEmpty() ? null : edges.get(0).cursor();
        String endCursor = edges.isEmpty() ? null : edges.get(edges.size() - 1).cursor();
        return new AuthorConnection(edges, pageInfo(startCursor, endCursor, page, all.size()), all.size());
    }

    public PublicationConnection publicationConnection(Integer first, String after) {
        List<Publication> all = getPublications();
        Page page = page(all.size(), first, after);

        List<PublicationEdge> edges = new ArrayList<>();
        for (int i = page.offset(); i < page.end(); i++) {
            edges.add(new PublicationEdge(all.get(i), encodeCursor(i + 1)));
        }
        String startCursor = edges.isEmpty() ? null : edges.get(0).cursor();
        String endCursor = edges.isEmpty() ? null : edges.get(edges.size() - 1).cursor();
        return new PublicationConnection(edges, pageInfo(startCursor, endCursor, page, all.size()), all.size());
    }

    /**
     * Forward batch loader: resolves the author for every book in a single query.
     */
    public Map<Book, Author> getAuthorsForBooks(List<Book> bookList) {
        log.info("[GRAPHQL-BATCH-MAPPING] Batch loading authors for {} books in a SINGLE query (Preventing N+1!)",
                bookList.size());

        Set<String> authorIds = bookList.stream().map(Book::authorId).collect(Collectors.toSet());
        Map<String, Author> byId = authors.findByIds(authorIds).stream()
                .collect(Collectors.toMap(Author::id, author -> author));

        Map<Book, Author> result = new HashMap<>();
        for (Book book : bookList) {
            Author author = byId.get(book.authorId());
            if (author != null) {
                result.put(book, author);
            }
        }
        return result;
    }

    /**
     * Reverse batch loader: resolves the books for every author in a single query. Authors without
     * books map to an empty list so the non-null schema list is satisfied.
     */
    public Map<Author, List<Book>> getBooksForAuthors(List<Author> authorList) {
        log.info("[GRAPHQL-BATCH-MAPPING] Batch loading books for {} authors in a SINGLE query (Preventing N+1!)",
                authorList.size());

        Set<String> authorIds = authorList.stream().map(Author::id).collect(Collectors.toSet());
        Map<String, List<Book>> byAuthorId = books.findByAuthorIds(authorIds).stream()
                .collect(Collectors.groupingBy(Book::authorId));

        Map<Author, List<Book>> result = new HashMap<>();
        for (Author author : authorList) {
            result.put(author, byAuthorId.getOrDefault(author.id(), List.of()));
        }
        return result;
    }

    public Book saveBook(String title, int pages, double price, int stock, String authorId) {
        validateBook(title, pages, price, stock, authorId);
        Book book = books.insert(title, pages, price, stock, authorId);
        publisher.publish(book);
        return book;
    }

    public Book updateBook(String bookId, BookUpdateInput input) {
        Book existing = books.findById(bookId).orElseThrow(() -> new BookNotFoundException(bookId));

        String title = input.title() != null ? input.title() : existing.title();
        int pages = input.pages() != null ? input.pages() : existing.pages();
        double price = input.price() != null ? input.price() : existing.price();
        int stock = input.stock() != null ? input.stock() : existing.stock();
        String authorId = input.authorId() != null ? input.authorId() : existing.authorId();

        validateBook(title, pages, price, stock, authorId);

        Book updated = new Book(existing.id(), title, pages, price, stock, authorId);
        books.update(updated);
        return updated;
    }

    public Book updateStock(String bookId, int newStock) {
        if (newStock < 0) {
            throw new InvalidBookInputException("stock", "stock must not be negative");
        }
        if (books.updateStock(bookId, newStock) == 0) {
            throw new BookNotFoundException(bookId);
        }
        return books.findById(bookId).orElseThrow(() -> new BookNotFoundException(bookId));
    }

    public boolean deleteBook(String bookId) {
        if (books.delete(bookId) == 0) {
            throw new BookNotFoundException(bookId);
        }
        return true;
    }

    private record Page(int offset, int end) {
    }

    private static Page page(int total, Integer first, String after) {
        int offset = Math.max(0, Math.min(decodeCursor(after), total));
        int size = first == null ? DEFAULT_PAGE_SIZE : Math.max(0, first);
        return new Page(offset, Math.min(offset + size, total));
    }

    private static PageInfo pageInfo(String startCursor, String endCursor, Page page, int total) {
        return new PageInfo(page.end() < total, page.offset() > 0, startCursor, endCursor);
    }

    private static String encodeCursor(int position) {
        return Base64.getEncoder().encodeToString((CURSOR_PREFIX + position).getBytes(StandardCharsets.UTF_8));
    }

    private static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (decoded.startsWith(CURSOR_PREFIX)) {
                return Integer.parseInt(decoded.substring(CURSOR_PREFIX.length()));
            }
        } catch (IllegalArgumentException ignored) {
            // an unparseable cursor falls back to the start of the list
        }
        return 0;
    }

    private void validateBook(String title, int pages, double price, int stock, String authorId) {
        if (title == null || title.isBlank()) {
            throw new InvalidBookInputException("title", "title must not be blank");
        }
        if (pages <= 0) {
            throw new InvalidBookInputException("pages", "pages must be positive");
        }
        if (price < 0) {
            throw new InvalidBookInputException("price", "price must not be negative");
        }
        if (stock < 0) {
            throw new InvalidBookInputException("stock", "stock must not be negative");
        }
        if (authorId == null || !authors.exists(authorId)) {
            throw new AuthorNotFoundException(authorId);
        }
    }
}
