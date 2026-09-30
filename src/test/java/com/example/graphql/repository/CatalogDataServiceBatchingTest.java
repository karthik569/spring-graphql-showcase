package com.example.graphql.repository;

import com.example.graphql.events.BookEventPublisher;
import com.example.graphql.model.Author;
import com.example.graphql.model.Book;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the batch claim directly: N related rows are resolved with a single repository call, which
 * is the whole point of the DataLoader-based loaders.
 */
@ExtendWith(MockitoExtension.class)
class CatalogDataServiceBatchingTest {

    @Mock
    private BookRepository books;

    @Mock
    private AuthorRepository authors;

    @Mock
    private MagazineRepository magazines;

    @Mock
    private BookEventPublisher publisher;

    @Test
    void resolvesEveryBooksAuthorWithOneQuery() {
        CatalogDataService service = new CatalogDataService(books, authors, magazines, publisher);

        List<Book> bookList = List.of(
                new Book("book-1", "Effective Java", 416, 45.0, 50, "author-1"),
                new Book("book-2", "Java Puzzlers", 312, 35.0, 20, "author-1"));

        when(authors.findByIds(anyCollection()))
                .thenReturn(List.of(new Author("author-1", "Joshua Bloch", "USA")));

        Map<Book, Author> resolved = service.getAuthorsForBooks(bookList);

        assertThat(resolved).hasSize(2);
        verify(authors, times(1)).findByIds(anyCollection());
    }

    @Test
    void resolvesEveryAuthorsBooksWithOneQuery() {
        CatalogDataService service = new CatalogDataService(books, authors, magazines, publisher);

        List<Author> authorList = List.of(
                new Author("author-1", "Joshua Bloch", "USA"),
                new Author("author-2", "Martin Fowler", "UK"));

        when(books.findByAuthorIds(anyCollection()))
                .thenReturn(List.of(new Book("book-1", "Effective Java", 416, 45.0, 50, "author-1")));

        Map<Author, List<Book>> resolved = service.getBooksForAuthors(authorList);

        assertThat(resolved).hasSize(2);
        assertThat(resolved.get(authorList.get(1))).isEmpty();
        verify(books, times(1)).findByAuthorIds(anyCollection());
    }
}
