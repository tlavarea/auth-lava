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
 * Same pattern as {@link SamsaraLocationSyncScheduler}, on its own cron cadence - deliberately not as fast as
 * location's ~1 min, since this sync issues 3 grouped {@code /fleet/vehicles/stats} calls per cycle (one per
 * {@code SamsaraFleetClient.DIAGNOSTIC_STAT_TYPE_BATCHES} batch) rather than location's single call.
 */
@Component
@Slf4j
public class SamsaraVehicleDiagnosticsSyncScheduler implements SchedulingConfigurer {

    private final JobOperator jobOperator;
    private final Job samsaraVehicleDiagnosticsSyncJob;
    private final String cronExpression;

    public SamsaraVehicleDiagnosticsSyncScheduler(
            JobOperator jobOperator,
            Job samsaraVehicleDiagnosticsSyncJob,
            @Value("${samsara-vehicle-diagnostics-sync.cron}") String cronExpression) {
        this.jobOperator = jobOperator;
        this.samsaraVehicleDiagnosticsSyncJob = samsaraVehicleDiagnosticsSyncJob;
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
            jobOperator.start(samsaraVehicleDiagnosticsSyncJob, params);
        } catch (JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException e) {
            log.debug("sync::another instance already handled this cycle: {}", LogSanitizer.sanitize(e.getMessage()));
        } catch (JobExecutionException e) {
            log.error("sync::failed to launch samsara vehicle diagnostics sync job", e);
        }
    }
}
