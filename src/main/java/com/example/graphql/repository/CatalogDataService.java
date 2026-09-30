package com.example.graphql.repository;

import com.example.graphql.model.Author;
import com.example.graphql.model.Book;
import com.example.graphql.model.BookFilter;
import com.example.graphql.model.BookSort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CatalogDataService {

    private static final Logger log = LoggerFactory.getLogger(CatalogDataService.class);

    private final Map<String, Book> books = new ConcurrentHashMap<>();
    private final Map<String, Author> authors = new ConcurrentHashMap<>();

    public CatalogDataService() {
        authors.put("author-1", new Author("author-1", "Joshua Bloch", "USA"));
        authors.put("author-2", new Author("author-2", "Martin Fowler", "UK"));
        authors.put("author-3", new Author("author-3", "Robert C. Martin", "USA"));

        books.put("book-1", new Book("book-1", "Effective Java", 416, 45.0, 50, "author-1"));
        books.put("book-2", new Book("book-2", "Refactoring", 448, 55.0, 30, "author-2"));
        books.put("book-3", new Book("book-3", "Clean Code", 464, 40.0, 75, "author-3"));
        books.put("book-4", new Book("book-4", "Java Puzzlers", 312, 35.0, 20, "author-1"));
    }

    public List<Book> getAllBooks() {
        return new ArrayList<>(books.values());
    }

    public Optional<Book> getBookById(String id) {
        return Optional.ofNullable(books.get(id));
    }

    public List<Author> getAllAuthors() {
        return new ArrayList<>(authors.values());
    }

    public Optional<Author> getAuthorById(String id) {
        return Optional.ofNullable(authors.get(id));
    }

    /**
     * Filters, sorts, and pages the catalog. Sorting is applied before paging so results are stable.
     */
    public List<Book> findBooks(BookFilter filter, BookSort sort, Integer limit, Integer offset) {
        Stream<Book> stream = filtered(filter).sorted(comparator(sort));
        if (offset != null && offset > 0) {
            stream = stream.skip(offset);
        }
        if (limit != null && limit >= 0) {
            stream = stream.limit(limit);
        }
        return stream.toList();
    }

    public int countBooks(BookFilter filter) {
        return (int) filtered(filter).count();
    }

    private Stream<Book> filtered(BookFilter filter) {
        Stream<Book> stream = books.values().stream();
        if (filter == null) {
            return stream;
        }
        if (filter.titleContains() != null && !filter.titleContains().isBlank()) {
            String needle = filter.titleContains().toLowerCase(Locale.ROOT);
            stream = stream.filter(book -> book.title().toLowerCase(Locale.ROOT).contains(needle));
        }
        if (filter.authorId() != null) {
            stream = stream.filter(book -> filter.authorId().equals(book.authorId()));
        }
        if (filter.minPrice() != null) {
            stream = stream.filter(book -> book.price() >= filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            stream = stream.filter(book -> book.price() <= filter.maxPrice());
        }
        if (filter.inStock() != null) {
            stream = stream.filter(book -> filter.inStock() == (book.stock() > 0));
        }
        return stream;
    }

    private Comparator<Book> comparator(BookSort sort) {
        Comparator<Book> byId = Comparator.comparing(Book::id);
        Comparator<Book> byTitle = Comparator.comparing(Book::title, String.CASE_INSENSITIVE_ORDER);
        Comparator<Book> byPrice = Comparator.comparingDouble(Book::price);
        Comparator<Book> byStock = Comparator.comparingInt(Book::stock);

        BookSort effective = sort == null ? BookSort.TITLE_ASC : sort;
        return switch (effective) {
            case TITLE_ASC -> byTitle.thenComparing(byId);
            case TITLE_DESC -> byTitle.reversed().thenComparing(byId);
            case PRICE_ASC -> byPrice.thenComparing(byId);
            case PRICE_DESC -> byPrice.reversed().thenComparing(byId);
            case STOCK_ASC -> byStock.thenComparing(byId);
            case STOCK_DESC -> byStock.reversed().thenComparing(byId);
        };
    }

    /**
     * Batch loader solving the N+1 problem: fetches all authors for a list of books in 1 operation.
     */
    public Map<Book, Author> getAuthorsForBooks(List<Book> bookList) {
        log.info("[GRAPHQL-BATCH-MAPPING] Batch loading authors for {} books in a SINGLE operation (Preventing N+1!)",
                bookList.size());

        Map<Book, Author> result = new HashMap<>();
        for (Book book : bookList) {
            Author author = authors.get(book.authorId());
            if (author != null) {
                result.put(book, author);
            }
        }
        return result;
    }

    /**
     * Batch loader solving the N+1 problem in the reverse direction: fetches all books for a list of
     * authors in 1 operation. Authors without books map to an empty list so the non-null schema list is satisfied.
     */
    public Map<Author, List<Book>> getBooksForAuthors(List<Author> authorList) {
        log.info("[GRAPHQL-BATCH-MAPPING] Batch loading books for {} authors in a SINGLE operation (Preventing N+1!)",
                authorList.size());

        Map<String, List<Book>> booksByAuthorId = books.values().stream()
                .collect(Collectors.groupingBy(Book::authorId));

        Map<Author, List<Book>> result = new HashMap<>();
        for (Author author : authorList) {
            result.put(author, booksByAuthorId.getOrDefault(author.id(), List.of()));
        }
        return result;
    }

    public Book saveBook(String title, int pages, double price, int stock, String authorId) {
        String id = "book-" + (books.size() + 1);
        Book book = new Book(id, title, pages, price, stock, authorId);
        books.put(id, book);
        return book;
    }

    public Book updateStock(String bookId, int newStock) {
        Book existing = books.get(bookId);
        if (existing == null) {
            throw new IllegalArgumentException("Book not found: " + bookId);
        }
        Book updated = new Book(existing.id(), existing.title(), existing.pages(), existing.price(), newStock, existing.authorId());
        books.put(bookId, updated);
        return updated;
    }
}
