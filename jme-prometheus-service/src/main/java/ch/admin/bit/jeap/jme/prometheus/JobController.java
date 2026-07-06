package ch.admin.bit.jeap.jme.prometheus;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping(path = "/api/jobs")
public class JobController {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private final MeterRegistry meterRegistry;
    private final Counter jobValidationErrorCounter;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final Gauge  availableJobsGauge; // keep a hard reference to the gauge in order to protect it from being garbage collected
    private final DistributionSummary jobDescriptionSizeSummary;
    private final Timer jobListTimer;

    public JobController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // Counters are used to count events and can only be incremented.
        jobValidationErrorCounter = Counter.builder("jobs.validation.error")
                .description("Number of invalid jobs received.")
                .register(meterRegistry);
        // Gauges are used to record the current value of something.
        availableJobsGauge = Gauge.builder("jobs.available", jobs, Map::size)
                .description("Number of jobs available.")
                .register(meterRegistry);
        // Distribution summaries are used to track the distribution of events and can be used to create histograms.
        jobDescriptionSizeSummary = DistributionSummary
                .builder("jobs.description.size")
                .description("Job description sizes")
                .baseUnit("characters")
                .publishPercentiles(0.5, 0.9, 0.95)
                .register(meterRegistry);
        // Timers are used to record the duration and frequency of events.
        jobListTimer = Timer
                .builder("jobs.list")
                .description("Timing job listings")
                .publishPercentiles(0.5, 0.9, 0.95)
                .register(meterRegistry);
    }

    @GetMapping
    public Collection<Job> listJobs() {
        // programmatically timing a method call
        return jobListTimer.record( () -> {
            simulateWork(); // let some time pass
            return jobs.values();
        });
    }

    // Convenient micrometer defined annotation for timing methods. The annotation also supports configuring percentiles,
    // but in this case, for demonstration purposes we configure the percentiles with a property in the application.yml.
    @Timed(value = "jobs.add", description = "Timing job additions")
    @PutMapping
    public ResponseEntity<Void> addJob(Job job) {
        if (job.checkValid()) {
            simulateWork(); // let some time pass
            jobs.put(job.getId(), job);
            jobDescriptionSizeSummary.record(job.getDescription().length());
            return new ResponseEntity<>(HttpStatus.OK);
        } else {
            jobValidationErrorCounter.increment();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{id}")
    public void removeJob(@PathVariable String id) {
        Job job = jobs.remove(id);
        // Record a metric with a tag/dimension that depends on the local execution context.
        // Note: if a meter with the given name already exists in the registry, 'register' will use it and not create a new one.
        // Attention: Mind the cardinality of a tag as a separate timeline will be created for every tag value and if you are
        // adding multiple tags then for every combination of the tags' values.
        Counter.builder("jobs.remove")
                .description("Number of jobs removed.")
                .tag("priority", job.getPriority().name()) // count the jobs separately by priority
                .register(meterRegistry)
                .increment();
    }

    private void simulateWork() {
        try {
            TimeUnit.SECONDS.sleep(Math.round(2.5 * random.nextDouble()));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

}
