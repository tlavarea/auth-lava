package com.lava.swexpedited.repository;

import com.lava.swexpedited.configuration.JooqCustomizerConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// Declared in com.lava.swexpedited.repository so @SpringBootApplication's implicit component
// scan stays limited to this package - it must not pull in batch/controller/scheduling/GFM-client
// beans from sibling packages, which need real credentials and aren't relevant to repository tests.
@SpringBootApplication
@Import(JooqCustomizerConfiguration.class)
class RepositoryTestConfiguration {}
