package com.example.graphql.repository;

import com.example.graphql.model.Author;
import com.example.graphql.model.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
