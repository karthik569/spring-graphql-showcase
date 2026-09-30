package com.example.graphql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings. The secret is a development default and must be overridden in any real deployment.
 */
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private String secret;
    private Duration ttl = Duration.ofHours(1);

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }
}
