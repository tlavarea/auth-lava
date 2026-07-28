package com.lava.swexpedited.scheduling;

import com.lava.swexpedited.logging.LogSanitizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Fires {@code pickupMatchJob} 6x/day - roughly every 2h20m, on :15/:35/:55 so it never lands on the same tick as
 * vektor-sync (:00/:20/:40), shipment-sync (:05/:25/:45), or vektor-fleet-sync (:10/:30/:50) - spanning 5 AM-5 PM EST,
 * the window ATR actually publishes shipment updates in; firing outside it would just re-score unchanged data against
 * the (paid) Google Route Matrix API. Each tick trails the nearest vektor-sync/shipment-sync tick by 10-15 minutes,
 * comfortably past when those jobs' tasklets finish (see {@code shipment-sync.cron}'s comment in application.yaml for
 * the same reasoning at a smaller offset), so {@code pickupMatchStep} still reads that cycle's fresh
 * {@code shipment_detail}/{@code vektor_manifest} even though it's no longer guaranteed by being the same job
 * execution.
 *
 * <p>Unlike every other scheduler in this package, these cron expressions are pinned to {@code America/New_York}
 * explicitly via {@link CronTrigger}'s timezone constructor rather than firing in the JVM's default timezone (which
 * nothing in this app's deployment sets) - getting the EST alignment wrong here would silently desync this job from
 * ATR's actual publish window.
 */
@Component
@Slf4j
public class PickupMatchScheduler implements SchedulingConfigurer {

    private static final ZoneId EST = ZoneId.of("America/New_York");

    private final JobOperator jobOperator;
    private final Job pickupMatchJob;
    private final List<String> cronExpressions;

    public PickupMatchScheduler(
            JobOperator jobOperator,
            Job pickupMatchJob,
            @Value("#{'${pickup-match-sync.cron}'.split(',')}") List<String> cronExpressions) {
        this.jobOperator = jobOperator;
        this.pickupMatchJob = pickupMatchJob;
        this.cronExpressions = cronExpressions;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        TimeZone timeZone = TimeZone.getTimeZone(EST);
        for (String cronExpression : this.cronExpressions) {
            taskRegistrar.addCronTask(new CronTask(this::sync, new CronTrigger(cronExpression, timeZone)));
        }
    }

    public void sync() {
        JobParameters params = new JobParametersBuilder()
                .addString(
                        "cycle", Instant.now().truncatedTo(ChronoUnit.MINUTES).toString())
                .toJobParameters();

        try {
            jobOperator.start(pickupMatchJob, params);
        } catch (JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException e) {
            log.debug("sync::another instance already handled this cycle: {}", LogSanitizer.sanitize(e.getMessage()));
        } catch (JobExecutionException e) {
            log.error("sync::failed to launch pickup match job", e);
        }
    }
}
