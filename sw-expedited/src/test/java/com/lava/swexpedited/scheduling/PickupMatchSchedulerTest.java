package com.lava.swexpedited.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class PickupMatchSchedulerTest {

    private final JobOperator jobOperator = mock(JobOperator.class);
    private final Job pickupMatchJob = mock(Job.class);
    private final PickupMatchScheduler pickupMatchScheduler =
            new PickupMatchScheduler(this.jobOperator, this.pickupMatchJob, List.of("0 15 5 ? * *", "0 35 7 ? * *"));

    @Test
    void configureTasks_registersOneCronTaskPerConfiguredExpressionPinnedToEasternTime() {
        ScheduledTaskRegistrar taskRegistrar = new ScheduledTaskRegistrar();

        this.pickupMatchScheduler.configureTasks(taskRegistrar);

        assertThat(taskRegistrar.getCronTaskList()).hasSize(2);
        List<String> expressions = taskRegistrar.getCronTaskList().stream()
                .map(CronTask::getExpression)
                .toList();
        assertThat(expressions).containsExactly("0 15 5 ? * *", "0 35 7 ? * *");
    }

    @Test
    void sync_launchesJob() throws Exception {
        this.pickupMatchScheduler.sync();

        verify(this.jobOperator).start(eq(this.pickupMatchJob), any());
    }

    @Test
    void sync_anotherInstanceAlreadyRunning_doesNotPropagate() throws Exception {
        when(this.jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("already running"));

        assertThatCode(this.pickupMatchScheduler::sync).doesNotThrowAnyException();
    }

    @Test
    void sync_anotherInstanceAlreadyCompleted_doesNotPropagate() throws Exception {
        when(this.jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobInstanceAlreadyCompleteException("already complete"));

        assertThatCode(this.pickupMatchScheduler::sync).doesNotThrowAnyException();
    }

    @Test
    void sync_otherJobExecutionException_doesNotPropagate() throws Exception {
        when(this.jobOperator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new InvalidJobParametersException("invalid params"));

        assertThatCode(this.pickupMatchScheduler::sync).doesNotThrowAnyException();
    }
}
