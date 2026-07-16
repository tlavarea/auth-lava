package com.lava.swexpedited.batch;

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
public class SamsaraLocationSyncJobConfig {

    @Bean
    public Job samsaraLocationSyncJob(JobRepository jobRepository, Step samsaraLocationSyncStep) {
        return new JobBuilder("samsaraLocationSyncJob", jobRepository)
                .start(samsaraLocationSyncStep)
                .build();
    }

    @Bean
    public Step samsaraLocationSyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SamsaraLocationSyncTasklet samsaraLocationSyncTasklet) {
        return new StepBuilder("samsaraLocationSyncStep", jobRepository)
                .tasklet(samsaraLocationSyncTasklet, transactionManager)
                .build();
    }
}
