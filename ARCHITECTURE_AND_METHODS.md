# Spring for GraphQL - In-Depth Architecture & Method Guide 🌌

This document provides a comprehensive, method-by-method technical deep dive into `spring-graphql-showcase`. It details the Schema-First design approach, query and mutation mappings, and DataLoader `@BatchMapping` implementation solving the N+1 problem.

---

## 1. Project Overview & GraphQL Architecture

The application runs on port `8086` using **Spring for GraphQL** and provides an interactive GraphiQL IDE at `http://localhost:8086/graphiql`. It demonstrates:
1. **Schema-First Design**: Strict GraphQL contract definitions in `src/main/resources/graphql/schema.graphqls`.
2. **`@QueryMapping` & `@MutationMapping`**: Type-safe method bindings to GraphQL schema operations.
3. **`@BatchMapping` with DataLoader**: Solving the $N+1$ query problem by batch-resolving author relationships across multiple books in a single round-trip.

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
- **`List<Book> books()`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves the root `books: [Book]` GraphQL query, returning all book records from `catalogService.getAllBooks()`.
- **`Optional<Book> bookById(@Argument String id)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves `bookById(id: ID!): Book` by querying the catalog for the specified ID.
- **`List<Author> authors()`** / **`Optional<Author> authorById(@Argument String id)`**:
  - *Annotation*: `@QueryMapping`.
  - *Operation*: Resolves author queries.
- **`Book addBook(@Argument BookInput input)`**:
  - *Annotation*: `@MutationMapping`.
  - *Operation*: Resolves the `addBook(input: BookInput!): Book` mutation, saving a new book with title, page count, price, and author ID.
- **`Book updateStock(@Argument String id, @Argument int stock)`**:
  - *Annotation*: `@MutationMapping`.
  - *Operation*: Modifies the stock count for a book and returns the updated entity.
- **`Map<Book, Author> author(List<Book> books)`**:
  - *Annotation*: `@BatchMapping(typeName = "Book", field = "author")`.
  - *N+1 Solution Mechanics*: When a client requests `query { books { id title author { name } } }`, standard GraphQL engines invoke the author resolver $N$ times (once per book). Spring for GraphQL collects all $N$ book instances into a list and calls this single method once, resolving all authors in a single map operation.

---

### B. Data Repository Layer

#### [`CatalogDataService.java`](file:///sdcard/Download/termux/spring-graphql-showcase/src/main/java/com/example/graphql/repository/CatalogDataService.java)
- **`Map<Book, Author> getAuthorsForBooks(List<Book> books)`**:
  - *Inputs*: `List<Book>` requested in the current query.
  - *Operation*: Collects unique author IDs from the books, executes a single bulk author lookup, and maps each `Book` key to its corresponding `Author` value.
- **`Book saveBook(String title, int pages, double price, int stock, String authorId)`**:
  - *Operation*: Persists new book in memory with an auto-generated ID (`book-N`).
- **`Book updateStock(String id, int stock)`**:
  - *Operation*: Updates stock level for a book.
