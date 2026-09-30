package com.example.graphql.config;

import graphql.ExecutionResult;
import graphql.execution.AbortExecutionException;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;

/**
 * Rejects documents whose raw text exceeds a configured length, before parsing or execution.
 * Implemented as instrumentation (like the depth and complexity limits) so the engine turns it
 * into an ordinary GraphQL error result.
 */
public class MaxQueryLengthInstrumentation extends SimplePerformantInstrumentation {

    private final int maxQueryLength;

    public MaxQueryLengthInstrumentation(int maxQueryLength) {
        this.maxQueryLength = maxQueryLength;
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters,
                                                                  InstrumentationState state) {
        String query = parameters.getExecutionInput().getQuery();
        if (query != null && query.length() > maxQueryLength) {
            throw new AbortExecutionException(
                    "Query length " + query.length() + " exceeds the maximum of " + maxQueryLength + " characters");
        }
        return super.beginExecution(parameters, state);
    }
}
