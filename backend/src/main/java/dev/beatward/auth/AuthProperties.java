package dev.beatward.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("beatward.auth")
public record AuthProperties(
        @DefaultValue("false") boolean cookieSecure,
        @DefaultValue("") String bootstrapUsername,
        @DefaultValue("") String bootstrapPasswordHash) {
}
