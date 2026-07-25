package com.lava.swexpedited.batch.vektor;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A single tasklet step - see {@link VektorFleetSyncTasklet}'s javadoc for why this doesn't need chunk-oriented
 * processing, and for why it's a separate job from {@link VektorSyncJobConfig} rather than a step chained onto it. No
 * dedicated {@code TaskExecutor} bean here - see {@link VektorSyncJobConfig}'s javadoc for why.
 */
@Configuration
public class VektorFleetSyncJobConfig {

    @Bean
    public Job vektorFleetSyncJob(JobRepository jobRepository, Step vektorFleetSyncStep) {
        return new JobBuilder("vektorFleetSyncJob", jobRepository)
                .start(vektorFleetSyncStep)
                .build();
    }

    @Bean
    public Step vektorFleetSyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            VektorFleetSyncTasklet vektorFleetSyncTasklet) {
        return new StepBuilder("vektorFleetSyncStep", jobRepository)
                .tasklet(vektorFleetSyncTasklet, transactionManager)
                .build();
    }
}
