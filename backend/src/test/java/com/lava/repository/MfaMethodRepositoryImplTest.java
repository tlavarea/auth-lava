package com.lava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lava.model.database.tables.pojos.MfaMethod;
import com.lava.model.database.tables.pojos.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MfaMethodRepositoryImplTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private MfaMethodRepository mfaMethodRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void insertPending_isNotReturnedByFindEnabledByUserIdAndType() {
        User user =
                this.userRepository.insert("mfa-pending@example.com", "hash").orElseThrow();

        this.mfaMethodRepository.insertPending(user.id(), "totp", "encrypted-secret");

        assertThat(this.mfaMethodRepository.findEnabledByUserIdAndType(user.id(), "totp"))
                .isEmpty();
    }

    @Test
    void findOptionalById_notFound_isEmpty() {
        assertThat(this.mfaMethodRepository.findOptionalById(-1L)).isEmpty();
    }

    @Test
    void findOptionalById_found_returnsMethod() {
        User user = this.userRepository.insert("mfa-find@example.com", "hash").orElseThrow();
        MfaMethod inserted = this.mfaMethodRepository.insertPending(user.id(), "totp", "encrypted-secret");

        Optional<MfaMethod> found = this.mfaMethodRepository.findOptionalById(inserted.id());

        assertThat(found).contains(inserted);
    }

    @Test
    void markVerifiedAndEnabled_thenFindEnabledByUserIdAndType_returnsMethod() {
        User user = this.userRepository.insert("mfa-verify@example.com", "hash").orElseThrow();
        MfaMethod inserted = this.mfaMethodRepository.insertPending(user.id(), "totp", "encrypted-secret");

        this.mfaMethodRepository.markVerifiedAndEnabled(inserted.id(), LocalDateTime.now());

        Optional<MfaMethod> found = this.mfaMethodRepository.findEnabledByUserIdAndType(user.id(), "totp");
        assertThat(found).isPresent();
        assertThat(found.get().isEnabled()).isTrue();
        assertThat(found.get().verifiedAt()).isNotNull();
    }

    @Test
    void deleteUnconfirmedByUserIdAndType_removesOnlyDisabledRows() {
        User user = this.userRepository.insert("mfa-delete@example.com", "hash").orElseThrow();
        MfaMethod enabled = this.mfaMethodRepository.insertPending(user.id(), "totp", "encrypted-secret-1");
        this.mfaMethodRepository.markVerifiedAndEnabled(enabled.id(), LocalDateTime.now());
        MfaMethod unconfirmed = this.mfaMethodRepository.insertPending(user.id(), "totp", "encrypted-secret-2");

        this.mfaMethodRepository.deleteUnconfirmedByUserIdAndType(user.id(), "totp");

        assertThat(this.mfaMethodRepository.findOptionalById(enabled.id())).isPresent();
        assertThat(this.mfaMethodRepository.findOptionalById(unconfirmed.id())).isEmpty();
    }
}
