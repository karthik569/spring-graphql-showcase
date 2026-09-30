# Spring for GraphQL Schema-First Showcase 🌌

A production-grade GraphQL server implementing **Schema-First Design**, `@QueryMapping`, `@MutationMapping`, `@SubscriptionMapping`, and `@BatchMapping` with DataLoader to eliminate the $N+1$ query problem. It also demonstrates filtering, sorting and pagination, interfaces and unions, custom scalars, Relay-style cursor connections, typed errors and error-as-data result unions, real-time updates over WebSocket, query limits with request metrics, automatic persisted queries, and JWT bearer authorization with operation- and field-level rules.

> 📖 **New to Spring GraphQL or this repo?** Start with the [Getting Started guide](GETTING_STARTED.html).

---

## 🌟 Comprehensive Method-by-Method Breakdown

### 1. [`BookGraphQLController`](file:///sdcard/Download/termux/spring-graphql-showcase/src/main/java/com/example/graphql/controller/BookGraphQLController.java)
- **`List<Book> books(BookFilter, BookSort, Integer limit, Integer offset)`**: `@QueryMapping`; resolves the root `books` query with optional filtering (title substring, author, price range, stock), sorting via the `BookSort` enum, and offset pagination.
- **`int bookCount(BookFilter)`**: `@QueryMapping`; number of books matching a filter, for pagination totals.
- **`BookConnection bookConnection(Integer first, String after, BookFilter, BookSort)`**: `@QueryMapping`; Relay-style cursor pagination with `edges`, opaque cursors, `pageInfo`, and `totalCount`.
- **`Optional<Book> bookById(String id)`**: `@QueryMapping`; resolves a single book by identifier.
- **`List<Publication> publications()` / `Optional<Magazine> magazineById(String id)`**: `@QueryMapping`; resolves books and magazines polymorphically through the `Publication` interface.
- **`List<Object> search(String text)`**: `@QueryMapping`; returns the `SearchResult` union of books, magazines, and authors.
- **`AuthorConnection authorConnection(Integer first, String after)` / `PublicationConnection publicationConnection(Integer first, String after)`**: `@QueryMapping`; cursor pagination for authors and for every publication.
- **`List<Magazine> magazinesPublishedAfter(Instant since)`**: `@QueryMapping`; filters magazines using the `DateTime` custom scalar as an argument.
- **`List<Author> authors()` / `Optional<Author> authorById(String id)`**: `@QueryMapping`; resolve author queries.
- **`Book addBook(BookInput input)`**: `@MutationMapping`; validates and creates a new book, then publishes a `bookAdded` event.
- **`Book updateBook(String id, BookUpdateInput input)`**: `@MutationMapping`; partial update — only the fields present in the input change.
- **`boolean deleteBook(String id)`**: `@MutationMapping`; removes a book, or raises a NOT_FOUND error.
- **`Book updateStock(String id, int stock)`**: `@MutationMapping`; legacy stock updater, marked `@deprecated` in the schema in favour of `updateBook`.
- **`Map<Book, Author> author(List<Book> books)`**: `@BatchMapping`; **solves the N+1 problem** by batching author lookups across books into a single DataLoader round-trip.
- **`Map<Author, List<Book>> books(List<Author> authors)`**: `@BatchMapping`; **solves the N+1 problem in reverse**, batching book lookups across authors.
- **`Flux<Book> bookAdded(GraphQlContext)`**: `@SubscriptionMapping`; streams newly created books over WebSocket and requires an authenticated connection.
- **`GraphQlScalarConfig`**: registers the hand-written `DateTime` (ISO-8601) and `URL` custom scalars.
- **`GraphQlLimitsConfiguration`**: `Instrumentation` beans enforcing max query depth, complexity, and length (see `graphql.limits`).
- **`GraphQlRequestInterceptor`**: logs each operation with its duration and records a Micrometer `graphql.request.duration` timer. Spring Boot additionally auto-instruments GraphQL requests as `graphql.request`.
- **`AuthPayload login(String username, String password)`** (`AuthController`): `@MutationMapping`; the one public write, issuing a signed JWT.
- **`double costPrice(Book)` / `String email(Author)`**: `@SchemaMapping`; computed fields guarded by `@PreAuthorize` (ADMIN and any authenticated user respectively).
- **`GraphQlExceptionResolver`**: maps `BookNotFoundException` / `AuthorNotFoundException` to NOT_FOUND, `InvalidBookInputException` to BAD_REQUEST, and access failures to UNAUTHORIZED / FORBIDDEN.
- **`Object addBookResult(BookInput input)`**: `@MutationMapping`; the error-as-data counterpart of `addBook`, returning `Book | ValidationFailed` as data rather than a GraphQL error.
- **`WebSocketAuthInterceptor`**: validates the bearer token on the WebSocket handshake and publishes the authentication into the GraphQL context, which `bookAdded` requires.
- **`PersistedQueryInterceptor` / `PersistedQueryStore`**: Apollo-style APQ — register a document under its hash, then resolve it from the hash alone.

