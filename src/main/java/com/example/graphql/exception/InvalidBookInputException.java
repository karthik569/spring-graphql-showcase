package com.example.graphql.exception;

public class InvalidBookInputException extends RuntimeException {

    public InvalidBookInputException(String message) {
        super(message);
    }
}
