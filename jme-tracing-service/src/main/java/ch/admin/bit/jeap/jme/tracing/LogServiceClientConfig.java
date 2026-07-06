package ch.admin.bit.jeap.jme.tracing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
class LogServiceClientConfig {

    /**
     * Builds the proxy that backs {@link LogServiceClient}. We clone the auto-configured
     * {@link RestClient.Builder} so the customizations applied by
     * {@code jeap-spring-boot-monitoring-starter} (in particular the Micrometer/OTel
     * instrumentation) are preserved while we add our own {@code baseUrl}.
     */
    @Bean
    LogServiceClient logServiceClient(RestClient.Builder restClientBuilder, @Value("${loguri}") String logUrl) {
        RestClient restClient = restClientBuilder.clone().baseUrl(logUrl).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(LogServiceClient.class);
    }
}
