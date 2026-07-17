package com.lava.swexpedited.scheduling;

import com.lava.swexpedited.logging.LogSanitizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * Runs on every sw-expedited instance. Multi-instance safety comes from Spring Batch's JobRepository, not a separate
 * distributed lock: all instances fire on the same cron-aligned tick and build identical JobParameters (a cron schedule
 * always fires on a whole-minute boundary, so truncating Instant.now() to the minute yields the same value on every
 * instance for a given firing). Only the instance that wins the race to create that JobInstance in the shared
 * Postgres-backed JobRepository actually runs the job - the rest get
 * JobExecutionAlreadyRunningException/JobInstanceAlreadyCompleteException, which is the expected outcome for "someone
 * else already handled this cycle", not an error.
 *
 * <p>Uses {@link JobOperator} rather than {@link org.springframework.batch.core.launch.JobLauncher} - JobLauncher is
 * deprecated since Spring Batch 6.0 in favor of JobOperator, which exposes the same {@code start(Job, JobParameters)}
 * semantics (and the same exception set) that this class relies on. The cron trigger is registered programmatically via
 * {@link SchedulingConfigurer}/{@link ScheduledTaskRegistrar} instead of {@code @Scheduled} so the cron expression can
 * be resolved from configuration once, in the constructor, rather than re-parsed as a SpEL string on every fire.
 */
@Component
@Slf4j
public class ShipmentSyncScheduler implements SchedulingConfigurer {

    private final JobOperator jobOperator;
    private final Job shipmentSyncJob;
    private final String cronExpression;

    public ShipmentSyncScheduler(
            JobOperator jobOperator, Job shipmentSyncJob, @Value("${shipment-sync.cron}") String cronExpression) {
        this.jobOperator = jobOperator;
        this.shipmentSyncJob = shipmentSyncJob;
        this.cronExpression = cronExpression;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addCronTask(this::sync, cronExpression);
    }

    public void sync() {
        JobParameters params = new JobParametersBuilder()
                .addString(
                        "cycle", Instant.now().truncatedTo(ChronoUnit.MINUTES).toString())
                .toJobParameters();

        try {
            jobOperator.start(shipmentSyncJob, params);
        } catch (JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException e) {
            log.debug("sync::another instance already handled this cycle: {}", LogSanitizer.sanitize(e.getMessage()));
        } catch (JobExecutionException e) {
            log.error("sync::failed to launch shipment sync job", e);
        }
    }
}
