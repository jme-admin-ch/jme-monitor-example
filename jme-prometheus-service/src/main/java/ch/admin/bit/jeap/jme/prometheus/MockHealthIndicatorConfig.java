package ch.admin.bit.jeap.jme.prometheus;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockHealthIndicatorConfig {
    @Bean
    public HealthIndicator mockDbHealthIndicator() {
        return () -> Health.up().withDetail("mockDb", "ok").build();
    }
}
