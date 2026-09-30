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
10. **Authorization**: JWT bearer tokens issued by a public `login` mutation, with `@PreAuthorize` rules on writes and on sensitive fields.
11. **More connections & scalar input**: Cursor connections for authors and publications, and `DateTime` used as a query argument.
12. **Error-as-data**: An `addBookResult` mutation returning `Book | ValidationFailed`, so failures arrive as data instead of GraphQL errors.
13. **Persisted queries & WebSocket auth**: Apollo-style automatic persisted queries, and bearer-token authentication for subscriptions.
14. **Persistence**: H2 + Flyway migrations behind JDBC repositories; filtering, sorting, and paging for books run in SQL, and the batch loaders issue a single `IN (…)` query.
15. **Schema directives**: A custom `@auth(requires: Role)` directive implemented with `SchemaDirectiveWiring`.

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
- **`AuthorConnection authorConnection(Integer first, String after)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Cursor pagination over authors, ordered by name.
- **`PublicationConnection publicationConnection(Integer first, String after)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Cursor pagination over every publication; edges carry the `Publication` interface, so clients page books and magazines together with inline fragments.
- **`List<Magazine> magazinesPublishedAfter(Instant since)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves `magazinesPublishedAfter(since: DateTime!)`, the one place a custom scalar is used as an argument (exercising `parseValue`/`parseLiteral`).
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
- **`double costPrice(Book book)`**:
  - *Annotation*: `@SchemaMapping(typeName = "Book", field = "costPrice")`, guarded by the schema's `@auth(requires: ADMIN)` directive.
  - *Operation*: Field-level authorization declared in the SDL and enforced by `AuthDirectiveWiring`, which wraps this field's data fetcher with a role check. Because the field is non-null, a denied read also demonstrates non-null error propagation.
- **`String email(Author author)`**:
  - *Annotation*: `@SchemaMapping(typeName = "Author", field = "email")` + `@PreAuthorize("isAuthenticated()")`.
  - *Operation*: Field-level authorization requiring any authenticated caller.
- **`Book addBook(@Argument BookInput input)`**:
  - *Annotation*: `@MutationMapping`.
  - *Operation*: Validates and saves a new book, then publishes a `bookAdded` event.
- **`Object addBookResult(@Argument BookInput input)`**:
  - *Annotation*: `@MutationMapping`.
  - *Operation*: The error-as-data counterpart of `addBook`. Returns the created `Book`, or a `ValidationFailed` when `InvalidBookInputException` (carrying the offending `field`) or `AuthorNotFoundException` is caught — so the client handles both union members and sees no `errors` entry.
- **Authorization on writes**:
  - `@PreAuthorize("isAuthenticated()")` on `addBook`, `addBookResult`, `updateBook`, and `updateStock`; `@PreAuthorize("hasRole('ADMIN')")` on `deleteBook`. `Book.costPrice` is guarded by the `@auth` schema directive instead, so exactly one mechanism applies per field.
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
- **`BookConnection authorBookConnection(Author author, Integer first, String after)`**:
  - *Annotation*: `@SchemaMapping(typeName = "Author", field = "bookConnection")`.
  - *Operation*: Per-author cursor pagination. It takes arguments, so it cannot be batch-resolved — the contrast with the batched `books` field above is deliberate.
- **`Flux<Book> bookAdded(GraphQLContext context)`**:
  - *Annotation*: `@SubscriptionMapping`.
  - *Operation*: Streams newly created books to subscribed clients over WebSocket, backed by `BookEventPublisher`. Requires an authenticated connection: the `WebSocketAuthInterceptor` publishes the authentication into the GraphQL context, and this method rejects the subscription when it is absent.

---

### B. Data Repository Layer

#### [`CatalogDataService.java`](file:///sdcard/Download/termux/spring-graphql-showcase/src/main/java/com/example/graphql/repository/CatalogDataService.java)
- **`List<Book> findBooks(BookFilter filter, BookSort sort, Integer limit, Integer offset)`**:
  - *Operation*: Applies the filter, then a deterministic sort (default `TITLE_ASC`, always with an ID tiebreaker), then `skip(offset)` and `limit(limit)`. Sorting before paging keeps results stable despite the unordered backing map.
- **`int countBooks(BookFilter filter)`**:
  - *Operation*: Counts the books matching a filter, sharing the same filter predicate as `findBooks`.
- **`BookConnection bookConnection(Integer first, String after, BookFilter filter, BookSort sort)`**:
  - *Operation*: Reuses the same filter/sort pipeline, then slices a page. Cursors are Base64 of `offset:<n>` where `n` is the 1-based edge position, so `after` resumes at the following offset; an unparseable cursor falls back to the start. `pageInfo` reports `hasNextPage`/`hasPreviousPage` plus the start and end cursors.
- **`AuthorConnection authorConnection(Integer first, String after)`** / **`PublicationConnection publicationConnection(Integer first, String after)`**:
  - *Operation*: The same cursor maths over authors (ordered by name) and over all publications (ordered by title), sharing the private `page(...)` helper and cursor codec.
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
- **`List<Magazine> magazinesPublishedAfter(Instant since)`**:
  - *Operation*: Filters magazines at or after `since`, ordered by publication date.
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

#### `SecurityConfig` / `JwtProperties`
- *Annotation*: `@Configuration @EnableMethodSecurity`; settings under `security.jwt`.
- *Operation*: Builds the HMAC-SHA256 `SecretKey`, a `NimbusJwtDecoder` (resource server validation) and a `NimbusJwtEncoder` (token minting), an in-memory `UserDetailsService`, and a `JwtAuthenticationConverter` that maps the `roles` claim to `ROLE_*` authorities. The filter chain disables CSRF and permits `/graphql`, `/graphiql`, and `/actuator/**`; authorization is then enforced per resolver by `@PreAuthorize`.

#### `AuthController`
- *Annotation*: `@Controller` with a `@MutationMapping`.
- *Operation*: Resolves the public `login(username, password)` mutation: authenticates through the `AuthenticationManager`, then mints a JWT carrying `sub`, `roles`, and an expiry, returning an `AuthPayload`.

#### `WebSocketAuthInterceptor`
- *Annotation*: `@Component implementing WebSocketGraphQlInterceptor` (the single WebSocket interceptor in the chain).
- *Operation*: Validates the bearer token from the WebSocket handshake with the `JwtDecoder` and writes the resulting `JwtAuthenticationToken` into the GraphQL context via `configureExecutionInput`, where `bookAdded` picks it up. Subscriptions bypass `@PreAuthorize` because WebSocket messages run outside the servlet security context.

#### `PersistedQueryInterceptor` / `PersistedQueryStore`
- *Annotation*: `@Component implementing WebGraphQlInterceptor` plus a bounded in-memory store.
- *Operation*: Implements Apollo-style APQ. A document sent with `extensions.persistedQuery.sha256Hash` is registered (after verifying the hash); a later hash-only request has its document substituted via `configureExecutionInput`; an unknown hash is reported as `PersistedQueryNotFound`, and a hash that does not match its document is rejected.

#### `SubscriptionExceptionResolver`
- *Annotation*: `@Bean` in `SecurityConfig`.
- *Operation*: Subscription failures are resolved separately from data fetcher exceptions, so the `AccessDeniedException` → UNAUTHORIZED mapping is registered here as well; otherwise a denied subscription surfaces as an opaque INTERNAL_ERROR.

---

## 3. Persistence Layer

`CatalogDataService` keeps its public API but now delegates to three repositories, so the controller and every resolver are unchanged while the storage became real.

#### `BookRepository`, `AuthorRepository`, `MagazineRepository`
- *Annotation*: `@Repository`, backed by `NamedParameterJdbcTemplate`.
- *Operation*: Map rows onto the existing `Book` / `Author` / `Magazine` records. `BookRepository.findAll` assembles its `WHERE`, `ORDER BY`, `LIMIT`, and `OFFSET` from the `BookFilter` / `BookSort` arguments, and `AuthorRepository.findByIds` / `BookRepository.findByAuthorIds` are the single `IN (…)` statements that back the two batch loaders.

#### `V1__catalog_schema.sql` / `V2__seed_catalog.sql`
- *Operation*: Flyway creates the `authors`, `books`, and `magazines` tables plus `book_seq` / `magazine_seq` sequences, then seeds exactly the data the showcase expects. Ids are formatted as `book-<n>` from a sequence so the public API shape (and every doc example) is unchanged.
