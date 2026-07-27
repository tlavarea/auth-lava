package com.lava.swexpedited.batch.samsara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class SamsaraVehicleDiagnosticsSyncJobConfigTest {

    private final SamsaraVehicleDiagnosticsSyncJobConfig samsaraVehicleDiagnosticsSyncJobConfig =
            new SamsaraVehicleDiagnosticsSyncJobConfig();

    @Test
    void samsaraVehicleDiagnosticsSyncStep_buildsNonNullStep() {
        Step step = this.samsaraVehicleDiagnosticsSyncJobConfig.samsaraVehicleDiagnosticsSyncStep(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                mock(SamsaraVehicleDiagnosticsSyncTasklet.class));

        assertThat(step).isNotNull();
    }

    @Test
    void samsaraVehicleDiagnosticsSyncJob_buildsNonNullJob() {
        Job job = this.samsaraVehicleDiagnosticsSyncJobConfig.samsaraVehicleDiagnosticsSyncJob(
                mock(JobRepository.class), mock(Step.class));

        assertThat(job).isNotNull();
    }
}
