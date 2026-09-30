package com.example.graphql.exception;

public class AuthorNotFoundException extends RuntimeException {

    public AuthorNotFoundException(String id) {
        super("Author not found: " + id);
    }
}
