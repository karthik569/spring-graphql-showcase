package com.example.graphql.exception;

public class InvalidBookInputException extends RuntimeException {

    private final String field;

    public InvalidBookInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
