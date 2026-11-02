package dev.beatward.monitor.target;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "beatward.health.resolver")
public record ResolverHealthProperties(@DefaultValue("60s") Duration downAfter) {

    public ResolverHealthProperties {
        if (downAfter == null || downAfter.isNegative() || downAfter.isZero()) {
            throw new IllegalArgumentException("beatward.health.resolver.down-after must be positive");
        }
    }
}
