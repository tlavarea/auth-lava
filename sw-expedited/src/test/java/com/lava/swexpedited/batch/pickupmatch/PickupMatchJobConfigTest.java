package com.lava.swexpedited.batch.pickupmatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class PickupMatchJobConfigTest {

    private final PickupMatchJobConfig pickupMatchJobConfig = new PickupMatchJobConfig();

    @Test
    void pickupMatchStep_buildsNonNullStep() {
        Step step = this.pickupMatchJobConfig.pickupMatchStep(
                mock(JobRepository.class), mock(PlatformTransactionManager.class), mock(PickupMatchTasklet.class));

        assertThat(step).isNotNull();
    }

    @Test
    void pickupMatchJob_buildsNonNullJob() {
        Job job = this.pickupMatchJobConfig.pickupMatchJob(mock(JobRepository.class), mock(Step.class));

        assertThat(job).isNotNull();
    }
}
