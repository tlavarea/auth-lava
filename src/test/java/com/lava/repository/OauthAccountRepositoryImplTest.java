package com.lava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.model.database.tables.pojos.OauthAccount;
import com.lava.model.database.tables.pojos.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OauthAccountRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private OauthAccountRepository oauthAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByProviderAndProviderUserId_notFound_isEmpty() {
        Optional<OauthAccount> found = this.oauthAccountRepository.findByProviderAndProviderUserId("google", "missing");

        assertThat(found).isEmpty();
    }

    @Test
    void insert_thenFindByProviderAndProviderUserId_roundTrips() {
        User user = this.userRepository.insert("oauth-link@example.com", null).orElseThrow();

        OauthAccount inserted = this.oauthAccountRepository.insert(user.id(), "github", "gh-123");

        assertThat(inserted.userId()).isEqualTo(user.id());
        assertThat(inserted.provider()).isEqualTo("github");
        assertThat(inserted.providerUserId()).isEqualTo("gh-123");

        Optional<OauthAccount> found = this.oauthAccountRepository.findByProviderAndProviderUserId("github", "gh-123");

        assertThat(found).contains(inserted);
    }
}
