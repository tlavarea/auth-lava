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

/** Same pattern as {@link SamsaraDriverSyncScheduler}, on its own (roster changes infrequently) cron cadence. */
@Component
@Slf4j
public class SamsaraVehicleSyncScheduler implements SchedulingConfigurer {

    private final JobOperator jobOperator;
    private final Job samsaraVehicleSyncJob;
    private final String cronExpression;

    public SamsaraVehicleSyncScheduler(
            JobOperator jobOperator,
            Job samsaraVehicleSyncJob,
            @Value("${samsara-vehicle-sync.cron}") String cronExpression) {
        this.jobOperator = jobOperator;
        this.samsaraVehicleSyncJob = samsaraVehicleSyncJob;
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
            jobOperator.start(samsaraVehicleSyncJob, params);
        } catch (JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException e) {
            log.debug("sync::another instance already handled this cycle: {}", LogSanitizer.sanitize(e.getMessage()));
        } catch (JobExecutionException e) {
            log.error("sync::failed to launch samsara vehicle sync job", e);
        }
    }
}
