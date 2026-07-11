package com.lava.repository;

import com.lava.model.database.tables.pojos.MfaMethod;
import java.time.LocalDateTime;
import java.util.Optional;

public interface MfaMethodRepository {

    void deleteEnabledByUserIdAndType(Long userId, String type);

    void deleteUnconfirmedByUserIdAndType(Long userId, String type);

    Optional<MfaMethod> findOptionalById(Long id);

    Optional<MfaMethod> findEnabledByUserIdAndType(Long userId, String type);

    MfaMethod insertPending(Long userId, String type, String secretEncrypted);

    void markVerifiedAndEnabled(Long id, LocalDateTime verifiedAt);
}
