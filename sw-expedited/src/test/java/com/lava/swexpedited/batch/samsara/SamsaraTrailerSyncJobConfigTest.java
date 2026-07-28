package com.lava.swexpedited.batch.samsara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class SamsaraTrailerSyncJobConfigTest {

    private final SamsaraTrailerSyncJobConfig samsaraTrailerSyncJobConfig = new SamsaraTrailerSyncJobConfig();

    @Test
    void samsaraTrailerSyncStep_buildsNonNullStep() {
        Step step = this.samsaraTrailerSyncJobConfig.samsaraTrailerSyncStep(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                mock(SamsaraTrailerSyncTasklet.class));

        assertThat(step).isNotNull();
    }

    @Test
    void samsaraTrailerSyncJob_buildsNonNullJob() {
        Job job = this.samsaraTrailerSyncJobConfig.samsaraTrailerSyncJob(mock(JobRepository.class), mock(Step.class));

        assertThat(job).isNotNull();
    }
}
