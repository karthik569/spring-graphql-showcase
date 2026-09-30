package com.example.graphql;

import com.example.graphql.events.BookEventPublisher;
import com.example.graphql.exception.AuthorNotFoundException;
import com.example.graphql.exception.BookNotFoundException;
import com.example.graphql.exception.InvalidBookInputException;
import com.example.graphql.model.Author;
import com.example.graphql.model.Book;
import com.example.graphql.model.BookConnection;
import com.example.graphql.model.BookFilter;
import com.example.graphql.model.BookSort;
import com.example.graphql.model.BookUpdateInput;
import com.example.graphql.model.Magazine;
import com.example.graphql.model.Publication;
import com.example.graphql.repository.CatalogDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CatalogDataServiceTest {

    private BookEventPublisher publisher;
    private CatalogDataService service;

    @BeforeEach
    void setUp() {
        publisher = new BookEventPublisher();
        service = new CatalogDataService(publisher);
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

    @Test
    void testSaveBookPublishesEventToSubscribers() {
        List<Book> emitted = new ArrayList<>();
        publisher.stream().subscribe(emitted::add);

        Book saved = service.saveBook("Reactive Spring", 300, 39.0, 10, "author-1");

        assertEquals(1, emitted.size());
        assertEquals(saved.id(), emitted.get(0).id());
    }

    @Test
    void testSaveBookRejectsInvalidInput() {
        assertThrows(InvalidBookInputException.class,
                () -> service.saveBook("Too Short", 0, 10.0, 1, "author-1"));
        assertThrows(InvalidBookInputException.class,
                () -> service.saveBook(" ", 100, 10.0, 1, "author-1"));
        assertThrows(InvalidBookInputException.class,
                () -> service.saveBook("Negative Price", 100, -1.0, 1, "author-1"));
    }

    @Test
    void testSaveBookRejectsUnknownAuthor() {
        assertThrows(AuthorNotFoundException.class,
                () -> service.saveBook("Orphan", 100, 10.0, 1, "author-999"));
    }

    @Test
    void testGetBooksForAuthorsGroupsBooksByAuthor() {
        List<Author> authors = service.getAllAuthors();
        Map<Author, List<Book>> booksByAuthor = service.getBooksForAuthors(authors);

        assertEquals(authors.size(), booksByAuthor.size());

        Author bloch = authors.stream().filter(a -> a.name().equals("Joshua Bloch")).findFirst().orElseThrow();
        List<String> titles = booksByAuthor.get(bloch).stream().map(Book::title).toList();
        assertTrue(titles.containsAll(List.of("Effective Java", "Java Puzzlers")));
    }

    @Test
    void testGetBooksForAuthorsReturnsEmptyListWhenAuthorHasNoBooks() {
        Author authorWithoutBooks = new Author("author-999", "Nobody", "Nowhere");

        Map<Author, List<Book>> booksByAuthor = service.getBooksForAuthors(List.of(authorWithoutBooks));

        assertNotNull(booksByAuthor.get(authorWithoutBooks));
        assertTrue(booksByAuthor.get(authorWithoutBooks).isEmpty());
    }

    @Test
    void testFindBooksFiltersByAuthorAndPrice() {
        List<Book> result = service.findBooks(new BookFilter(null, "author-1", 40.0, null, null), null, null, null);

        assertEquals(1, result.size());
        assertEquals("Effective Java", result.get(0).title());
    }

    @Test
    void testFindBooksFiltersByTitleAndStock() {
        List<Book> result = service.findBooks(new BookFilter("clean", null, null, null, true), null, null, null);

        assertEquals(1, result.size());
        assertEquals("Clean Code", result.get(0).title());
    }

    @Test
    void testFindBooksSortsAndPages() {
        List<String> ascending = service.findBooks(null, BookSort.TITLE_ASC, null, null)
                .stream().map(Book::title).toList();
        assertEquals(List.of("Clean Code", "Effective Java", "Java Puzzlers", "Refactoring"), ascending);

        List<String> descending = service.findBooks(null, BookSort.TITLE_DESC, null, null)
                .stream().map(Book::title).toList();
        assertEquals(List.of("Refactoring", "Java Puzzlers", "Effective Java", "Clean Code"), descending);

        List<String> page = service.findBooks(null, BookSort.PRICE_ASC, 2, 1)
                .stream().map(Book::title).toList();
        assertEquals(List.of("Clean Code", "Effective Java"), page);
    }

    @Test
    void testCountBooks() {
        assertEquals(4, service.countBooks(null));
        assertEquals(2, service.countBooks(new BookFilter(null, "author-1", null, null, null)));
        assertEquals(1, service.countBooks(new BookFilter("clean", null, null, null, null)));
    }

    @Test
    void testUpdateBookPatchesOnlyProvidedFields() {
        Book updated = service.updateBook("book-1", new BookUpdateInput(null, null, null, 7, null));

        assertEquals("Effective Java", updated.title());
        assertEquals(416, updated.pages());
        assertEquals(7, updated.stock());
    }

    @Test
    void testUpdateBookThrowsForUnknownBook() {
        assertThrows(BookNotFoundException.class,
                () -> service.updateBook("does-not-exist", new BookUpdateInput(null, null, null, 1, null)));
    }

    @Test
    void testDeleteBookRemovesBook() {
        assertTrue(service.deleteBook("book-4"));
        assertTrue(service.getBookById("book-4").isEmpty());
        assertThrows(BookNotFoundException.class, () -> service.deleteBook("book-4"));
    }

    @Test
    void testUpdateStockThrowsForUnknownBook() {
        assertThrows(BookNotFoundException.class, () -> service.updateStock("does-not-exist", 5));
    }

    @Test
    void testGetPublicationsIncludesBooksAndMagazines() {
        List<Publication> publications = service.getPublications();

        assertEquals(6, publications.size());
        assertTrue(publications.stream().anyMatch(publication -> publication.id().equals("book-1")));
        assertTrue(publications.stream().anyMatch(publication -> publication.id().equals("magazine-1")));
    }

    @Test
    void testGetMagazineById() {
        assertEquals("GraphQL Weekly", service.getMagazineById("magazine-2").orElseThrow().title());
        assertTrue(service.getMagazineById("does-not-exist").isEmpty());
    }

    @Test
    void testSearchMatchesAcrossTypes() {
        List<Object> javaResults = service.search("java");
        assertTrue(javaResults.stream().anyMatch(result -> result instanceof Book));
        assertTrue(javaResults.stream().anyMatch(result -> result instanceof Magazine));
        assertFalse(javaResults.stream().anyMatch(result -> result instanceof Author));

        assertTrue(service.search("a").stream().anyMatch(result -> result instanceof Author));
    }

    @Test
    void testBookConnectionPagesWithCursors() {
        BookConnection firstPage = service.bookConnection(2, null, null, BookSort.TITLE_ASC);

        assertEquals(2, firstPage.edges().size());
        assertEquals(4, firstPage.totalCount());
        assertTrue(firstPage.pageInfo().hasNextPage());
        assertFalse(firstPage.pageInfo().hasPreviousPage());
        assertNotNull(firstPage.pageInfo().endCursor());

        BookConnection secondPage = service.bookConnection(2, firstPage.pageInfo().endCursor(), null, BookSort.TITLE_ASC);

        assertEquals(2, secondPage.edges().size());
        assertTrue(secondPage.pageInfo().hasPreviousPage());
        assertNotEquals(firstPage.edges().get(0).node().id(), secondPage.edges().get(0).node().id());
    }

    @Test
    void testBookConnectionWithUnparseableCursorStartsFromTheBeginning() {
        BookConnection connection = service.bookConnection(1, "not-a-cursor", null, BookSort.TITLE_ASC);

        assertEquals(1, connection.edges().size());
        assertFalse(connection.pageInfo().hasPreviousPage());
    }
}
