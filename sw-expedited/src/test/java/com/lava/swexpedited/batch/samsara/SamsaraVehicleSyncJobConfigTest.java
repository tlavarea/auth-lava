package com.lava.swexpedited.batch.samsara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class SamsaraVehicleSyncJobConfigTest {

    private final SamsaraVehicleSyncJobConfig samsaraVehicleSyncJobConfig = new SamsaraVehicleSyncJobConfig();

    @Test
    void samsaraVehicleSyncStep_buildsNonNullStep() {
        Step step = this.samsaraVehicleSyncJobConfig.samsaraVehicleSyncStep(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                mock(SamsaraVehicleSyncTasklet.class));

        assertThat(step).isNotNull();
    }

    @Test
    void samsaraVehicleSyncJob_buildsNonNullJob() {
        Job job = this.samsaraVehicleSyncJobConfig.samsaraVehicleSyncJob(mock(JobRepository.class), mock(Step.class));

        assertThat(job).isNotNull();
    }
}
