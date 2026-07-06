package ch.admin.bit.jeap.jme.prometheus;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permit-all filter chain for the application's business endpoints.
 * <p>
 * The actuator endpoints are protected by a separate filter chain registered by
 * {@link ch.admin.bit.jeap.monitor.ActuatorSecurity} from the jeap-monitoring-starter, which
 * uses a lower {@link Order} value so it runs first and only matches actuator paths. This
 * chain runs after it (order 100) and accepts anything that didn't match the actuator chain
 * — the application's REST endpoints — without authentication.
 */
@Configuration
public class PermitAllSecurityConfiguration {

    @Bean
    @Order(100)
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

}
