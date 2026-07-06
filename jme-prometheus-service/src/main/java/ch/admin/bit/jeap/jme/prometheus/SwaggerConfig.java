package ch.admin.bit.jeap.jme.prometheus;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "JME Prometheus Example",
                description = "An example showing how to create metrics and expose them for prometheus.",
                contact = @Contact(
                        email = "jEAP-Community@bit.admin.ch",
                        name = "jEAP",
                        url = "https://jeap-admin-ch.github.io/docs/what-is-jeap"
                )
        )
)

@Configuration
public class SwaggerConfig {
    @Bean
    GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("Jobs API")
                .pathsToMatch("/api/jobs/**")
                .build();
    }
}
