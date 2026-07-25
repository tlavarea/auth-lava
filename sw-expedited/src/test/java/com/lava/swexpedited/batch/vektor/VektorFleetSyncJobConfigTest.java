package com.lava.swexpedited.batch.vektor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class VektorFleetSyncJobConfigTest {

    private final VektorFleetSyncJobConfig vektorFleetSyncJobConfig = new VektorFleetSyncJobConfig();

    @Test
    void vektorFleetSyncStep_buildsNonNullStep() {
        Step step = this.vektorFleetSyncJobConfig.vektorFleetSyncStep(
                mock(JobRepository.class), mock(PlatformTransactionManager.class), mock(VektorFleetSyncTasklet.class));

        assertThat(step).isNotNull();
    }

    @Test
    void vektorFleetSyncJob_buildsNonNullJob() {
        Job job = this.vektorFleetSyncJobConfig.vektorFleetSyncJob(mock(JobRepository.class), mock(Step.class));

        assertThat(job).isNotNull();
    }
}
