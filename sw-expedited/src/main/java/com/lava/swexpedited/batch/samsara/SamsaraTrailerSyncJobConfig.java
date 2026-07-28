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
public class SamsaraTrailerSyncJobConfig {

    @Bean
    public Job samsaraTrailerSyncJob(JobRepository jobRepository, Step samsaraTrailerSyncStep) {
        return new JobBuilder("samsaraTrailerSyncJob", jobRepository)
                .start(samsaraTrailerSyncStep)
                .build();
    }

    @Bean
    public Step samsaraTrailerSyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SamsaraTrailerSyncTasklet samsaraTrailerSyncTasklet) {
        return new StepBuilder("samsaraTrailerSyncStep", jobRepository)
                .tasklet(samsaraTrailerSyncTasklet, transactionManager)
                .build();
    }
}
