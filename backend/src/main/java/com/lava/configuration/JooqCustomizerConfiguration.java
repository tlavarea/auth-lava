package com.lava.configuration;

import org.jooq.conf.RenderNameCase;
import org.jooq.conf.RenderQuotedNames;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqCustomizerConfiguration {

    // "user" is a reserved word in PostgreSQL: even unquoted-and-lowercased, `SELECT ... FROM
    // user` fails with a grammar error because the parser treats "user" as the USER/CURRENT_USER
    // expression, not a table reference. Quoting every identifier ("user", "refresh_token", ...)
    // sidesteps that regardless of dialect-specific reserved word lists.
    //
    // jOOQ code generation runs against an in-memory H2 simulation of the Liquibase changelog
    // (see pom.xml's LiquibaseDatabase config), and H2 folds unquoted identifiers to UPPERCASE -
    // so the generated model holds names like "USER", "REFRESH_TOKEN". The real tables were
    // created unquoted by Liquibase against Postgres, which folds unquoted names to lowercase
    // instead. RenderNameCase.LOWER forces every rendered name to lowercase regardless of the
    // case jOOQ's model stored it in, so combined with ALWAYS-quoting we render e.g. "user" -
    // quoted (avoids the reserved-word collision) and lowercase (matches the real table).
    @Bean
    public DefaultConfigurationCustomizer configurationCustomizer() {
        return configuration -> configuration
                .settings()
                .withRenderQuotedNames(RenderQuotedNames.ALWAYS)
                .withRenderNameCase(RenderNameCase.LOWER);
    }
}
