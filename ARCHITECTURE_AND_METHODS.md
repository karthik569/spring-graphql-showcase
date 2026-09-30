# Spring for GraphQL - In-Depth Architecture & Method Guide 🌌

This document provides a comprehensive, method-by-method technical deep dive into `spring-graphql-showcase`. It details the Schema-First design approach, query and mutation mappings, filtering/sorting/pagination, typed error handling, subscriptions, and the DataLoader `@BatchMapping` implementation solving the N+1 problem in both directions.

---

## 1. Project Overview & GraphQL Architecture

The application runs on port `8086` using **Spring for GraphQL** and provides an interactive GraphiQL IDE at `http://localhost:8086/graphiql`. It demonstrates:
1. **Schema-First Design**: Strict GraphQL contract definitions in `src/main/resources/graphql/schema.graphqls`.
2. **`@QueryMapping` & `@MutationMapping`**: Type-safe method bindings to GraphQL schema operations.
3. **`@BatchMapping` with DataLoader**: Solving the $N+1$ query problem by batch-resolving relationships in a single round-trip, in both directions (`Book.author` and `Author.books`).
4. **Filtering, sorting & pagination**: `BookFilter` input and `BookSort` enum arguments, offset paging, and a `bookCount` total.
5. **Typed errors**: A `DataFetcherExceptionResolverAdapter` mapping domain exceptions to NOT_FOUND / BAD_REQUEST classifications.
6. **Subscriptions**: A `bookAdded` stream pushed to clients over WebSocket.
7. **Interfaces, unions & custom scalars**: A `Publication` interface implemented by `Book` and `Magazine`, a `SearchResult` union, and hand-written `DateTime` and `URL` scalars.
8. **Cursor connections**: Relay-style pagination with `edges`, opaque cursors, and `pageInfo`.
9. **Hardening & observability**: Query depth, complexity, and length limits plus request logging and Micrometer metrics.

```
 [GraphQL Client / GraphiQL IDE]
                │
                │ POST /graphql (Query: { books { title author { name } } })
                ▼
   [BookGraphQLController]
        │
        ├──► @QueryMapping books() ──► Resolves 5 Books
        │
        └──► @BatchMapping author() ──► DataLoader collects all 5 Book instances
                                       and executes a SINGLE batch lookup for authors
```

---

## 2. In-Depth Class & Method Breakdown

### A. GraphQL Controller Layer

