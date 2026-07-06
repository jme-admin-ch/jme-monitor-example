package ch.admin.bit.jeap.jme.prometheus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusOutputFormat;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;

@RestController
public class TestController {
    private final Counter counter;
    private final DistributionSummary summary;
    private final Environment environment;
    private final PrometheusScrapeEndpoint prometheusScrapeEndpoint;
    private final SecureRandom random = new SecureRandom();

    public TestController(MeterRegistry meterRegistry, Environment environment, PrometheusScrapeEndpoint prometheusScrapeEndpoint) {
        this.environment = environment;
        this.prometheusScrapeEndpoint = prometheusScrapeEndpoint;
        //Create a new counter (can only increment)
        counter = Counter.builder("my.counter").register(meterRegistry);

        //Create a new Distribution summary (shows the distribution of a value)
        summary = DistributionSummary.builder("my.summary")
                //Also publish the 0.95-Percentile (5% of the measurements are above this number)
                .publishPercentiles(0.95)
                //How long to keep the statistics for max and percentile
                .distributionStatisticExpiry(Duration.ofMinutes(1))
                .register(meterRegistry);
    }

    @GetMapping(path = "/")
    public String welcome() {
        return "Welcome JME Prometheus Service with profiles: " + Arrays.toString(environment.getActiveProfiles());
    }

    @GetMapping("/test")
    public String test() {
        counter.increment();
        double result = random.nextDouble();
        summary.record(result);
        return String.valueOf(result);
    }

    /**
     * This endpoint is used to test the prometheus metrics on platforms where they are not exposed publicly (AWS)
     */
    @GetMapping("/prometheus-metrics-mirror")
    public String prometheusMetricsMirror() {
        WebEndpointResponse<byte[]> response = prometheusScrapeEndpoint.scrape(PrometheusOutputFormat.CONTENT_TYPE_004, null);
        byte[] body = response.getBody();
        return body == null ? "" : new String(body);
    }
}
