package com.lava.swexpedited.batch.samsara;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** A single tasklet step - see {@link SamsaraDriverSyncJobConfig}'s javadoc for why no chunk step is needed here. */
@Configuration
public class SamsaraVehicleSyncJobConfig {

    @Bean
    public Job samsaraVehicleSyncJob(JobRepository jobRepository, Step samsaraVehicleSyncStep) {
        return new JobBuilder("samsaraVehicleSyncJob", jobRepository)
                .start(samsaraVehicleSyncStep)
                .build();
    }

    @Bean
    public Step samsaraVehicleSyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SamsaraVehicleSyncTasklet samsaraVehicleSyncTasklet) {
        return new StepBuilder("samsaraVehicleSyncStep", jobRepository)
                .tasklet(samsaraVehicleSyncTasklet, transactionManager)
                .build();
    }
}
