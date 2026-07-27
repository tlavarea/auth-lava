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
public class SamsaraVehicleDiagnosticsSyncJobConfig {

    @Bean
    public Job samsaraVehicleDiagnosticsSyncJob(JobRepository jobRepository, Step samsaraVehicleDiagnosticsSyncStep) {
        return new JobBuilder("samsaraVehicleDiagnosticsSyncJob", jobRepository)
                .start(samsaraVehicleDiagnosticsSyncStep)
                .build();
    }

    @Bean
    public Step samsaraVehicleDiagnosticsSyncStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SamsaraVehicleDiagnosticsSyncTasklet samsaraVehicleDiagnosticsSyncTasklet) {
        return new StepBuilder("samsaraVehicleDiagnosticsSyncStep", jobRepository)
                .tasklet(samsaraVehicleDiagnosticsSyncTasklet, transactionManager)
                .build();
    }
}
