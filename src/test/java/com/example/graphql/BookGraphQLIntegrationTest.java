package com.example.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
class BookGraphQLIntegrationTest {

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Autowired
    private WebTestClient webTestClient;

    record AuthorView(String id, String name, String country) {
    }

    record BookView(String id, String title, Integer pages, Double price, Integer stock, AuthorView author) {
    }

    record BookSummary(String id, String title, Integer stock) {
    }

    @Test
    void graphQlEndpointSpeaksJsonOverHttp() {
        webTestClient.post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", "{ books { id title } }"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.data.books").isArray()
                .jsonPath("$.errors").doesNotExist();
    }

    @Test
    void booksQueryReturnsEveryBookWithItsAuthor() {
        List<BookView> books = graphQlTester.document("""
                        query {
                          books {
                            id
                            title
                            pages
                            price
                            stock
                            author { id name country }
                          }
                        }
                        """)
                .execute()
                .path("books")
                .entityList(BookView.class)
                .get();

        assertThat(books).hasSizeGreaterThanOrEqualTo(4);
        assertThat(books).extracting(BookView::title)
                .contains("Effective Java", "Refactoring", "Clean Code", "Java Puzzlers");
        assertThat(books).allSatisfy(book -> assertThat(book.author()).isNotNull());
        assertThat(books).extracting(book -> book.author().name())
                .contains("Joshua Bloch", "Martin Fowler", "Robert C. Martin");
    }

    @Test
    void bookByIdReturnsTheRequestedBook() {
        BookView book = graphQlTester.document("""
                        query ($id: ID!) {
                          bookById(id: $id) {
                            id
                            title
                            pages
                            price
                            stock
                            author { id name country }
                          }
                        }
                        """)
                .variable("id", "book-1")
                .execute()
                .path("bookById")
                .entity(BookView.class)
                .get();

        assertThat(book.id()).isEqualTo("book-1");
        assertThat(book.title()).isEqualTo("Effective Java");
        assertThat(book.pages()).isEqualTo(416);
        assertThat(book.author().name()).isEqualTo("Joshua Bloch");
    }

    @Test
    void bookByIdReturnsNullWhenBookDoesNotExist() {
        graphQlTester.document("""
                        query ($id: ID!) {
                          bookById(id: $id) { id title }
                        }
                        """)
                .variable("id", "does-not-exist")
                .execute()
                .path("bookById")
                .valueIsNull();
    }

    @Test
    void authorsQueryReturnsEveryAuthor() {
        List<AuthorView> authors = graphQlTester.document("""
                        query { authors { id name country } }
                        """)
                .execute()
                .path("authors")
                .entityList(AuthorView.class)
                .get();

        assertThat(authors).extracting(AuthorView::name)
                .containsExactlyInAnyOrder("Joshua Bloch", "Martin Fowler", "Robert C. Martin");
    }

    @Test
    void authorByIdReturnsTheRequestedAuthor() {
        AuthorView author = graphQlTester.document("""
                        query ($id: ID!) {
                          authorById(id: $id) { id name country }
                        }
                        """)
                .variable("id", "author-2")
                .execute()
                .path("authorById")
                .entity(AuthorView.class)
                .get();

        assertThat(author.name()).isEqualTo("Martin Fowler");
        assertThat(author.country()).isEqualTo("UK");
    }

    @Test
    void authorByIdReturnsNullWhenAuthorDoesNotExist() {
        graphQlTester.document("""
                        query ($id: ID!) {
                          authorById(id: $id) { id name }
                        }
                        """)
                .variable("id", "does-not-exist")
                .execute()
                .path("authorById")
                .valueIsNull();
    }

    @Test
    void addBookMutationCreatesAQueryableBook() {
        BookView created = graphQlTester.document("""
                        mutation ($input: BookInput!) {
                          addBook(input: $input) {
                            id
                            title
                            pages
                            price
                            stock
                            author { name }
                          }
                        }
                        """)
                .variable("input", Map.of(
                        "title", "Cloud Native Java",
                        "pages", 450,
                        "price", 59.0,
                        "stock", 12,
                        "authorId", "author-1"))
                .execute()
                .path("addBook")
                .entity(BookView.class)
                .get();

        assertThat(created.id()).startsWith("book-");
        assertThat(created.title()).isEqualTo("Cloud Native Java");
        assertThat(created.pages()).isEqualTo(450);
        assertThat(created.stock()).isEqualTo(12);
        assertThat(created.author().name()).isEqualTo("Joshua Bloch");

        BookSummary fetched = graphQlTester.document("""
                        query ($id: ID!) {
                          bookById(id: $id) { id title stock }
                        }
                        """)
                .variable("id", created.id())
                .execute()
                .path("bookById")
                .entity(BookSummary.class)
                .get();

        assertThat(fetched.title()).isEqualTo("Cloud Native Java");
    }

    @Test
    void updateStockMutationChangesAndPersistsStock() {
        Integer updatedStock = graphQlTester.document("""
                        mutation ($id: ID!, $stock: Int!) {
                          updateStock(id: $id, stock: $stock) { id title stock }
                        }
                        """)
                .variable("id", "book-2")
                .variable("stock", 123)
                .execute()
                .path("updateStock.stock")
                .entity(Integer.class)
                .get();

        assertThat(updatedStock).isEqualTo(123);

        Integer reloadedStock = graphQlTester.document("""
                        query ($id: ID!) {
                          bookById(id: $id) { id stock }
                        }
                        """)
                .variable("id", "book-2")
                .execute()
                .path("bookById.stock")
                .entity(Integer.class)
                .get();

        assertThat(reloadedStock).isEqualTo(123);
    }

    @Test
    void updateStockMutationOnUnknownBookReturnsGraphQLError() {
        graphQlTester.document("""
                        mutation ($id: ID!, $stock: Int!) {
                          updateStock(id: $id, stock: $stock) { id }
                        }
                        """)
                .variable("id", "does-not-exist")
                .variable("stock", 1)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }
}
