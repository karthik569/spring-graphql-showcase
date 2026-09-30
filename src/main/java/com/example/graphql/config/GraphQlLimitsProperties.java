package com.example.graphql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable guards applied to every incoming GraphQL request.
 */
@ConfigurationProperties(prefix = "graphql.limits")
public class GraphQlLimitsProperties {

    private int maxDepth = 10;
    private int maxComplexity = 200;
    private int maxQueryLength = 10000;

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMaxComplexity() {
        return maxComplexity;
    }

    public void setMaxComplexity(int maxComplexity) {
        this.maxComplexity = maxComplexity;
    }

    public int getMaxQueryLength() {
        return maxQueryLength;
    }

    public void setMaxQueryLength(int maxQueryLength) {
        this.maxQueryLength = maxQueryLength;
    }
}