Authorization rules: reads are public; `addBook`, `addBookResult`, `updateBook`, and `updateStock` require an authenticated user; `deleteBook` requires the ADMIN role; the `bookAdded` subscription requires an authenticated WebSocket connection.

> **Not supported on this stack**: `@defer`/`@stream` (Spring for GraphQL 1.3 has no incremental-delivery transport) and GraphQL over HTTP GET (the transport is POST-only).

---

## 🚀 How to Run in Termux

### 1. Build and Run Tests
```bash
cd /sdcard/Download/termux/spring-graphql-showcase
mvn clean test
```

### 2. Start Application on Port 8086
```bash
mvn spring-boot:run
```

---

## 🧪 Interactive API Testing & cURL Commands

### 1. Execute GraphQL Query with Batched Author Resolution
```bash
curl -i -X POST http://localhost:8086/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query { books { id title pages author { id name country } } }"}'
```

---

### 2. Execute GraphQL Query with Filtering & Pagination
```bash
curl -i -X POST http://localhost:8086/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query { books(filter: {authorId: \"author-1\"}, sort: PRICE_ASC, limit: 2) { id title price } bookCount }"}'
```

---

### 3. Execute GraphQL Mutation
```bash
curl -i -X POST http://localhost:8086/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { addBook(input: {title: \"Cloud Native Java\", pages: 450, price: 59.0, stock: 12, authorId: \"author-1\"}) { id title price } }"}'
```

---

### 4. Subscribe to New Books (WebSocket)
```bash
# GraphQL over WebSocket is served at ws://localhost:8086/graphql
# Easiest to try in GraphiQL: http://localhost:8086/graphiql
#
# subscription { bookAdded { id title } }
```

---

### 5. Log In for a JWT, Then Call a Protected Mutation
```bash
# Public login (dev users: user/password and admin/admin)
TOKEN=$(curl -s -X POST http://localhost:8086/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { login(username: \"user\", password: \"password\") { accessToken } }"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# Reads stay public; writes need the bearer token
curl -i -X POST http://localhost:8086/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"query":"mutation { addBook(input: {title: \"Cloud Native Java\", pages: 450, price: 59.0, stock: 12, authorId: \"author-1\"}) { id title } }"}'
```

> **Dev-only settings**: `security.jwt.secret` in `application.yml` is a placeholder and the demo users are in-memory. Override the secret (and replace the user store) before any real deployment.

---

### 6. Errors as Data, and Persisted Queries
```bash
# addBookResult returns the failure as data instead of a GraphQL error
curl -s -X POST http://localhost:8086/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"query":"mutation { addBookResult(input: {title: \"Bad\", pages: 0, price: 1.0, stock: 1, authorId: \"author-1\"}) { __typename ... on Book { id } ... on ValidationFailed { field message } } }"}'

# APQ: register a query under its SHA-256 hash, then call it by hash alone
HASH=$(printf 'query { bookCount }' | sha256sum | cut -d' ' -f1)
curl -s -X POST http://localhost:8086/graphql -H "Content-Type: application/json" \
  -d "{\"query\":\"query { bookCount }\",\"extensions\":{\"persistedQuery\":{\"sha256Hash\":\"$HASH\"}}}"
curl -s -X POST http://localhost:8086/graphql -H "Content-Type: application/json" \
  -d "{\"extensions\":{\"persistedQuery\":{\"sha256Hash\":\"$HASH\"}}}"
```
