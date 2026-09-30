# Spring for GraphQL Schema-First Showcase 🌌

A production-grade GraphQL server implementing **Schema-First Design**, `@QueryMapping`, `@MutationMapping`, and `@BatchMapping` with DataLoader to eliminate the $N+1$ query problem.

> 📖 **New to Spring GraphQL or this repo?** Start with the [Getting Started guide](GETTING_STARTED.html).

---

## 🌟 Comprehensive Method-by-Method Breakdown

### 1. [`BookGraphQLController`](file:///sdcard/Download/termux/spring-graphql-showcase/src/main/java/com/example/graphql/controller/BookGraphQLController.java)
- **`List<Book> books()`**: `@QueryMapping`; resolves the root `books` query, returning the list of available book records.
- **`Book bookById(String id)`**: `@QueryMapping`; resolves a single book by identifier.
- **`Book addBook(String title, int pages, String authorId)`**: `@MutationMapping`; creates a new book instance in the system.
- **`Mono<Map<Book, Author>> authors(List<Book> books)`**: `@BatchMapping`; **Solves the N+1 problem** by batching author lookups across multiple books into a single asynchronous operation via DataLoader.

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

### 2. Execute GraphQL Mutation
```bash
curl -i -X POST http://localhost:8086/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation { addBook(title: \"Cloud Native Java\", pages: 450, authorId: \"author-1\") { id title pages } }"}'
```
