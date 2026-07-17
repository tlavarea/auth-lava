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
public class SamsaraDriverDutyStatusSyncJobConfig {

    @Bean
    public Job samsaraDriverDutyStatusSyncJob(JobRepository jobRepository, Step samsaraDriverDutyStatusSyncStep) {
        return new JobBuilder("samsaraDriverDutyStatusSyncJob", jobRepository)
                .start(samsaraDriverDutyStatusSyncStep)
                .build();
    }

    @Bean
    public Step samsaraDriverDutyStatusSyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SamsaraDriverDutyStatusSyncTasklet samsaraDriverDutyStatusSyncTasklet) {
        return new StepBuilder("samsaraDriverDutyStatusSyncStep", jobRepository)
                .tasklet(samsaraDriverDutyStatusSyncTasklet, transactionManager)
                .build();
    }
}
