package com.lava.swexpedited.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class SamsaraLocationSyncSchedulerTest {

    private final JobOperator jobOperator = mock(JobOperator.class);
    private final Job samsaraLocationSyncJob = mock(Job.class);
    private final SamsaraLocationSyncScheduler samsaraLocationSyncScheduler =
            new SamsaraLocationSyncScheduler(this.jobOperator, this.samsaraLocationSyncJob, "0 * * ? * *");

    @Test
    void configureTasks_registersCronTaskWithConfiguredExpression() {
        ScheduledTaskRegistrar taskRegistrar = new ScheduledTaskRegistrar();

        this.samsaraLocationSyncScheduler.configureTasks(taskRegistrar);

        assertThat(taskRegistrar.getCronTaskList()).hasSize(1);
        CronTask cronTask = taskRegistrar.getCronTaskList().getFirst();
        assertThat(cronTask.getExpression()).isEqualTo("0 * * ? * *");
    }

    @Test
    void sync_launchesJob() throws Exception {
        this.samsaraLocationSyncScheduler.sync();

        verify(this.jobOperator).start(eq(this.samsaraLocationSyncJob), any());
    }

    @Test
    void sync_anotherInstanceAlreadyRunning_doesNotPropagate() throws Exception {
        when(this.jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("already running"));

        assertThatCode(this.samsaraLocationSyncScheduler::sync).doesNotThrowAnyException();
    }

    @Test
    void sync_anotherInstanceAlreadyCompleted_doesNotPropagate() throws Exception {
        when(this.jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobInstanceAlreadyCompleteException("already complete"));

        assertThatCode(this.samsaraLocationSyncScheduler::sync).doesNotThrowAnyException();
    }

    @Test
    void sync_otherJobExecutionException_doesNotPropagate() throws Exception {
        when(this.jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new InvalidJobParametersException("invalid params"));

        assertThatCode(this.samsaraLocationSyncScheduler::sync).doesNotThrowAnyException();
    }
}
