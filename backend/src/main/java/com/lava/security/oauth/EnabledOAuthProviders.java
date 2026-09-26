package com.lava.security.oauth;

import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/**
 * Which OAuth providers this deployment actually has, and a guarantee that it has at least one.
 *
 * <p>Providers are switched on by activating an {@code oauth-<id>} profile (see application.yaml). That makes "not
 * configured" mean "absent" rather than "fatal", which is what lets two deployments of this service run different
 * provider sets — but it also means a forgotten profile would otherwise degrade silently into a login page with no
 * buttons and no error. Hence the check in the constructor: no providers at all is a startup failure, because a service
 * whose only job is authentication cannot authenticate anyone.
 *
 * <p>Also the single source of truth for the SPA: {@code GET /api/auth/providers} reads this rather than each frontend
 * keeping its own list, which would drift the first time a deployment's profiles changed.
 */
@Component
@Configuration
public class EnabledOAuthProviders {

    private final List<String> registrationIds;

    /**
     * @param repositories - the registration repository, absent entirely when no provider profile is active: Spring
     *     Boot only auto-configures one when at least one registration is declared.
     */
    public EnabledOAuthProviders(ObjectProvider<ClientRegistrationRepository> repositories) {
        this.registrationIds = idsOf(repositories.getIfAvailable());

        if (registrationIds.isEmpty()) {
            throw new IllegalStateException("No OAuth providers are configured. Activate at least one provider profile"
                    + " (for example SPRING_PROFILES_ACTIVE=oauth-google) and supply that provider's client id and"
                    + " secret.");
        }
    }

    public List<String> registrationIds() {
        return registrationIds;
    }

    /**
     * {@link org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository} is iterable,
     * which is how the ids are recovered. A repository that is not iterable cannot be enumerated through the interface,
     * so it is reported as empty rather than guessed at — and the constructor then fails loudly instead of quietly
     * publishing an incomplete list.
     */
    private static List<String> idsOf(ClientRegistrationRepository repository) {
        if (!(repository instanceof Iterable<?> registrations)) {
            return List.of();
        }

        return StreamSupport.stream(registrations.spliterator(), false)
                .filter(ClientRegistration.class::isInstance)
                .map(ClientRegistration.class::cast)
                .map(ClientRegistration::getRegistrationId)
                .sorted()
                .toList();
    }
}
