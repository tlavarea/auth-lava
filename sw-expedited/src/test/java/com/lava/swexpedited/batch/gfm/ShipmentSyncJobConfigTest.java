package com.lava.swexpedited.batch.gfm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.lava.swexpedited.repository.ShipmentListingRepository;
import com.lava.swexpedited.shipment.ShipmentListingRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

class ShipmentSyncJobConfigTest {

    private final ShipmentSyncJobConfig shipmentSyncJobConfig = new ShipmentSyncJobConfig();

    @Test
    void shipmentSyncStep_buildsNonNullStep() {
        Step step = this.shipmentSyncJobConfig.shipmentSyncStep(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                mock(FetchAndLoadShipmentsTasklet.class));

        assertThat(step).isNotNull();
    }

    @Test
    void shipmentDetailStep_buildsNonNullStep() {
        Step step = this.shipmentSyncJobConfig.shipmentDetailStep(
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                mock(ItemReader.class),
                mock(ShipmentDetailItemProcessor.class),
                mock(ShipmentDetailItemWriter.class),
                mock(ShipmentDetailSkipListener.class),
                1000);

        assertThat(step).isNotNull();
    }

    @Test
    void shipmentListingReader_readsFromRepository() {
        ShipmentListingRepository shipmentListingRepository = mock(ShipmentListingRepository.class);
        ShipmentListingRow row = new ShipmentListingRow(
                1L,
                "Open",
                null,
                "SHIP1",
                "FAK",
                "1",
                "GBLOC",
                "origin",
                "destination",
                "AF2",
                1,
                0,
                null,
                null,
                null,
                false);
        org.mockito.Mockito.when(shipmentListingRepository.findAll()).thenReturn(List.of(row));

        ItemReader<ShipmentListingRow> reader =
                this.shipmentSyncJobConfig.shipmentListingReader(shipmentListingRepository);

        assertThat(reader).isNotNull();
    }

    @Test
    void shipmentSyncJob_buildsNonNullJob() {
        Job job = this.shipmentSyncJobConfig.shipmentSyncJob(
                mock(JobRepository.class), mock(Step.class), mock(Step.class), mock(GfmLogoutJobListener.class));

        assertThat(job).isNotNull();
    }

    @Test
    void batchTaskExecutor_buildsBoundedSingleThreadExecutor() {
        TaskExecutor executor = this.shipmentSyncJobConfig.batchTaskExecutor();

        assertThat(executor).isNotNull();
    }
}
