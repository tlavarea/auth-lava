package com.lava.swexpedited.scheduling;

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
 * Same multi-instance-safe, non-blocking scheduling pattern as {@code ShipmentSyncScheduler} - see its javadoc for the
 * full rationale (JobRepository-based dedup via minute-truncated JobParameters, JobOperator over the deprecated
 * JobLauncher, SchedulingConfigurer over {@code @Scheduled} so the cron expression is resolved once).
 */
@Component
@Slf4j
public class SamsaraDriverSyncScheduler implements SchedulingConfigurer {

    private final JobOperator jobOperator;
    private final Job samsaraDriverSyncJob;
    private final String cronExpression;

    public SamsaraDriverSyncScheduler(
            JobOperator jobOperator,
            Job samsaraDriverSyncJob,
            @Value("${samsara-driver-sync.cron}") String cronExpression) {
        this.jobOperator = jobOperator;
        this.samsaraDriverSyncJob = samsaraDriverSyncJob;
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
            jobOperator.start(samsaraDriverSyncJob, params);
        } catch (JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException e) {
            log.debug("sync::another instance already handled this cycle: {}", e.getMessage());
        } catch (JobExecutionException e) {
            log.error("sync::failed to launch samsara driver sync job", e);
        }
    }
}
