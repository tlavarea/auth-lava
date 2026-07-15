package com.lava.swexpedited.batch;

import com.lava.swexpedited.repository.ShipmentListingRepository;
import com.lava.swexpedited.shipment.ShipmentDetailRow;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@link FetchAndLoadShipmentsTasklet} logs into GFM and replaces shipment_listing with the current CSV snapshot; the
 * chunk-oriented {@code shipmentDetailStep} that follows it fetches the "getBid" detail for each currently-listed offer
 * over that same authenticated session (the login chain runs once per job, not once per shipment) and stores it in
 * shipment_detail. The CSV step stays a plain tasklet - it's small enough that chunk-oriented checkpoint/restart
 * machinery isn't earning its complexity there - but the per-shipment HTTP call in the detail step is exactly what
 * chunk processing (with retry/skip so one bad shipment doesn't fail the whole cycle) is built for.
 */
@Configuration
public class ShipmentSyncJobConfig {

    @Bean
    public Job shipmentSyncJob(
            JobRepository jobRepository,
            Step shipmentSyncStep,
            Step shipmentDetailStep,
            GfmLogoutJobListener gfmLogoutJobListener) {
        return new JobBuilder("shipmentSyncJob", jobRepository)
                .start(shipmentSyncStep)
                .next(shipmentDetailStep)
                .listener(gfmLogoutJobListener)
                .build();
    }

    @Bean
    public Step shipmentSyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FetchAndLoadShipmentsTasklet fetchAndLoadShipmentsTasklet) {
        return new StepBuilder("shipmentSyncStep", jobRepository)
                .tasklet(fetchAndLoadShipmentsTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step shipmentDetailStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<ShipmentListingRow> shipmentListingReader,
            ShipmentDetailItemProcessor shipmentDetailItemProcessor,
            ShipmentDetailItemWriter shipmentDetailItemWriter,
            ShipmentDetailSkipListener shipmentDetailSkipListener,
            @Value("${gfm.detail-fetch-skip-limit:1000}") int skipLimit) {
        return new StepBuilder("shipmentDetailStep", jobRepository)
                .<ShipmentListingRow, ShipmentDetailRow>chunk(10)
                .transactionManager(transactionManager)
                .reader(shipmentListingReader)
                .processor(shipmentDetailItemProcessor)
                .writer(shipmentDetailItemWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(skipLimit)
                .skipListener(shipmentDetailSkipListener)
                .build();
    }

    /**
     * {@code @StepScope} so this re-reads shipment_listing fresh at the start of every step execution instead of once
     * at application startup - it needs whatever {@code shipmentSyncStep} just committed in this same job run, not a
     * snapshot from whenever the context was created.
     *
     * <p>Declared to return {@link ListItemReader} rather than the {@link ItemReader} interface - Spring Batch's
     * listener-factory machinery inspects the declared return type of a {@code @StepScope} {@code @Bean} method to find
     * annotation-based listener methods, and can't do that through an interface type.
     */
    @Bean
    @StepScope
    public ListItemReader<ShipmentListingRow> shipmentListingReader(
            ShipmentListingRepository shipmentListingRepository) {
        return new ListItemReader<>(shipmentListingRepository.findAll());
    }

    /**
     * Picked up automatically by Spring Boot's batch autoconfiguration (it accepts a TaskExecutor via
     * ObjectProvider&lt;TaskExecutor&gt; and wires it into the JobOperator it builds), so ShipmentSyncScheduler.sync()
     * becomes non-blocking - jobOperator.start(...) still synchronously performs the JobRepository dedup check our
     * multi-instance coordination depends on, but the actual step execution (the auth chain + fetch) runs on this
     * executor instead of the calling (scheduler) thread. Bounded rather than SimpleAsyncTaskExecutor's
     * unbounded-thread-per-task behavior, since only one job type ever runs here.
     */
    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("shipment-sync-");
        executor.initialize();
        return executor;
    }
}
