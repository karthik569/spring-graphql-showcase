package com.example.graphql;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
class BookGraphQLIntegrationTest {

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Autowired
    private WebTestClient webTestClient;

    @LocalServerPort
    private int port;

    @Autowired
    private MeterRegistry meterRegistry;

    record AuthorView(String id, String name, String country) {
    }

    record BookView(String id, String title, Integer pages, Double price, Integer stock, AuthorView author) {
    }

    record BookSummary(String id, String title, Integer stock) {
    }

    record BookTitle(String title) {
    }

    record AuthorWithBooks(String name, List<BookTitle> books) {
    }

    record SubscribedBook(String id, String title) {
    }

    record PublicationView(String id, String title, Integer issueNumber, Integer pages) {
    }

    record MagazineView(String id, String title, Integer issueNumber, String publisher, String publishedOn, String website) {
    }

    record BookNode(String id, String title) {
    }

    record EdgeView(String cursor, BookNode node) {
    }

    record PageInfoView(boolean hasNextPage, boolean hasPreviousPage, String startCursor, String endCursor) {
    }

    record BookConnectionView(List<EdgeView> edges, PageInfoView pageInfo, int totalCount) {
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
    void booksCanBeFiltered() {
        List<String> byTitle = titles("query { books(filter: {titleContains: \"Effective\"}) { title } }");
        assertThat(byTitle).containsExactly("Effective Java");

        List<String> byAuthor = titles("query { books(filter: {authorId: \"author-2\"}) { title } }");
        assertThat(byAuthor).containsExactly("Refactoring");

        List<String> byPrice = titles("query { books(filter: {minPrice: 44.0, maxPrice: 46.0}) { title } }");
        assertThat(byPrice).containsExactly("Effective Java");
    }

    @Test
    void booksCanBeSorted() {
        List<String> ascending = titles("query { books(sort: TITLE_ASC) { title } }");
        List<String> descending = titles("query { books(sort: TITLE_DESC) { title } }");

        assertThat(ascending).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(descending).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER.reversed());
    }

    @Test
    void booksCanBePaged() {
        List<String> all = titles("query { books(sort: TITLE_ASC) { title } }");
        List<String> page = titles("query { books(sort: TITLE_ASC, limit: 2, offset: 1) { title } }");

        assertThat(all).hasSizeGreaterThanOrEqualTo(3);
        assertThat(page).isEqualTo(all.subList(1, 3));
    }

    @Test
    void bookCountMatchesTheFilteredBooksQuery() {
        Integer total = graphQlTester.document("query { bookCount }")
                .execute()
                .path("bookCount")
                .entity(Integer.class)
                .get();

        assertThat(total).isEqualTo(titles("query { books { title } }").size());

        Integer byAuthor = graphQlTester.document("query { bookCount(filter: {authorId: \"author-2\"}) }")
                .execute()
                .path("bookCount")
                .entity(Integer.class)
                .get();

        assertThat(byAuthor).isEqualTo(1);
    }

