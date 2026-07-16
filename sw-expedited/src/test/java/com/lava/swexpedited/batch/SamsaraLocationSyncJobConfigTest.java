package com.lava.swexpedited.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class SamsaraLocationSyncJobConfigTest {

    private final SamsaraLocationSyncJobConfig samsaraLocationSyncJobConfig = new SamsaraLocationSyncJobConfig();

    @Test
    void samsaraLocationSyncStep_buildsNonNullStep() {
        Step step = this.samsaraLocationSyncJobConfig.samsaraLocationSyncStep(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                mock(SamsaraLocationSyncTasklet.class));

        assertThat(step).isNotNull();
    }

    @Test
    void samsaraLocationSyncJob_buildsNonNullJob() {
        Job job = this.samsaraLocationSyncJobConfig.samsaraLocationSyncJob(mock(JobRepository.class), mock(Step.class));

        assertThat(job).isNotNull();
    }
}
