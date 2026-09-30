package com.example.graphql;

import com.example.graphql.events.BookEventPublisher;
import com.example.graphql.model.Author;
import com.example.graphql.model.Book;
import com.example.graphql.repository.CatalogDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CatalogDataServiceTest {

    private CatalogDataService service;

    @BeforeEach
    void setUp() {
        service = new CatalogDataService(new BookEventPublisher());
    }

    @Test
    void testGetAllBooks() {
        List<Book> books = service.getAllBooks();
        assertFalse(books.isEmpty());
        assertTrue(books.stream().anyMatch(b -> b.title().equals("Effective Java")));
    }

    @Test
    void testBatchAuthorLoading() {
        List<Book> books = service.getAllBooks();
        Map<Book, Author> authorMap = service.getAuthorsForBooks(books);

        assertEquals(books.size(), authorMap.size());
        Book effectiveJava = books.stream().filter(b -> b.title().equals("Effective Java")).findFirst().orElseThrow();
        assertEquals("Joshua Bloch", authorMap.get(effectiveJava).name());
    }

    @Test
    void testSaveBook() {
        Book saved = service.saveBook("Spring in Action", 520, 49.99, 100, "author-1");
        assertNotNull(saved.id());
        assertEquals("Spring in Action", saved.title());
    }
}
