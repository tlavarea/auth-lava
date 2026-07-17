package com.lava.swexpedited.batch.samsara;

import com.lava.swexpedited.batch.gfm.ShipmentSyncJobConfig;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A single tasklet step, unlike {@link ShipmentSyncJobConfig}'s chunk-oriented detail step - Samsara's driver and
 * assignment endpoints are already bulk/paginated list calls (a handful of HTTP requests total per sync, not one per
 * driver), so there's no per-item retry/skip checkpointing here to earn chunk-oriented complexity.
 */
@Configuration
public class SamsaraDriverSyncJobConfig {

    @Bean
    public Job samsaraDriverSyncJob(JobRepository jobRepository, Step samsaraDriverSyncStep) {
        return new JobBuilder("samsaraDriverSyncJob", jobRepository)
                .start(samsaraDriverSyncStep)
                .build();
    }

    @Bean
    public Step samsaraDriverSyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SamsaraDriverSyncTasklet samsaraDriverSyncTasklet) {
        return new StepBuilder("samsaraDriverSyncStep", jobRepository)
                .tasklet(samsaraDriverSyncTasklet, transactionManager)
                .build();
    }
}