#### [`BookGraphQLController.java`](file:///sdcard/Download/termux/spring-graphql-showcase/src/main/java/com/example/graphql/controller/BookGraphQLController.java)
- **`List<Book> books(BookFilter filter, BookSort sort, Integer limit, Integer offset)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves `books(filter: BookFilter, sort: BookSort, limit: Int, offset: Int): [Book!]!` via `catalogService.findBooks(...)`. Every argument is optional, so the bare `books` query still returns the whole catalog.
- **`int bookCount(BookFilter filter)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves `bookCount(filter: BookFilter): Int!` for pagination totals.
- **`BookConnection bookConnection(Integer first, String after, BookFilter filter, BookSort sort)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves `bookConnection(first: Int, after: String, ...): BookConnection!`, delegating to `catalogService.bookConnection(...)` for Relay-style cursor paging.
- **`Optional<Book> bookById(@Argument String id)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves `bookById(id: ID!): Book` by querying the catalog for the specified ID.
- **`List<Author> authors()`** / **`Optional<Author> authorById(@Argument String id)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves author queries.
- **`List<Publication> publications()`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves `publications: [Publication!]!`. The returned list mixes `Book` and `Magazine`; graphql-java resolves the concrete type from the runtime class simple name, so clients use inline fragments.
- **`Optional<Magazine> magazineById(@Argument String id)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves `magazineById(id: ID!): Magazine`, including the `DateTime` and `URL` custom scalar fields.
- **`List<Object> search(@Argument String text)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves `search(text: String!): [SearchResult!]!`, returning books, magazines, and authors whose title or name contains the text.
- **`Book addBook(@Argument BookInput input)`**:
  - *Annotation*: `@MutationMapping`.
  - *Operation*: Validates and saves a new book, then publishes a `bookAdded` event.
- **`Book updateBook(@Argument String id, @Argument BookUpdateInput input)`**:
  - *Annotation*: `@MutationMapping`.
  - *Operation*: Resolves `updateBook(id: ID!, input: BookUpdateInput!): Book!` as a partial patch — each nullable input field falls back to the existing value.
- **`boolean deleteBook(@Argument String id)`**:
  - *Annotation*: `@MutationMapping`.
  - *Operation*: Resolves `deleteBook(id: ID!): Boolean!`, throwing `BookNotFoundException` (classified NOT_FOUND) when the book does not exist.
- **`Book updateStock(@Argument String id, @Argument int stock)`**:
  - *Annotation*: `@MutationMapping` (deprecated).
  - *Operation*: Legacy stock updater, superseded by `updateBook` and marked `@deprecated` in the schema.
- **`Map<Book, Author> author(List<Book> books)`**:
  - *Annotation*: `@BatchMapping(typeName = "Book", field = "author")`.
  - *N+1 Solution Mechanics*: When a client requests `query { books { id title author { name } } }`, standard GraphQL engines invoke the author resolver $N$ times (once per book). Spring for GraphQL collects all $N$ book instances into a list and calls this single method once, resolving all authors in a single map operation.
- **`Map<Author, List<Book>> books(List<Author> authors)`**:
  - *Annotation*: `@BatchMapping(typeName = "Author", field = "books")`.
  - *Reverse N+1 Solution*: The mirror image. For `query { authors { books { title } } }` the engine collects every parent author and calls this method once with the whole list. Authors without books map to an empty list, so the non-null `[Book!]!` schema type is always satisfied.
- **`Flux<Book> bookAdded()`**:
  - *Annotation*: `@SubscriptionMapping`.
  - *Operation*: Streams newly created books to subscribed clients over WebSocket, backed by `BookEventPublisher`.

---

### B. Data Repository Layer

#### [`CatalogDataService.java`](file:///sdcard/Download/termux/spring-graphql-showcase/src/main/java/com/example/graphql/repository/CatalogDataService.java)
- **`List<Book> findBooks(BookFilter filter, BookSort sort, Integer limit, Integer offset)`**:
  - *Operation*: Applies the filter, then a deterministic sort (default `TITLE_ASC`, always with an ID tiebreaker), then `skip(offset)` and `limit(limit)`. Sorting before paging keeps results stable despite the unordered backing map.
- **`int countBooks(BookFilter filter)`**:
  - *Operation*: Counts the books matching a filter, sharing the same filter predicate as `findBooks`.
- **`BookConnection bookConnection(Integer first, String after, BookFilter filter, BookSort sort)`**:
  - *Operation*: Reuses the same filter/sort pipeline, then slices a page. Cursors are Base64 of `offset:<n>` where `n` is the 1-based edge position, so `after` resumes at the following offset; an unparseable cursor falls back to the start. `pageInfo` reports `hasNextPage`/`hasPreviousPage` plus the start and end cursors.
- **`Map<Book, Author> getAuthorsForBooks(List<Book> books)`**:
  - *Inputs*: `List<Book>` requested in the current query.
  - *Operation*: Collects unique author IDs from the books, executes a single bulk author lookup, and maps each `Book` key to its corresponding `Author` value.
- **`Map<Author, List<Book>> getBooksForAuthors(List<Author> authors)`**:
  - *Operation*: The reverse batch loader. Groups all books by author ID in one pass, then maps each requested author to its books (an empty list when the author has none).
- **`Book saveBook(String title, int pages, double price, int stock, String authorId)`**:
  - *Operation*: Validates the input, persists a new book in memory with an auto-generated ID (`book-N`), and publishes a `bookAdded` event.
- **`Book updateBook(String id, BookUpdateInput input)`**:
  - *Operation*: Partial update; throws `BookNotFoundException` when the ID is unknown and re-validates the merged result.
- **`Book updateStock(String id, int stock)`**:
  - *Operation*: Updates the stock level for a book, throwing `BookNotFoundException` when the ID is unknown.
- **`boolean deleteBook(String id)`**:
  - *Operation*: Removes a book, throwing `BookNotFoundException` when the ID is unknown.
- **`Optional<Magazine> getMagazineById(String id)`** / **`List<Publication> getPublications()`**:
  - *Operation*: Magazines live in their own map; `getPublications()` returns books and magazines together as `Publication` values, which backs the GraphQL interface.
- **`List<Object> search(String text)`**:
  - *Operation*: Case-insensitive match across book titles, magazine titles, and author names; the heterogeneous result list backs the `SearchResult` union.
- **`validateBook(...)`**:
  - *Operation*: Shared guard raising `InvalidBookInputException` (blank title, non-positive pages, negative price/stock) or `AuthorNotFoundException` (unknown author ID).

---

### C. Cross-Cutting Components

#### `GraphQlExceptionResolver`
- *Annotation*: `@Component`, extending `DataFetcherExceptionResolverAdapter`.
- *Operation*: Maps domain exceptions onto typed GraphQL errors — `BookNotFoundException` / `AuthorNotFoundException` → `NOT_FOUND`, `InvalidBookInputException` → `BAD_REQUEST` — so clients receive a machine-readable `extensions.classification` instead of an opaque `INTERNAL_ERROR`.

#### `BookEventPublisher`
- *Annotation*: `@Component`.
- *Operation*: Holds a `Sinks.Many<Book>` multicast sink. `publish(Book)` feeds it from the create path; `stream()` exposes it as a `Flux<Book>` that the `bookAdded` subscription forwards to WebSocket clients.

#### `GraphQlScalarConfig`
- *Annotation*: `@Configuration` implementing `RuntimeWiringConfigurer`.
- *Operation*: Registers the hand-written `DateTime` (an ISO-8601 `Instant`) and `URL` (a `java.net.URI`) scalars, each with a `Coercing` that serializes to a string and rejects invalid input with a coercion exception.

#### `GraphQlLimitsConfiguration` / `MaxQueryLengthInstrumentation`
- *Annotation*: `@Configuration` exposing `Instrumentation` beans; Spring Boot collects them onto the `GraphQlSource`.
- *Operation*: Enforces `MaxQueryDepthInstrumentation`, `MaxQueryComplexityInstrumentation`, and a custom raw-length check. Limits come from `GraphQlLimitsProperties` (`graphql.limits` in `application.yml`).

#### `GraphQlRequestInterceptor`
- *Annotation*: `@Component` implementing `WebGraphQlInterceptor`.
- *Operation*: Times every GraphQL request, logs the operation with its duration and outcome, and records a Micrometer timer named `graphql.request.duration` tagged by `operation` and `outcome`. Spring Boot separately auto-instruments GraphQL requests as `graphql.request`.
