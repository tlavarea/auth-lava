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

/** Same pattern as {@link SamsaraDriverSyncScheduler}, on its own (much faster, ~1 min) cron cadence. */
@Component
@Slf4j
public class SamsaraLocationSyncScheduler implements SchedulingConfigurer {

    private final JobOperator jobOperator;
    private final Job samsaraLocationSyncJob;
    private final String cronExpression;

    public SamsaraLocationSyncScheduler(
            JobOperator jobOperator,
            Job samsaraLocationSyncJob,
            @Value("${samsara-location-sync.cron}") String cronExpression) {
        this.jobOperator = jobOperator;
        this.samsaraLocationSyncJob = samsaraLocationSyncJob;
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
            jobOperator.start(samsaraLocationSyncJob, params);
        } catch (JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException e) {
            log.debug("sync::another instance already handled this cycle: {}", e.getMessage());
        } catch (JobExecutionException e) {
            log.error("sync::failed to launch samsara location sync job", e);
        }
    }
}
