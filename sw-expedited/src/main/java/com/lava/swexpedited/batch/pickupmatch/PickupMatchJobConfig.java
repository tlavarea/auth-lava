package com.lava.swexpedited.batch.pickupmatch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Previously {@code pickupMatchStep} ran as the last step of {@code ShipmentSyncJobConfig}'s {@code shipmentSyncJob}.
 * It's split into its own job because it has a fundamentally different, much lower-frequency schedule need: it's the
 * one step in this app that pays per Google Routes API (Compute Route Matrix) call, and ATR only publishes shipment
 * updates 5 AM-5 PM EST, so there's nothing to gain from matching on every 20-minute shipment-sync tick the way the GFM
 * CSV/detail steps benefit from. See {@code PickupMatchScheduler} for the resulting cron.
 */
@Configuration
public class PickupMatchJobConfig {

    @Bean
    public Job pickupMatchJob(JobRepository jobRepository, Step pickupMatchStep) {
        return new JobBuilder("pickupMatchJob", jobRepository)
                .start(pickupMatchStep)
                .build();
    }

    @Bean
    public Step pickupMatchStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            PickupMatchTasklet pickupMatchTasklet) {
        return new StepBuilder("pickupMatchStep", jobRepository)
                .tasklet(pickupMatchTasklet, transactionManager)
                .build();
    }
}
