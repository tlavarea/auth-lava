package com.lava.swexpedited.configuration;

import org.jooq.conf.RenderNameCase;
import org.jooq.conf.RenderQuotedNames;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * jOOQ code generation runs against an in-memory H2 simulation of the Liquibase changelog, and H2 folds unquoted
 * identifiers to UPPERCASE - so the generated model holds names like "SHIPMENT_LISTING", "RANK". The real tables are
 * created unquoted by Liquibase against Postgres, which folds unquoted names to lowercase instead. Always-quoting plus
 * RenderNameCase.LOWER renders every identifier lowercase and quoted regardless of the case jOOQ's model stored it in,
 * so it matches the real table - and protects against future reserved-word collisions (e.g. a column named "user" or
 * "order") the same way it already does in backend.
 */
@Configuration
public class JooqCustomizerConfiguration {

    @Bean
    public DefaultConfigurationCustomizer configurationCustomizer() {
        return configuration -> configuration
                .settings()
                .withRenderQuotedNames(RenderQuotedNames.ALWAYS)
                .withRenderNameCase(RenderNameCase.LOWER);
    }
}
