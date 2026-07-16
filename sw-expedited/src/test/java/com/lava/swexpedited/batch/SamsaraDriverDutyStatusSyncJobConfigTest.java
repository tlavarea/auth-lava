package com.lava.swexpedited.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class SamsaraDriverDutyStatusSyncJobConfigTest {

    private final SamsaraDriverDutyStatusSyncJobConfig samsaraDriverDutyStatusSyncJobConfig =
            new SamsaraDriverDutyStatusSyncJobConfig();

    @Test
    void samsaraDriverDutyStatusSyncStep_buildsNonNullStep() {
        Step step = this.samsaraDriverDutyStatusSyncJobConfig.samsaraDriverDutyStatusSyncStep(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                mock(SamsaraDriverDutyStatusSyncTasklet.class));

        assertThat(step).isNotNull();
    }

    @Test
    void samsaraDriverDutyStatusSyncJob_buildsNonNullJob() {
        Job job = this.samsaraDriverDutyStatusSyncJobConfig.samsaraDriverDutyStatusSyncJob(
                mock(JobRepository.class), mock(Step.class));

        assertThat(job).isNotNull();
    }
}