    @Test
    void authorBooksAreResolvedThroughReverseBatchMapping() {
        List<AuthorWithBooks> authors = graphQlTester.document("""
                        query {
                          authors {
                            name
                            books { title }
                          }
                        }
                        """)
                .execute()
                .path("authors")
                .entityList(AuthorWithBooks.class)
                .get();

        assertThat(authors).extracting(AuthorWithBooks::name)
                .contains("Joshua Bloch", "Martin Fowler", "Robert C. Martin");
        assertThat(authors).allSatisfy(author -> assertThat(author.books()).isNotNull());

        AuthorWithBooks bloch = authors.stream()
                .filter(author -> author.name().equals("Joshua Bloch"))
                .findFirst()
                .orElseThrow();
        assertThat(bloch.books()).extracting(BookTitle::title)
                .contains("Effective Java", "Java Puzzlers");
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
        BookView created = addBook("Cloud Native Java", 450, 59.0, 12, "author-1");

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
    void updateBookMutationPatchesOnlyProvidedFields() {
        BookView created = addBook("Integration Update", 100, 10.0, 5, "author-1");

        BookSummary updated = graphQlTester.document("""
                        mutation ($id: ID!, $input: BookUpdateInput!) {
                          updateBook(id: $id, input: $input) { id title stock }
                        }
                        """)
                .variable("id", created.id())
                .variable("input", Map.of("stock", 0))
                .execute()
                .path("updateBook")
                .entity(BookSummary.class)
                .get();

        assertThat(updated.stock()).isZero();
        assertThat(updated.title()).isEqualTo("Integration Update");

        deleteBook(created.id());
    }

    @Test
    void deleteBookMutationRemovesBookThenReportsNotFound() {
        BookView created = addBook("Doomed", 100, 10.0, 1, "author-1");

        assertThat(deleteBook(created.id())).isTrue();

        graphQlTester.document("""
                        query ($id: ID!) {
                          bookById(id: $id) { id }
                        }
                        """)
                .variable("id", created.id())
                .execute()
                .path("bookById")
                .valueIsNull();

        graphQlTester.document("mutation ($id: ID!) { deleteBook(id: $id) }")
                .variable("id", created.id())
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).isNotEmpty();
                    assertThat(errors.get(0).getErrorType().toString()).isEqualTo("NOT_FOUND");
                });
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
    void updateStockMutationOnUnknownBookIsClassifiedNotFound() {
        graphQlTester.document("""
                        mutation ($id: ID!, $stock: Int!) {
                          updateStock(id: $id, stock: $stock) { id }
                        }
                        """)
                .variable("id", "does-not-exist")
                .variable("stock", 1)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).isNotEmpty();
                    assertThat(errors.get(0).getErrorType().toString()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void invalidBookInputIsClassifiedBadRequest() {
        graphQlTester.document("""
                        mutation {
                          addBook(input: {title: "Bad Book", pages: 0, price: 1.0, stock: 1, authorId: "author-1"}) { id }
                        }
                        """)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).isNotEmpty();
                    assertThat(errors.get(0).getErrorType().toString()).isEqualTo("BAD_REQUEST");
                });
    }

    @Test
    void bookAddedSubscriptionEmitsNewBooks() throws InterruptedException {
        WebSocketGraphQlTester wsTester = WebSocketGraphQlTester
                .builder("http://localhost:" + port + "/graphql", new ReactorNettyWebSocketClient())
                .build();

        BlockingQueue<SubscribedBook> received = new LinkedBlockingQueue<>();
        Disposable subscription = wsTester.document("subscription { bookAdded { id title } }")
                .executeSubscription()
                .toFlux("bookAdded", SubscribedBook.class)
                .subscribe(received::add);

        try {
            Thread.sleep(1000);
            addBook("Reactive Integration", 120, 20.0, 3, "author-1");

            SubscribedBook emitted = received.poll(15, TimeUnit.SECONDS);
            assertThat(emitted).isNotNull();
            assertThat(emitted.title()).isEqualTo("Reactive Integration");
        } finally {
            subscription.dispose();
            wsTester.stop().block();
        }
    }

    @Test
    void publicationsExposeInterfaceTypes() {
        List<PublicationView> publications = graphQlTester.document("""
                        query {
                          publications {
                            id
                            title
                            ... on Magazine { issueNumber }
                            ... on Book { pages }
                          }
                        }
                        """)
                .execute()
                .path("publications")
                .entityList(PublicationView.class)
                .get();

        assertThat(publications).extracting(PublicationView::id)
                .contains("book-1", "magazine-1", "magazine-2");

        PublicationView magazine = publications.stream()
                .filter(publication -> publication.id().equals("magazine-1"))
                .findFirst()
                .orElseThrow();
        assertThat(magazine.issueNumber()).isEqualTo(42);
        assertThat(magazine.pages()).isNull();

        PublicationView book = publications.stream()
                .filter(publication -> publication.id().equals("book-1"))
                .findFirst()
                .orElseThrow();
        assertThat(book.pages()).isEqualTo(416);
        assertThat(book.issueNumber()).isNull();
    }

    @Test
    void searchReturnsUnionMembers() {
        graphQlTester.document("""
                        query ($text: String!) {
                          search(text: $text) {
                            __typename
                            ... on Book { title }
                            ... on Magazine { issueNumber }
                            ... on Author { name }
                          }
                        }
                        """)
                .variable("text", "a")
                .execute()
                .path("search[*].__typename")
                .entityList(String.class)
                .contains("Book", "Magazine", "Author");
    }

    @Test
    void magazineFieldsUseCustomScalars() {
        MagazineView magazine = graphQlTester.document("""
                        query ($id: ID!) {
                          magazineById(id: $id) {
                            id
                            title
                            issueNumber
                            publisher
                            publishedOn
                            website
                          }
                        }
                        """)
                .variable("id", "magazine-1")
                .execute()
                .path("magazineById")
                .entity(MagazineView.class)
                .get();

        assertThat(magazine.publishedOn()).isEqualTo("2024-05-01T00:00:00Z");
        assertThat(magazine.website()).isEqualTo("https://javamagazine.example.com");
    }

    @Test
    void bookConnectionPagesWithCursors() {
        BookConnectionView firstPage = graphQlTester.document("""
                        query {
                          bookConnection(first: 2, sort: TITLE_ASC) {
                            edges { cursor node { id title } }
                            pageInfo { hasNextPage hasPreviousPage startCursor endCursor }
                            totalCount
                          }
                        }
                        """)
                .execute()
                .path("bookConnection")
                .entity(BookConnectionView.class)
                .get();

        assertThat(firstPage.edges()).hasSize(2);
        assertThat(firstPage.pageInfo().hasNextPage()).isTrue();
        assertThat(firstPage.pageInfo().hasPreviousPage()).isFalse();
        assertThat(firstPage.pageInfo().endCursor()).isNotBlank();
        assertThat(firstPage.totalCount()).isGreaterThanOrEqualTo(4);

        BookConnectionView secondPage = graphQlTester.document("""
                        query ($after: String!) {
                          bookConnection(first: 2, after: $after, sort: TITLE_ASC) {
                            edges { cursor node { id } }
                            pageInfo { hasNextPage hasPreviousPage }
                            totalCount
                          }
                        }
                        """)
                .variable("after", firstPage.pageInfo().endCursor())
                .execute()
                .path("bookConnection")
                .entity(BookConnectionView.class)
                .get();

        assertThat(secondPage.edges()).isNotEmpty();
        assertThat(secondPage.pageInfo().hasPreviousPage()).isTrue();
        assertThat(secondPage.edges().get(0).node().id())
                .isNotEqualTo(firstPage.edges().get(0).node().id());
    }

    @Test
    void queriesExceedingMaxDepthAreRejected() {
        String nested = "books { title }";
        for (int i = 0; i < 6; i++) {
            nested = "books { author { " + nested + " } }";
        }

        graphQlTester.document("query { " + nested + " }")
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).isNotEmpty();
                    assertThat(errors.get(0).getMessage()).contains("depth");
                });
    }

    @Test
    void queriesExceedingMaxComplexityAreRejected() {
        StringBuilder document = new StringBuilder("query {\n");
        for (int i = 0; i < 250; i++) {
            document.append("  b").append(i).append(": bookCount\n");
        }
        document.append("}");

        graphQlTester.document(document.toString())
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).isNotEmpty();
                    assertThat(errors.get(0).getMessage()).contains("complexity");
                });
    }

    @Test
    void queriesExceedingMaxLengthAreRejected() {
        String document = "query { bookCount }" + " ".repeat(11000);

        graphQlTester.document(document)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).isNotEmpty();
                    assertThat(errors.get(0).getMessage()).contains("exceeds the maximum");
                });
    }

    @Test
    void requestsAreRecordedInMetrics() {
        graphQlTester.document("query { bookCount }")
                .execute()
                .path("bookCount")
                .entity(Integer.class)
                .get();

        assertThat(meterRegistry.find("graphql.request.duration").timers()).isNotEmpty();
    }

    private List<String> titles(String document) {
        return graphQlTester.document(document)
                .execute()
                .path("books")
                .entityList(BookTitle.class)
                .get()
                .stream()
                .map(BookTitle::title)
                .toList();
    }

    private BookView addBook(String title, int pages, double price, int stock, String authorId) {
        return graphQlTester.document("""
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
                        "title", title,
                        "pages", pages,
                        "price", price,
                        "stock", stock,
                        "authorId", authorId))
                .execute()
                .path("addBook")
                .entity(BookView.class)
                .get();
    }

    private boolean deleteBook(String id) {
        return graphQlTester.document("mutation ($id: ID!) { deleteBook(id: $id) }")
                .variable("id", id)
                .execute()
                .path("deleteBook")
                .entity(Boolean.class)
                .get();
    }
}
