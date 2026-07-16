package com.lava.swexpedited.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class SamsaraDriverSyncJobConfigTest {

    private final SamsaraDriverSyncJobConfig samsaraDriverSyncJobConfig = new SamsaraDriverSyncJobConfig();

    @Test
    void samsaraDriverSyncStep_buildsNonNullStep() {
        Step step = this.samsaraDriverSyncJobConfig.samsaraDriverSyncStep(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                mock(SamsaraDriverSyncTasklet.class));

        assertThat(step).isNotNull();
    }

    @Test
    void samsaraDriverSyncJob_buildsNonNullJob() {
        Job job = this.samsaraDriverSyncJobConfig.samsaraDriverSyncJob(mock(JobRepository.class), mock(Step.class));

        assertThat(job).isNotNull();
    }
}
