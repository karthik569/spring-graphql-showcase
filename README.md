# Spring for GraphQL Schema-First Showcase 🌌

A production-grade GraphQL server implementing **Schema-First Design**, `@QueryMapping`, `@MutationMapping`, `@SubscriptionMapping`, and `@BatchMapping` with DataLoader to eliminate the $N+1$ query problem. It also demonstrates filtering, sorting and offset pagination, typed GraphQL errors, and real-time updates over WebSocket.

> 📖 **New to Spring GraphQL or this repo?** Start with the [Getting Started guide](GETTING_STARTED.html).

---

## 🌟 Comprehensive Method-by-Method Breakdown

### 1. [`BookGraphQLController`](file:///sdcard/Download/termux/spring-graphql-showcase/src/main/java/com/example/graphql/controller/BookGraphQLController.java)
- **`List<Book> books(BookFilter, BookSort, Integer limit, Integer offset)`**: `@QueryMapping`; resolves the root `books` query with optional filtering (title substring, author, price range, stock), sorting via the `BookSort` enum, and offset pagination.
- **`int bookCount(BookFilter)`**: `@QueryMapping`; number of books matching a filter, for pagination totals.
- **`Optional<Book> bookById(String id)`**: `@QueryMapping`; resolves a single book by identifier.
- **`List<Author> authors()` / `Optional<Author> authorById(String id)`**: `@QueryMapping`; resolve author queries.
- **`Book addBook(BookInput input)`**: `@MutationMapping`; validates and creates a new book, then publishes a `bookAdded` event.
- **`Book updateBook(String id, BookUpdateInput input)`**: `@MutationMapping`; partial update — only the fields present in the input change.
- **`boolean deleteBook(String id)`**: `@MutationMapping`; removes a book, or raises a NOT_FOUND error.
- **`Book updateStock(String id, int stock)`**: `@MutationMapping`; legacy stock updater, marked `@deprecated` in the schema in favour of `updateBook`.
- **`Map<Book, Author> author(List<Book> books)`**: `@BatchMapping`; **solves the N+1 problem** by batching author lookups across books into a single DataLoader round-trip.
- **`Map<Author, List<Book>> books(List<Author> authors)`**: `@BatchMapping`; **solves the N+1 problem in reverse**, batching book lookups across authors.
- **`Flux<Book> bookAdded()`**: `@SubscriptionMapping`; streams newly created books to subscribers over WebSocket.
- **`GraphQlExceptionResolver`**: maps `BookNotFoundException` / `AuthorNotFoundException` to NOT_FOUND and `InvalidBookInputException` to BAD_REQUEST.

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
