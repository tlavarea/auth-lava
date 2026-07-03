package com.lava.configuration;

import org.jooq.conf.RenderNameCase;
import org.jooq.conf.RenderQuotedNames;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqCustomizerConfiguration {

    // jOOQ code generation runs against an in-memory H2 simulation of the Liquibase changelog
    // (see pom.xml's LiquibaseDatabase config), and H2 folds unquoted identifiers to UPPERCASE.
    // The generated code therefore renders quoted "USERS"-style identifiers. The real tables were
    // created unquoted by Liquibase against Postgres, which folds unquoted names to lowercase
    // instead - so the quoted uppercase name jOOQ sends doesn't match. Rendering names unquoted
    // here lets Postgres apply its own (lowercase) folding and resolve the real tables.
    @Bean
    public DefaultConfigurationCustomizer configurationCustomizer() {
        return configuration -> configuration
                .settings()
                .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_UNQUOTED)
                .withRenderNameCase(RenderNameCase.LOWER_IF_UNQUOTED);
    }
}
