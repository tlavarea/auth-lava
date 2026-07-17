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
 * A single tasklet step - see {@link VektorSyncTasklet}'s javadoc for why this doesn't need chunk-oriented processing.
 * No dedicated {@code TaskExecutor} bean here: {@code ShipmentSyncJobConfig#batchTaskExecutor} is the one TaskExecutor
 * bean in the whole application context (Spring Boot's batch autoconfiguration only resolves it unambiguously with a
 * single candidate), shared by every scheduled job including this one.
 */
@Configuration
public class VektorSyncJobConfig {

    @Bean
    public Job vektorSyncJob(JobRepository jobRepository, Step vektorSyncStep) {
        return new JobBuilder("vektorSyncJob", jobRepository)
                .start(vektorSyncStep)
                .build();
    }

    @Bean
    public Step vektorSyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            VektorSyncTasklet vektorSyncTasklet) {
        return new StepBuilder("vektorSyncStep", jobRepository)
                .tasklet(vektorSyncTasklet, transactionManager)
                .build();
    }
}
